package web.tosunsaeng.domain.exams.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.entity.QuestionGradingJob;
import web.tosunsaeng.domain.exams.domain.enums.GradingJobStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
import web.tosunsaeng.domain.exams.domain.repository.MockExamRepository;
import web.tosunsaeng.domain.exams.domain.repository.QuestionGradingJobRepository;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.auth.CurrentUserProvider;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamReadServiceTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000081";
    private static final String OTHER_USER_ID = "00000000-0000-0000-0000-000000000082";
    private static final String EXAM_ID = "ex_retries_owner";

    @Mock
    private ExamSessionRepository examSessionRepository;

    @Mock
    private MockExamRepository mockExamRepository;

    @Mock
    private ExamSummaryRepository examSummaryRepository;

    @Mock
    private ExamResultRepository examResultRepository;

    @Mock
    private QuestionGradingJobRepository questionGradingJobRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private ExamReadService examReadService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        lenient().when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    void historyUsesCompletedAtDeterministicOrderAndBatchSummaryCompatibility() {
        LocalDateTime newest = LocalDateTime.of(2026, 8, 4, 11, 0);
        LocalDateTime tied = LocalDateTime.of(2026, 8, 4, 10, 0);
        LocalDateTime legacyTime = LocalDateTime.of(2026, 8, 3, 9, 0);
        List<ExamSession> repositoryCandidates = List.of(
                session("ex_tie_a", USER_ID, "mock_exam_004", 4, false, tied),
                session("ex_other", OTHER_USER_ID, "mock_exam_004", 1, false, newest),
                session("ex_legacy", USER_ID, null, 3, null, legacyTime),
                session("ex_incomplete_active", USER_ID, "mock_exam_004", 1, true, null),
                session("ex_tie_z", USER_ID, "mock_exam_004", 2, false, tied),
                session("ex_inactive_without_completion", USER_ID, "mock_exam_004", 1, false, null),
                session("ex_newest", USER_ID, "mock_exam_004", 5, true, newest)
        );
        when(examSessionRepository.findCompletedByUserId(USER_ID)).thenReturn(repositoryCandidates);
        when(mockExamRepository.findTitlesByMockExamIdIn(anyCollection())).thenReturn(List.of(
                MockExam.builder().mockExamId("mock_exam_004").title("모의고사 4").build(),
                MockExam.builder().mockExamId(GradingKeys.LEGACY_MOCK_EXAM_ID).title("Legacy 모의고사").build()
        ));
        when(examSummaryRepository.findHistoryCandidatesByExamIdIn(anyCollection())).thenReturn(List.of(
                summary("summary:ex_newest:0002", "ex_newest", 145, "Advanced High"),
                summary("summary:ex_newest:0001", "ex_newest", 100, "Intermediate"),
                summary("summary:ex_legacy:0001", "ex_legacy", 135, "Advanced Mid")
        ));
        when(examResultRepository.findLegacySummaryCandidatesByExamIdIn(anyCollection())).thenReturn(List.of(
                legacySummary("legacy:ex_newest:9999", "ex_newest", 30, "Novice"),
                legacySummary("legacy:ex_tie_z:0002", "ex_tie_z", 130, "Advanced Low"),
                legacySummary("legacy:ex_tie_z:0001", "ex_tie_z", 120, "Intermediate")
        ));

        ExamResponseDTO.ExamHistoryResult result = examReadService.getExamHistory();

        assertEquals(4, result.getTotalCount());
        assertEquals(
                List.of("ex_newest", "ex_tie_z", "ex_tie_a", "ex_legacy"),
                result.getHistories().stream().map(ExamResponseDTO.ExamHistoryItem::getExamId).toList()
        );
        ExamResponseDTO.ExamHistoryItem newestItem = result.getHistories().getFirst();
        ExamResponseDTO.ExamHistoryItem legacyFallback = result.getHistories().get(1);
        ExamResponseDTO.ExamHistoryItem missingSummary = result.getHistories().get(2);
        ExamResponseDTO.ExamHistoryItem legacyActiveNull = result.getHistories().get(3);
        assertAll(
                () -> assertEquals("모의고사 4", newestItem.getTitle()),
                () -> assertEquals(5, newestItem.getCycleNumber()),
                () -> assertEquals(newest, newestItem.getCompletedAt()),
                () -> assertEquals(145, newestItem.getTotalScore()),
                () -> assertEquals("Advanced High", newestItem.getLevelEstimate()),
                () -> assertTrue(newestItem.isSummaryAvailable()),
                () -> assertEquals(130, legacyFallback.getTotalScore()),
                () -> assertEquals("Advanced Low", legacyFallback.getLevelEstimate()),
                () -> assertTrue(legacyFallback.isSummaryAvailable()),
                () -> assertNull(missingSummary.getTotalScore()),
                () -> assertNull(missingSummary.getLevelEstimate()),
                () -> assertFalse(missingSummary.isSummaryAvailable()),
                () -> assertEquals("Legacy 모의고사", legacyActiveNull.getTitle()),
                () -> assertEquals(135, legacyActiveNull.getTotalScore()),
                () -> assertTrue(legacyActiveNull.isSummaryAvailable())
        );

        JsonNode json = objectMapper.valueToTree(result);
        assertAll(
                () -> assertFalse(json.toString().contains("userId")),
                () -> assertFalse(json.toString().contains("user_id")),
                () -> assertFalse(json.toString().contains("mockExamId"))
        );
        verify(examSessionRepository).findCompletedByUserId(USER_ID);
        verify(mockExamRepository).findTitlesByMockExamIdIn(Set.of("mock_exam_004", "mock_exam_003"));
        verify(examSummaryRepository).findHistoryCandidatesByExamIdIn(Set.of(
                "ex_newest", "ex_tie_z", "ex_tie_a", "ex_legacy"
        ));
        verify(examResultRepository).findLegacySummaryCandidatesByExamIdIn(Set.of(
                "ex_newest", "ex_tie_z", "ex_tie_a", "ex_legacy"
        ));
        verify(mockExamRepository, never()).findAll();
        verify(examSummaryRepository, never()).findFirstByExamIdOrderByIdDesc(anyString());
        verify(examResultRepository, never()).findFirstByExamIdAndTotalScoreIsNotNullOrderByIdDesc(anyString());
        verify(examResultRepository, never()).findByExamId(anyString());
    }

    @Test
    void historyWithoutCompletedSessionsReturnsEmptyListsWithoutMetadataQueries() {
        when(examSessionRepository.findCompletedByUserId(USER_ID)).thenReturn(List.of(
                session("ex_incomplete", USER_ID, "mock_exam_004", 1, false, null)
        ));

        ExamResponseDTO.ExamHistoryResult result = examReadService.getExamHistory();

        assertEquals(0, result.getTotalCount());
        assertEquals(List.of(), result.getHistories());
        verifyNoInteractions(
                mockExamRepository,
                examSummaryRepository,
                examResultRepository,
                questionGradingJobRepository
        );
    }

    @Test
    void retriesMergeJobAndLegacyAttemptsWithoutUsingDispatchAttempt() {
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(
                session(EXAM_ID, USER_ID, "mock_exam_004", 1, true, null)
        ));
        when(examResultRepository.findQuestionAttemptsByExamId(EXAM_ID)).thenReturn(List.of(
                result("result-q1-r1", 1, 1),
                result("result-q2-r0", 2, 0),
                result("result-q3-legacy", 3, null),
                result("result-q3-r1", 3, 1),
                result("result-q4-legacy", 4, null),
                result("result-q4-r1", 4, 1)
        ));
        when(questionGradingJobRepository.findAttemptsByExamId(EXAM_ID)).thenReturn(List.of(
                job("job-q1-r3", 1, 3, GradingJobStatus.FAILED, 0),
                job("job-q1-r0", 1, 0, GradingJobStatus.PENDING, 99),
                job("job-q1-r2", 1, 2, GradingJobStatus.COMPLETED, 77),
                job("job-q1-r1", 1, 1, GradingJobStatus.PROCESSING, 55),
                job("job-q2-r0", 2, 0, GradingJobStatus.COMPLETED, 12),
                job("job-q4-r1", 4, 1, GradingJobStatus.PENDING, 40),
                job("job-q5-r1", 5, 1, GradingJobStatus.PROCESSING, 88)
        ));

        ExamResponseDTO.ExamRetriesResult result = examReadService.getExamRetries(EXAM_ID);

        assertEquals(EXAM_ID, result.getExamId());
        assertEquals(
                List.of(1, 3, 4, 5),
                result.getQuestions().stream()
                        .map(ExamResponseDTO.RetriedQuestionItem::getQuestionNumber)
                        .toList()
        );

        ExamResponseDTO.RetriedQuestionItem questionOne = result.getQuestions().getFirst();
        assertAll(
                () -> assertEquals(1, questionOne.getPartNumber()),
                () -> assertEquals(4, questionOne.getTotalAttemptCount()),
                () -> assertEquals(3, questionOne.getLatestRetryCount()),
                () -> assertEquals(List.of(0, 1, 2, 3), questionOne.getAttempts().stream()
                        .map(ExamResponseDTO.RetryAttemptItem::getRetryCount).toList()),
                () -> assertEquals(
                        List.of(
                                GradingJobStatus.PENDING,
                                GradingJobStatus.PROCESSING,
                                GradingJobStatus.COMPLETED,
                                GradingJobStatus.FAILED
                        ),
                        questionOne.getAttempts().stream()
                                .map(ExamResponseDTO.RetryAttemptItem::getStatus).toList()
                )
        );

        ExamResponseDTO.RetriedQuestionItem legacyOnly = result.getQuestions().get(1);
        assertAll(
                () -> assertEquals(2, legacyOnly.getPartNumber()),
                () -> assertEquals(List.of(0, 1), legacyOnly.getAttempts().stream()
                        .map(ExamResponseDTO.RetryAttemptItem::getRetryCount).toList()),
                () -> assertTrue(legacyOnly.getAttempts().stream()
                        .allMatch(attempt -> attempt.getStatus() == GradingJobStatus.COMPLETED))
        );

        ExamResponseDTO.RetriedQuestionItem duplicateAttempt = result.getQuestions().get(2);
        assertAll(
                () -> assertEquals(2, duplicateAttempt.getTotalAttemptCount()),
                () -> assertEquals(1, duplicateAttempt.getLatestRetryCount()),
                () -> assertEquals(GradingJobStatus.PENDING, duplicateAttempt.getAttempts().get(1).getStatus())
        );

        ExamResponseDTO.RetriedQuestionItem noSyntheticInitial = result.getQuestions().get(3);
        assertAll(
                () -> assertEquals(3, noSyntheticInitial.getPartNumber()),
                () -> assertEquals(1, noSyntheticInitial.getTotalAttemptCount()),
                () -> assertEquals(List.of(1), noSyntheticInitial.getAttempts().stream()
                        .map(ExamResponseDTO.RetryAttemptItem::getRetryCount).toList())
        );

        JsonNode json = objectMapper.valueToTree(result);
        for (String forbiddenField : List.of(
                "dispatchAttempt", "failureReason", "score", "feedback", "transcript",
                "audioUrl", "fileKey", "userId", "user_id"
        )) {
            assertFalse(json.toString().contains(forbiddenField));
        }
        assertFalse(json.toString().contains("55"));
        assertFalse(json.toString().contains("77"));
        assertFalse(json.toString().contains("88"));
        assertFalse(json.toString().contains("99"));
        verify(examResultRepository).findQuestionAttemptsByExamId(EXAM_ID);
        verify(questionGradingJobRepository).findAttemptsByExamId(EXAM_ID);
    }

    @Test
    void retriesWithOnlyInitialAttemptReturnsEmptyQuestions() {
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(
                session(EXAM_ID, USER_ID, "mock_exam_004", 1, true, null)
        ));
        when(examResultRepository.findQuestionAttemptsByExamId(EXAM_ID)).thenReturn(List.of(
                result("legacy-initial", 1, null)
        ));
        when(questionGradingJobRepository.findAttemptsByExamId(EXAM_ID)).thenReturn(List.of(
                job("job-initial", 2, 0, GradingJobStatus.COMPLETED, 15)
        ));

        ExamResponseDTO.ExamRetriesResult result = examReadService.getExamRetries(EXAM_ID);

        assertEquals(EXAM_ID, result.getExamId());
        assertEquals(List.of(), result.getQuestions());
    }

    @Test
    void retriesRejectsAnotherUsersExamBeforeReadingAttempts() {
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(
                session(EXAM_ID, OTHER_USER_ID, "mock_exam_004", 1, true, null)
        ));

        ExamsException exception = assertThrows(
                ExamsException.class,
                () -> examReadService.getExamRetries(EXAM_ID)
        );

        assertSame(ErrorStatus._FORBIDDEN, exception.getCode());
        verifyNoInteractions(examResultRepository, questionGradingJobRepository);
    }

    @Test
    void retriesMissingExamKeepsExistingNotFoundError() {
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.empty());

        ExamsException exception = assertThrows(
                ExamsException.class,
                () -> examReadService.getExamRetries(EXAM_ID)
        );

        assertSame(ErrorStatus._EXAM_NOT_FOUND, exception.getCode());
        verifyNoInteractions(examResultRepository, questionGradingJobRepository);
    }

    private static ExamSession session(
            String examId,
            String userId,
            String mockExamId,
            Integer cycleNumber,
            Boolean active,
            LocalDateTime completedAt) {
        return ExamSession.builder()
                .examId(examId)
                .userId(userId)
                .mockExamId(mockExamId)
                .cycleNumber(cycleNumber)
                .active(active)
                .completedAt(completedAt)
                .build();
    }

    private static ExamSummary summary(
            String id,
            String examId,
            Integer totalScore,
            String levelEstimate) {
        return ExamSummary.builder()
                .id(id)
                .examId(examId)
                .totalScore(totalScore)
                .levelEstimate(levelEstimate)
                .build();
    }

    private static ExamResult legacySummary(
            String id,
            String examId,
            Integer totalScore,
            String levelEstimate) {
        return ExamResult.builder()
                .id(id)
                .examId(examId)
                .totalScore(totalScore)
                .levelEstimate(levelEstimate)
                .build();
    }

    private static ExamResult result(String id, Integer questionNumber, Integer retryCount) {
        return ExamResult.builder()
                .id(id)
                .examId(EXAM_ID)
                .questionNumber(questionNumber)
                .retryCount(retryCount)
                .score(9.5)
                .transcript("not exposed")
                .feedback(ExamResult.ItemFeedback.builder().summary("not exposed").build())
                .build();
    }

    private static QuestionGradingJob job(
            String id,
            Integer questionNumber,
            Integer retryCount,
            GradingJobStatus status,
            int dispatchAttempt) {
        return QuestionGradingJob.builder()
                .jobId(id)
                .examId(EXAM_ID)
                .questionNumber(questionNumber)
                .retryCount(retryCount)
                .status(status)
                .dispatchAttempt(dispatchAttempt)
                .fileKey("not-exposed")
                .failureReason("not-exposed")
                .pendingAt(Instant.EPOCH)
                .build();
    }
}
