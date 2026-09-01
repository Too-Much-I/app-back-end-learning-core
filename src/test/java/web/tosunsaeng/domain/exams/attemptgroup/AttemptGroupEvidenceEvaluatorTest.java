package web.tosunsaeng.domain.exams.attemptgroup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import web.tosunsaeng.domain.exams.application.GradingKeys;
import web.tosunsaeng.domain.exams.application.MockExamCatalogService;
import web.tosunsaeng.domain.exams.attemptgroup.application.AttemptGroupEvidenceEvaluator;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupFailureCode;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupProjectionStatus;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventProperties;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.domain.entity.QuestionGradingJob;
import web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
import web.tosunsaeng.domain.exams.domain.repository.QuestionGradingJobRepository;
import web.tosunsaeng.domain.exams.domain.repository.SummaryGradingJobRepository;
import web.tosunsaeng.global.config.GradingProperties;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AttemptGroupEvidenceEvaluatorTest {
    private static final String EXAM_ID = "ex_attempt_group_001";
    private static final String USER_ID = "00000000-0000-4000-8000-000000000001";
    private static final Instant NOW = Instant.parse("2026-09-01T03:00:00Z");

    @Mock MockExamCatalogService catalogService;
    @Mock ExamResultRepository resultRepository;
    @Mock ExamSummaryRepository summaryRepository;
    @Mock QuestionGradingJobRepository questionJobRepository;
    @Mock SummaryGradingJobRepository summaryJobRepository;
    private AttemptGroupEvidenceEvaluator evaluator;
    private ExamSession session;

    @BeforeEach
    void setUp() {
        evaluator = new AttemptGroupEvidenceEvaluator(
                catalogService, resultRepository, summaryRepository,
                questionJobRepository, summaryJobRepository,
                new GradingProperties(Duration.ofMinutes(1), Duration.ofMinutes(3), 3,
                        URI.create("http://ai:8000"), Duration.ofSeconds(2),
                        Duration.ofSeconds(5), 2, 10), properties());
        session = ExamSession.builder()
                .examId(EXAM_ID).userId(USER_ID).mockExamId("mock_exam_001")
                .status(ExamSessionStatus.IN_PROGRESS).active(true)
                .entitlementState(ExamEntitlementState.CONFIRMED)
                .attemptGroupId("018f6f36-2f42-4bf5-8c17-0be35de4872e")
                .attemptGroupProjectionStatus(AttemptGroupProjectionStatus.GRADING)
                .gradingStartedAt(NOW.minus(Duration.ofMinutes(10)))
                .build();
        when(catalogService.getRequiredExam("mock_exam_001")).thenReturn(MockExam.builder()
                .mockExamId("mock_exam_001")
                .questions(List.of(Question.builder().questionNumber(1).build()))
                .build());
        lenient().when(summaryJobRepository.findById(GradingKeys.summaryJobId(EXAM_ID)))
                .thenReturn(Optional.empty());
    }

    @Test
    void strictDeterministicEvidenceCompletes() {
        ExamResult result = initialResult();
        when(resultRepository.findByExamId(EXAM_ID)).thenReturn(List.of(result));
        when(questionJobRepository.findByExamIdAndRetryCount(EXAM_ID, 0))
                .thenReturn(List.of(QuestionGradingJob.completed(
                        GradingKeys.questionJobId(EXAM_ID, 1, 0), EXAM_ID, 1, 0,
                        "key", "mock_exam_001", NOW)));
        when(summaryRepository.findById(GradingKeys.summaryJobId(EXAM_ID)))
                .thenReturn(Optional.of(summary(160)));

        AttemptGroupEvidenceEvaluator.Evaluation evaluation = evaluator.evaluate(session, NOW);

        assertTrue(evaluation.completed());
        assertTrue(evaluation.completionEvidence().requiredFeedbackQueryable());
        assertNull(evaluation.failureCode());
    }

    @Test
    void outOfRangeScoreIsIntegrityViolation() {
        when(resultRepository.findByExamId(EXAM_ID)).thenReturn(List.of(initialResult()));
        when(questionJobRepository.findByExamIdAndRetryCount(EXAM_ID, 0)).thenReturn(List.of());
        when(summaryRepository.findById(GradingKeys.summaryJobId(EXAM_ID)))
                .thenReturn(Optional.of(summary(201)));

        AttemptGroupEvidenceEvaluator.Evaluation evaluation = evaluator.evaluate(session, NOW);

        assertEquals(AttemptGroupFailureCode.RESULT_INTEGRITY_VIOLATION,
                evaluation.failureCode());
    }

    private ExamResult initialResult() {
        return ExamResult.builder()
                .id(GradingKeys.feedbackResultId(EXAM_ID, 1, 0))
                .examId(EXAM_ID).userId(USER_ID).mockExamId("mock_exam_001")
                .questionNumber(1).retryCount(0)
                .feedback(ExamResult.ItemFeedback.builder().summary("ok").build())
                .build();
    }

    private ExamSummary summary(int score) {
        return ExamSummary.builder()
                .id(GradingKeys.summaryJobId(EXAM_ID))
                .examId(EXAM_ID).userId(USER_ID).mockExamId("mock_exam_001")
                .totalScore(score).partFeedback(Map.of("part1", "ok"))
                .build();
    }

    static AttemptGroupEventProperties properties() {
        return new AttemptGroupEventProperties(true, false, "", "ap-northeast-2",
                Duration.ofMinutes(30), Duration.ofSeconds(1), 20, Duration.ofSeconds(30),
                Duration.ofMinutes(15), Duration.ofSeconds(2), Duration.ofSeconds(5),
                Duration.ofDays(30), Duration.ofDays(90));
    }
}
