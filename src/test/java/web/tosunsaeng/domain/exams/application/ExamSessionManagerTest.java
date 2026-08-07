package web.tosunsaeng.domain.exams.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionCompletionQuery;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamSessionManagerTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000031";
    private static final Instant NOW = Instant.parse("2026-07-29T06:30:00Z");

    @Mock
    private ExamSessionRepository examSessionRepository;

    @Mock
    private ExamSessionCompletionQuery examSessionCompletionQuery;

    @Mock
    private ExamCompletionEvidenceService completionEvidenceService;

    @Mock
    private MockExamCatalogService mockExamCatalogService;

    private ExamSessionManager manager;
    private List<MockExamCatalogService.CatalogExam> catalog;

    @BeforeEach
    void setUp() {
        manager = new ExamSessionManager(
                examSessionRepository,
                examSessionCompletionQuery,
                completionEvidenceService,
                mockExamCatalogService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        catalog = List.of(catalogExam(1), catalogExam(2), catalogExam(3));
    }

    @Test
    void firstStartCreatesNewInProgressSession() {
        stubNewAssignment(List.of(), List.of());

        ExamSessionManager.Assignment assignment = manager.startNew(USER_ID);

        assertSelected(assignment, 1, 1);
        verify(examSessionRepository, never()).abandonIfInProgress(any());
    }

    @Test
    void completionCountsContinueToSelectLeastCompletedPaper() {
        stubNewAssignment(List.of(), List.of(
                new ExamSessionCompletionQuery.CompletionCount("mock_exam_001", 1)
        ));

        ExamSessionManager.Assignment assignment = manager.startNew(USER_ID);

        assertSelected(assignment, 2, 1);
    }

    @Test
    void startingNewExamAbandonsExistingInProgressSession() {
        ExamSession existing = inProgress("exam_A", "mock_exam_001");
        stubNewAssignment(List.of(existing), List.of());
        when(examSessionRepository.abandonIfInProgress("exam_A")).thenReturn(1L);

        ExamSessionManager.Assignment assignment = manager.startNew(USER_ID);

        assertAll(
                () -> assertNotEquals("exam_A", assignment.session().getExamId()),
                () -> assertEquals(ExamSessionStatus.IN_PROGRESS, assignment.session().getStatus()),
                () -> assertTrue(assignment.session().isActive())
        );
        verify(examSessionRepository).abandonIfInProgress("exam_A");
    }

    @Test
    void completedSessionIsNotAbandonedWhenStartingNewExam() {
        ExamSession completed = ExamSession.builder()
                .examId("exam_completed")
                .userId(USER_ID)
                .mockExamId("mock_exam_001")
                .active(false)
                .status(ExamSessionStatus.COMPLETED)
                .completedAt(LocalDateTime.of(2026, 7, 28, 12, 0))
                .build();
        stubNewAssignment(List.of(completed), List.of(
                new ExamSessionCompletionQuery.CompletionCount("mock_exam_001", 1)
        ));

        ExamSessionManager.Assignment assignment = manager.startNew(USER_ID);

        assertAll(
                () -> assertSelected(assignment, 2, 1),
                () -> assertTrue(completed.isCompleted()),
                () -> assertEquals(LocalDateTime.of(2026, 7, 28, 12, 0), completed.getCompletedAt())
        );
        verify(examSessionRepository, never()).abandonIfInProgress("exam_completed");
    }

    @Test
    void explicitInProgressStatusIsAbandonedEvenWhenLegacyActiveFlagIsFalse() {
        ExamSession inconsistent = ExamSession.builder()
                .examId("exam_in_progress_with_stale_active")
                .userId(USER_ID)
                .mockExamId("mock_exam_001")
                .active(false)
                .status(ExamSessionStatus.IN_PROGRESS)
                .build();
        stubNewAssignment(List.of(inconsistent), List.of());
        when(examSessionRepository.abandonIfInProgress(inconsistent.getExamId())).thenReturn(1L);

        manager.startNew(USER_ID);

        verify(examSessionRepository).abandonIfInProgress(inconsistent.getExamId());
    }

    @Test
    void allAbnormalMultipleInProgressSessionsAreAbandoned() {
        ExamSession first = inProgress("exam_A", "mock_exam_001");
        ExamSession second = inProgress("exam_B", "mock_exam_002");
        stubNewAssignment(List.of(first, second), List.of());
        when(examSessionRepository.abandonIfInProgress("exam_A")).thenReturn(1L);
        when(examSessionRepository.abandonIfInProgress("exam_B")).thenReturn(1L);

        ExamSessionManager.Assignment assignment = manager.startNew(USER_ID);

        assertAll(
                () -> assertNotEquals("exam_A", assignment.session().getExamId()),
                () -> assertNotEquals("exam_B", assignment.session().getExamId()),
                () -> assertEquals(ExamSessionStatus.IN_PROGRESS, assignment.session().getStatus())
        );
        verify(examSessionRepository).abandonIfInProgress("exam_A");
        verify(examSessionRepository).abandonIfInProgress("exam_B");
    }

    @Test
    void legacyInProgressSessionIsAbandonedInsteadOfReused() {
        ExamSession legacy = ExamSession.builder()
                .examId("exam_legacy")
                .userId(USER_ID)
                .mockExamId("mock_exam_001")
                .createdAt(LocalDateTime.of(2024, 1, 1, 9, 0))
                .build();
        when(examSessionRepository.findActiveOrLegacyCandidatesByUserId(USER_ID))
                .thenReturn(List.of(legacy));
        when(completionEvidenceService.findCompletionEvidence(legacy.getExamId(), legacy.getCreatedAt()))
                .thenReturn(noCompletionEvidence());
        when(examSessionRepository.abandonIfInProgress(legacy.getExamId())).thenReturn(1L);
        stubCatalogAndInsert(List.of());

        ExamSessionManager.Assignment assignment = manager.startNew(USER_ID);

        assertNotEquals(legacy.getExamId(), assignment.session().getExamId());
        verify(examSessionRepository).abandonIfInProgress(legacy.getExamId());
    }

    @Test
    void legacyCompletionEvidenceIsBackfilledAndNotAbandoned() {
        LocalDateTime completedAt = LocalDateTime.of(2024, 1, 31, 6, 30);
        ExamSession legacy = ExamSession.builder()
                .examId("exam_legacy_completed")
                .userId(USER_ID)
                .mockExamId("mock_exam_001")
                .createdAt(LocalDateTime.of(2024, 1, 1, 9, 0))
                .build();
        when(examSessionRepository.findActiveOrLegacyCandidatesByUserId(USER_ID))
                .thenReturn(List.of(legacy));
        when(completionEvidenceService.findCompletionEvidence(legacy.getExamId(), legacy.getCreatedAt()))
                .thenReturn(completionEvidence(completedAt));
        when(examSessionRepository.backfillLegacyCompletionIfUnchanged(legacy.getExamId(), completedAt))
                .thenReturn(1L);
        stubCatalogAndInsert(List.of(
                new ExamSessionCompletionQuery.CompletionCount("mock_exam_001", 1)
        ));

        ExamSessionManager.Assignment assignment = manager.startNew(USER_ID);

        assertSelected(assignment, 2, 1);
        verify(examSessionRepository).backfillLegacyCompletionIfUnchanged(legacy.getExamId(), completedAt);
        verify(examSessionRepository, never()).abandonIfInProgress(legacy.getExamId());
    }

    @Test
    void duplicateKeyDuringSessionCreationRetries() {
        ExamSession concurrentSession = inProgress("exam_concurrent", "mock_exam_001");
        ExamSession savedSession = ExamSession.builder()
                .examId("ex_saved_after_retry")
                .userId(USER_ID)
                .createdAt(LocalDateTime.of(2026, 7, 29, 6, 30))
                .mockExamId("mock_exam_001")
                .cycleNumber(1)
                .active(true)
                .status(ExamSessionStatus.IN_PROGRESS)
                .build();
        when(examSessionRepository.findActiveOrLegacyCandidatesByUserId(USER_ID))
                .thenReturn(List.of(), List.of(concurrentSession));
        when(examSessionRepository.abandonIfInProgress(concurrentSession.getExamId())).thenReturn(1L);
        when(examSessionCompletionQuery.countCompletedByMockExamId(USER_ID)).thenReturn(List.of());
        when(mockExamCatalogService.findAssignableExams()).thenReturn(catalog);
        when(examSessionRepository.insert(any(ExamSession.class)))
                .thenThrow(new DuplicateKeyException("duplicate active Session"))
                .thenReturn(savedSession);

        ExamSessionManager.Assignment assignment = manager.startNew(USER_ID);

        assertAll(
                () -> assertTrue(assignment.created()),
                () -> assertEquals(savedSession.getExamId(), assignment.session().getExamId()),
                () -> assertEquals(ExamSessionStatus.IN_PROGRESS, assignment.session().getStatus()),
                () -> assertNotEquals(concurrentSession.getExamId(), assignment.session().getExamId())
        );
        verify(examSessionRepository, times(2)).insert(any(ExamSession.class));
        verify(examSessionRepository).abandonIfInProgress(concurrentSession.getExamId());
    }

    @Test
    void concurrentStartsLeaveExactlyOneActiveSessionAndNeverReuseExamId() throws Exception {
        Map<String, ExamSession> sessions = new ConcurrentHashMap<>();
        CountDownLatch initialLookups = new CountDownLatch(2);
        CountDownLatch releaseLookups = new CountDownLatch(1);
        AtomicInteger lookupCount = new AtomicInteger();

        when(examSessionRepository.findActiveOrLegacyCandidatesByUserId(USER_ID)).thenAnswer(invocation -> {
            if (lookupCount.incrementAndGet() <= 2) {
                initialLookups.countDown();
                assertTrue(releaseLookups.await(5, TimeUnit.SECONDS));
            }
            synchronized (sessions) {
                return sessions.values().stream()
                        .filter(ExamSession::isInProgress)
                        .toList();
            }
        });
        when(examSessionRepository.abandonIfInProgress(any())).thenAnswer(invocation -> {
            String examId = invocation.getArgument(0);
            synchronized (sessions) {
                ExamSession current = sessions.get(examId);
                if (current == null || !current.isInProgress()) {
                    return 0L;
                }
                sessions.put(examId, copyWithStatus(current, ExamSessionStatus.ABANDONED, false));
                return 1L;
            }
        });
        when(examSessionCompletionQuery.countCompletedByMockExamId(USER_ID)).thenReturn(List.of());
        when(mockExamCatalogService.findAssignableExams()).thenReturn(catalog);
        when(examSessionRepository.insert(any(ExamSession.class))).thenAnswer(invocation -> {
            ExamSession candidate = invocation.getArgument(0);
            synchronized (sessions) {
                if (sessions.values().stream().anyMatch(ExamSession::isInProgress)) {
                    throw new DuplicateKeyException("partial unique active-user index");
                }
                sessions.put(candidate.getExamId(), candidate);
                return candidate;
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ExamSessionManager.Assignment> first = executor.submit(() -> manager.startNew(USER_ID));
            Future<ExamSessionManager.Assignment> second = executor.submit(() -> manager.startNew(USER_ID));
            assertTrue(initialLookups.await(5, TimeUnit.SECONDS));
            releaseLookups.countDown();

            ExamSessionManager.Assignment firstResult = first.get(5, TimeUnit.SECONDS);
            ExamSessionManager.Assignment secondResult = second.get(5, TimeUnit.SECONDS);
            long activeCount = sessions.values().stream().filter(ExamSession::isInProgress).count();

            assertAll(
                    () -> assertEquals(1, activeCount),
                    () -> assertNotEquals(firstResult.session().getExamId(), secondResult.session().getExamId()),
                    () -> assertTrue(firstResult.created()),
                    () -> assertTrue(secondResult.created()),
                    () -> assertEquals(2, sessions.size())
            );
        } finally {
            releaseLookups.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void completionUsesClockAndChangesStateOnlyOnce() {
        AtomicReference<LocalDateTime> storedCompletion = new AtomicReference<>();
        AtomicBoolean active = new AtomicBoolean(true);
        when(examSessionRepository.completeIfIncomplete(eq("ex_complete_001"), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    if (!storedCompletion.compareAndSet(null, invocation.getArgument(1))) {
                        return 0L;
                    }
                    active.set(false);
                    return 1L;
                });

        boolean first = manager.completeIfIncomplete("ex_complete_001");
        boolean duplicate = manager.completeIfIncomplete("ex_complete_001");

        assertAll(
                () -> assertTrue(first),
                () -> assertFalse(duplicate),
                () -> assertFalse(active.get()),
                () -> assertEquals(LocalDateTime.of(2026, 7, 29, 6, 30), storedCompletion.get())
        );
    }

    private void stubNewAssignment(
            List<ExamSession> inProgress,
            List<ExamSessionCompletionQuery.CompletionCount> completionCounts) {
        when(examSessionRepository.findActiveOrLegacyCandidatesByUserId(USER_ID)).thenReturn(inProgress);
        stubCatalogAndInsert(completionCounts);
    }

    private void stubCatalogAndInsert(List<ExamSessionCompletionQuery.CompletionCount> completionCounts) {
        when(examSessionCompletionQuery.countCompletedByMockExamId(USER_ID)).thenReturn(completionCounts);
        when(mockExamCatalogService.findAssignableExams()).thenReturn(catalog);
        when(examSessionRepository.insert(any(ExamSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static void assertSelected(
            ExamSessionManager.Assignment assignment,
            int sequence,
            int cycleNumber) {
        ExamSession session = assignment.session();
        assertAll(
                () -> assertTrue(assignment.created()),
                () -> assertEquals("mock_exam_%03d".formatted(sequence), session.getMockExamId()),
                () -> assertEquals(cycleNumber, session.getCycleNumber()),
                () -> assertEquals(ExamSessionStatus.IN_PROGRESS, session.getStatus()),
                () -> assertTrue(session.isActive()),
                () -> assertNull(session.getCompletedAt()),
                () -> assertEquals(USER_ID, session.getUserId()),
                () -> assertEquals(LocalDateTime.of(2026, 7, 29, 6, 30), session.getCreatedAt()),
                () -> assertTrue(session.getExamId().matches("^ex_[0-9a-f]{10}_0729_0630$"))
        );
    }

    private static ExamSession inProgress(String examId, String mockExamId) {
        return ExamSession.builder()
                .examId(examId)
                .userId(USER_ID)
                .mockExamId(mockExamId)
                .cycleNumber(1)
                .active(true)
                .status(ExamSessionStatus.IN_PROGRESS)
                .build();
    }

    private static ExamSession copyWithStatus(
            ExamSession source,
            ExamSessionStatus status,
            boolean active) {
        return ExamSession.builder()
                .examId(source.getExamId())
                .userId(source.getUserId())
                .createdAt(source.getCreatedAt())
                .mockExamId(source.getMockExamId())
                .cycleNumber(source.getCycleNumber())
                .active(active)
                .status(status)
                .completedAt(source.getCompletedAt())
                .build();
    }

    private static MockExamCatalogService.CatalogExam catalogExam(int sequence) {
        return new MockExamCatalogService.CatalogExam(mockExam(sequence), sequence);
    }

    private static MockExam mockExam(int sequence) {
        return MockExam.builder()
                .mockExamId("mock_exam_%03d".formatted(sequence))
                .sequence(sequence)
                .active(true)
                .questions(List.of(Question.builder().questionNumber(1).build()))
                .build();
    }

    private static ExamCompletionEvidenceService.CompletionEvidence noCompletionEvidence() {
        return new ExamCompletionEvidenceService.CompletionEvidence(false, null, "none", false, 0, 0);
    }

    private static ExamCompletionEvidenceService.CompletionEvidence completionEvidence(
            LocalDateTime completedAt) {
        return new ExamCompletionEvidenceService.CompletionEvidence(
                true,
                completedAt,
                "exam_summaries.createdAt",
                false,
                1,
                0
        );
    }
}
