package web.tosunsaeng.domain.notifications.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import web.tosunsaeng.domain.exams.application.ExamGradingService;
import web.tosunsaeng.domain.exams.application.ExamSessionManager;
import web.tosunsaeng.domain.exams.application.GradingKeys;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.exams.domain.entity.SummaryGradingJob;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
import web.tosunsaeng.domain.exams.domain.repository.SummaryGradingJobRepository;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationOutbox;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationOutboxStatus;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationType;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationOutboxRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamCompletionNotificationServiceTest {

    private static final String EXAM_ID = "ex_notification_complete_001";
    private static final String USER_ID = "00000000-0000-4000-8000-000000000042";
    private static final Instant NOW = Instant.parse("2026-08-04T06:00:00Z");

    @Mock
    private ExamSummaryRepository examSummaryRepository;

    @Mock
    private ExamResultRepository examResultRepository;

    @Mock
    private ExamSessionRepository examSessionRepository;

    @Mock
    private SummaryGradingJobRepository summaryJobRepository;

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @Mock
    private ExamSessionManager examSessionManager;

    @Mock
    private ExamGradingService gradingService;

    private final AtomicBoolean summaryStored = new AtomicBoolean();
    private final AtomicBoolean sessionCompleted = new AtomicBoolean();
    private final AtomicBoolean summaryJobCompleted = new AtomicBoolean();
    private final AtomicBoolean outboxStored = new AtomicBoolean();
    private final AtomicReference<NotificationOutbox> storedOutbox = new AtomicReference<>();
    private final AtomicInteger transactionCount = new AtomicInteger();
    private final NotificationIdentityCodec identityCodec = new NotificationIdentityCodec();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ExamCompletionNotificationService service;

    @BeforeEach
    void setUp() {
        summaryStored.set(false);
        sessionCompleted.set(false);
        summaryJobCompleted.set(false);
        outboxStored.set(false);
        storedOutbox.set(null);
        transactionCount.set(0);

        when(examSummaryRepository.existsById(GradingKeys.summaryJobId(EXAM_ID)))
                .thenAnswer(ignored -> summaryStored.get());
        when(examSummaryRepository.existsByExamId(EXAM_ID))
                .thenAnswer(ignored -> summaryStored.get());
        when(examResultRepository.findFirstByExamIdAndTotalScoreIsNotNullOrderByIdDesc(EXAM_ID))
                .thenReturn(Optional.empty());
        when(examSummaryRepository.insert(any(ExamSummary.class))).thenAnswer(invocation -> {
            summaryStored.set(true);
            return invocation.getArgument(0);
        });
        when(examSessionManager.completeIfIncomplete(EXAM_ID)).thenAnswer(ignored -> {
            sessionCompleted.set(true);
            return true;
        });
        org.mockito.Mockito.doAnswer(ignored -> {
            summaryJobCompleted.set(true);
            return null;
        }).when(gradingService).completeSummary(EXAM_ID);
        when(gradingService.areAllRequiredQuestionsComplete(EXAM_ID)).thenReturn(true);
        when(examSessionRepository.findById(EXAM_ID)).thenAnswer(ignored -> Optional.of(
                sessionCompleted.get() ? completedSession() : activeSession()
        ));
        when(summaryJobRepository.findById(GradingKeys.summaryJobId(EXAM_ID)))
                .thenAnswer(ignored -> summaryJobCompleted.get()
                        ? Optional.of(SummaryGradingJob.completed(
                                GradingKeys.summaryJobId(EXAM_ID), EXAM_ID, NOW))
                        : Optional.empty());
        when(outboxRepository.existsByEventKey(ExamCompletionNotificationService.eventKey(EXAM_ID)))
                .thenAnswer(ignored -> outboxStored.get());
        when(outboxRepository.insert(any(NotificationOutbox.class))).thenAnswer(invocation -> {
            NotificationOutbox outbox = invocation.getArgument(0);
            storedOutbox.set(outbox);
            outboxStored.set(true);
            return outbox;
        });
        when(outboxRepository.findById(anyString())).thenAnswer(ignored ->
                Optional.ofNullable(storedOutbox.get()));
        when(outboxRepository.findByEventKey(ExamCompletionNotificationService.eventKey(EXAM_ID)))
                .thenAnswer(ignored -> Optional.ofNullable(storedOutbox.get()));

        service = newService(new SnapshotTransactionOperations());
    }

    @Test
    void summaryCompletionPersistsAllCompletionEvidenceAndOneMinimalOutboxInOneTransaction()
            throws Exception {
        service.completeSummaryCallback(summaryRequest(), activeSession(), "mock_exam_003");

        ArgumentCaptor<ExamSummary> summaryCaptor = ArgumentCaptor.forClass(ExamSummary.class);
        verify(examSummaryRepository).insert(summaryCaptor.capture());
        ExamSummary summary = summaryCaptor.getValue();
        NotificationOutbox outbox = storedOutbox.get();
        assertAll(
                () -> assertEquals(1, transactionCount.get()),
                () -> assertTrue(summaryStored.get()),
                () -> assertTrue(sessionCompleted.get()),
                () -> assertTrue(summaryJobCompleted.get()),
                () -> assertNotNull(outbox),
                () -> assertEquals("summary:" + EXAM_ID + ":v1", summary.getId()),
                () -> assertEquals(USER_ID, summary.getUserId()),
                () -> assertEquals(170, summary.getTotalScore()),
                () -> assertEquals("Advanced Mid", summary.getLevelEstimate()),
                () -> assertEquals("synthetic summary", summary.getSummary()),
                () -> assertEquals("synthetic overall feedback", summary.getOverallFeedback()),
                () -> assertEquals("synthetic part feedback", summary.getPartFeedback().get("part1")),
                () -> assertEquals("synthetic strength", summary.getStrengths().getFirst()),
                () -> assertEquals("synthetic weakness", summary.getWeaknesses().getFirst()),
                () -> assertEquals("synthetic practice", summary.getRecommendedPractice().getFirst()),
                () -> assertEquals(
                        "EXAM_GRADING_COMPLETED:" + EXAM_ID,
                        outbox.getEventKey()
                ),
                () -> assertEquals(NotificationType.EXAM_GRADING_COMPLETED, outbox.getType()),
                () -> assertEquals(NotificationOutboxStatus.PENDING, outbox.getStatus()),
                () -> assertEquals(USER_ID, outbox.getUserId()),
                () -> assertEquals(EXAM_ID, outbox.getExamId()),
                () -> assertNull(outbox.getLastErrorCode())
        );
    }

    @Test
    void oneQuestionCompletionDoesNotCreateOutboxBeforeAllRequiredQuestionsComplete() {
        summaryStored.set(true);
        sessionCompleted.set(true);
        summaryJobCompleted.set(true);
        when(gradingService.areAllRequiredQuestionsComplete(EXAM_ID)).thenReturn(false);

        service.reconcileAfterQuestionCompletion(EXAM_ID);

        verify(outboxRepository, never()).insert(any(NotificationOutbox.class));
    }

    @Test
    void missingSummarySessionCompletionOrSummaryJobBlocksOutbox() {
        service.reconcileAfterQuestionCompletion(EXAM_ID);
        verify(outboxRepository, never()).insert(any(NotificationOutbox.class));

        summaryStored.set(true);
        service.reconcileAfterQuestionCompletion(EXAM_ID);
        verify(outboxRepository, never()).insert(any(NotificationOutbox.class));

        sessionCompleted.set(true);
        service.reconcileAfterQuestionCompletion(EXAM_ID);
        verify(outboxRepository, never()).insert(any(NotificationOutbox.class));

        summaryJobCompleted.set(true);
        service.reconcileAfterQuestionCompletion(EXAM_ID);
        verify(outboxRepository, times(1)).insert(any(NotificationOutbox.class));
    }

    @Test
    void duplicateCallbacksKeepOneSummaryAndOneOutbox() throws Exception {
        ExamRequestDTO.AiResultReq request = summaryRequest();

        service.completeSummaryCallback(request, activeSession(), "mock_exam_003");
        service.completeSummaryCallback(request, completedSession(), "mock_exam_003");

        verify(examSummaryRepository, times(1)).insert(any(ExamSummary.class));
        verify(outboxRepository, times(1)).insert(any(NotificationOutbox.class));
        verify(examSessionManager, times(2)).completeIfIncomplete(EXAM_ID);
        verify(gradingService, times(2)).completeSummary(EXAM_ID);
    }

    @Test
    void expectedEventKeyDuplicateConvergesButUnrelatedDuplicateIsNotHidden() throws Exception {
        summaryStored.set(true);
        sessionCompleted.set(true);
        summaryJobCompleted.set(true);
        NotificationOutbox expected = NotificationOutbox.pending(
                identityCodec.notificationId(ExamCompletionNotificationService.eventKey(EXAM_ID)),
                ExamCompletionNotificationService.eventKey(EXAM_ID),
                USER_ID,
                EXAM_ID,
                NOW
        );
        when(outboxRepository.existsByEventKey(ExamCompletionNotificationService.eventKey(EXAM_ID)))
                .thenReturn(false, true);
        when(outboxRepository.findByEventKey(expected.getEventKey()))
                .thenReturn(Optional.of(expected));
        when(outboxRepository.insert(any(NotificationOutbox.class)))
                .thenThrow(new DuplicateKeyException("synthetic expected event key duplicate"));

        service.reconcileAfterQuestionCompletion(EXAM_ID);

        outboxStored.set(false);
        storedOutbox.set(null);
        when(outboxRepository.existsByEventKey(ExamCompletionNotificationService.eventKey(EXAM_ID)))
                .thenReturn(false);
        when(outboxRepository.findByEventKey(expected.getEventKey()))
                .thenReturn(Optional.empty());
        when(outboxRepository.insert(any(NotificationOutbox.class)))
                .thenThrow(new DuplicateKeyException("synthetic unrelated duplicate"));

        assertThrows(
                DuplicateKeyException.class,
                () -> service.reconcileAfterQuestionCompletion(EXAM_ID)
        );
    }

    @Test
    void outboxFailureRollsBackCompletionStateInTheTransactionBoundary() throws Exception {
        when(outboxRepository.insert(any(NotificationOutbox.class)))
                .thenThrow(new IllegalStateException("synthetic outbox storage failure"));

        assertThrows(
                IllegalStateException.class,
                () -> service.completeSummaryCallback(
                        summaryRequest(), activeSession(), "mock_exam_003")
        );

        assertAll(
                () -> assertFalse(summaryStored.get()),
                () -> assertFalse(sessionCompleted.get()),
                () -> assertFalse(summaryJobCompleted.get()),
                () -> assertFalse(outboxStored.get())
        );
    }

    private ExamCompletionNotificationService newService(TransactionOperations operations) {
        return new ExamCompletionNotificationService(
                operations,
                examSummaryRepository,
                examResultRepository,
                examSessionRepository,
                summaryJobRepository,
                outboxRepository,
                examSessionManager,
                gradingService,
                identityCodec,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private ExamRequestDTO.AiResultReq summaryRequest() throws Exception {
        return objectMapper.readValue("""
                {
                  "user_id": "ex_notification_complete_001",
                  "suggested_total_score": 170,
                  "level_estimate": "Advanced Mid",
                  "summary": "synthetic summary",
                  "overall_feedback": "synthetic overall feedback",
                  "part_feedback": {"part1": "synthetic part feedback"},
                  "strengths": ["synthetic strength"],
                  "weaknesses": ["synthetic weakness"],
                  "recommended_practice": ["synthetic practice"]
                }
                """, ExamRequestDTO.AiResultReq.class);
    }

    private ExamSession activeSession() {
        return ExamSession.builder()
                .examId(EXAM_ID)
                .userId(USER_ID)
                .mockExamId("mock_exam_003")
                .active(true)
                .build();
    }

    private ExamSession completedSession() {
        return ExamSession.builder()
                .examId(EXAM_ID)
                .userId(USER_ID)
                .mockExamId("mock_exam_003")
                .active(false)
                .completedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC))
                .build();
    }

    private final class SnapshotTransactionOperations implements TransactionOperations {

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            transactionCount.incrementAndGet();
            boolean summaryBefore = summaryStored.get();
            boolean sessionBefore = sessionCompleted.get();
            boolean jobBefore = summaryJobCompleted.get();
            boolean outboxBefore = outboxStored.get();
            NotificationOutbox storedBefore = storedOutbox.get();
            try {
                return action.doInTransaction(new SimpleTransactionStatus());
            } catch (RuntimeException failure) {
                summaryStored.set(summaryBefore);
                sessionCompleted.set(sessionBefore);
                summaryJobCompleted.set(jobBefore);
                outboxStored.set(outboxBefore);
                storedOutbox.set(storedBefore);
                throw failure;
            }
        }
    }
}
