package web.tosunsaeng.domain.exams.attemptgroup.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.exams.application.GradingKeys;
import web.tosunsaeng.domain.exams.application.MockExamCatalogService;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupCompletionEvidence;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupFailureCode;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupProjectionStatus;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventProperties;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.exams.domain.entity.QuestionGradingJob;
import web.tosunsaeng.domain.exams.domain.entity.SummaryGradingJob;
import web.tosunsaeng.domain.exams.domain.enums.GradingJobStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
import web.tosunsaeng.domain.exams.domain.repository.QuestionGradingJobRepository;
import web.tosunsaeng.domain.exams.domain.repository.SummaryGradingJobRepository;
import web.tosunsaeng.global.config.GradingProperties;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AttemptGroupEvidenceEvaluator {
    private final MockExamCatalogService catalogService;
    private final ExamResultRepository resultRepository;
    private final ExamSummaryRepository summaryRepository;
    private final QuestionGradingJobRepository questionJobRepository;
    private final SummaryGradingJobRepository summaryJobRepository;
    private final GradingProperties gradingProperties;
    private final AttemptGroupEventProperties eventProperties;

    public Evaluation evaluate(ExamSession session, Instant now) {
        List<Integer> required = requiredQuestionNumbers(session);
        List<ExamResult> allResults = safe(resultRepository.findByExamId(session.getExamId()));
        List<QuestionGradingJob> questionJobs = safe(
                questionJobRepository.findByExamIdAndRetryCount(session.getExamId(), 0));

        Map<Integer, QuestionGradingJob> jobs = new HashMap<>();
        boolean duplicateJobs = false;
        for (QuestionGradingJob job : questionJobs) {
            if (job.getQuestionNumber() == null) {
                continue;
            }
            duplicateJobs |= jobs.putIfAbsent(job.getQuestionNumber(), job) != null;
        }

        Set<Integer> submitted = new HashSet<>();
        jobs.keySet().stream().filter(required::contains).forEach(submitted::add);
        allResults.stream()
                .filter(this::isInitialQuestionResult)
                .map(ExamResult::getQuestionNumber)
                .filter(required::contains)
                .forEach(submitted::add);
        boolean gradingReady = required.stream().allMatch(submitted::contains);

        ResultEvidence resultEvidence = inspectResults(session, required, allResults);
        SummaryEvidence summaryEvidence = inspectSummary(session);
        boolean integrityViolation = duplicateJobs
                || resultEvidence.integrityViolation()
                || summaryEvidence.integrityViolation();

        if (!integrityViolation
                && resultEvidence.queryable()
                && summaryEvidence.validScore()
                && summaryEvidence.queryable()) {
            return new Evaluation(
                    gradingReady,
                    true,
                    AttemptGroupCompletionEvidence.complete(),
                    null,
                    false
            );
        }
        if (integrityViolation) {
            return new Evaluation(gradingReady, false, null,
                    AttemptGroupFailureCode.RESULT_INTEGRITY_VIOLATION, false);
        }

        boolean activeWork = questionJobs.stream().anyMatch(job -> activeQuestionWork(job, now))
                || summaryJobRepository.findById(GradingKeys.summaryJobId(session.getExamId()))
                .map(job -> activeSummaryWork(job, now))
                .orElse(false);
        boolean requiredExhausted = required.stream().anyMatch(questionNumber -> {
            QuestionGradingJob job = jobs.get(questionNumber);
            return !resultEvidence.completedQuestions().contains(questionNumber)
                    && job != null
                    && job.getStatus() == GradingJobStatus.FAILED
                    && job.getDispatchAttempt() >= gradingProperties.maxDispatchAttempts();
        });
        if (requiredExhausted && !activeWork) {
            return new Evaluation(gradingReady, false, null,
                    AttemptGroupFailureCode.REQUIRED_RESULTS_UNAVAILABLE, false);
        }

        boolean summaryExhausted = resultEvidence.queryable()
                && !summaryEvidence.queryable()
                && summaryJobRepository.findById(GradingKeys.summaryJobId(session.getExamId()))
                .filter(job -> job.getStatus() == GradingJobStatus.FAILED)
                .filter(job -> job.getDispatchAttempt() >= gradingProperties.maxDispatchAttempts()
                        || "FEEDBACK_GENERATION_FAILED".equals(job.getFailureReason()))
                .isPresent();
        if (summaryExhausted && !activeWork) {
            return new Evaluation(gradingReady, false, null,
                    AttemptGroupFailureCode.SUMMARY_UNAVAILABLE, false);
        }

        Instant gradingStartedAt = session.getGradingStartedAt();
        boolean deadlineExceeded = gradingStartedAt != null
                && !now.isBefore(gradingStartedAt.plus(eventProperties.gradingDeadline()));
        if (deadlineExceeded && !activeWork) {
            return new Evaluation(gradingReady, false, null,
                    AttemptGroupFailureCode.GRADING_DEADLINE_EXCEEDED, false);
        }
        return new Evaluation(gradingReady, false, null, null, activeWork);
    }

    private ResultEvidence inspectResults(
            ExamSession session,
            List<Integer> required,
            List<ExamResult> results
    ) {
        Map<Integer, ExamResult> logical = new HashMap<>();
        boolean integrityViolation = false;
        boolean feedbackQueryable = true;
        for (ExamResult result : results) {
            if (!isInitialQuestionResult(result) || !required.contains(result.getQuestionNumber())) {
                continue;
            }
            ExamResult previous = logical.putIfAbsent(result.getQuestionNumber(), result);
            integrityViolation |= previous != null;
            integrityViolation |= !Objects.equals(result.getExamId(), session.getExamId())
                    || !Objects.equals(result.getUserId(), session.getUserId())
                    || !Objects.equals(
                    GradingKeys.effectiveMockExamId(result.getMockExamId()),
                    GradingKeys.effectiveMockExamId(session.getMockExamId()));
            feedbackQueryable &= result.getFeedback() != null;
        }
        return new ResultEvidence(
                required.stream().allMatch(logical::containsKey)
                        && feedbackQueryable && !integrityViolation,
                integrityViolation,
                Set.copyOf(logical.keySet())
        );
    }

    private SummaryEvidence inspectSummary(ExamSession session) {
        ExamSummary summary = summaryRepository.findById(GradingKeys.summaryJobId(session.getExamId()))
                .orElse(null);
        if (summary == null) {
            return new SummaryEvidence(false, false, false);
        }
        boolean identityValid = Objects.equals(summary.getExamId(), session.getExamId())
                && Objects.equals(summary.getUserId(), session.getUserId())
                && Objects.equals(
                GradingKeys.effectiveMockExamId(summary.getMockExamId()),
                GradingKeys.effectiveMockExamId(session.getMockExamId()));
        boolean scorePresent = summary.getTotalScore() != null;
        boolean scoreValid = scorePresent
                && summary.getTotalScore() >= 0
                && summary.getTotalScore() <= 200;
        boolean queryable = summary.getPartFeedback() != null
                && !summary.getPartFeedback().isEmpty();
        boolean scoreOutOfRange = scorePresent && !scoreValid;
        return new SummaryEvidence(queryable && identityValid, scoreValid && identityValid,
                !identityValid || scoreOutOfRange);
    }

    private List<Integer> requiredQuestionNumbers(ExamSession session) {
        return catalogService.getRequiredExam(GradingKeys.effectiveMockExamId(session.getMockExamId()))
                .getQuestions().stream()
                .filter(Objects::nonNull)
                .map(question -> question.getQuestionNumber())
                .filter(number -> number != null && number > 0)
                .distinct()
                .sorted()
                .toList();
    }

    private boolean isInitialQuestionResult(ExamResult result) {
        return result.getQuestionNumber() != null
                && (result.getRetryCount() == null || result.getRetryCount() == 0);
    }

    private boolean activeQuestionWork(QuestionGradingJob job, Instant now) {
        return job.getStatus() == GradingJobStatus.PROCESSING
                && job.getProcessingStartedAt() != null
                && now.isBefore(job.getProcessingStartedAt().plus(gradingProperties.processingTimeout()));
    }

    private boolean activeSummaryWork(SummaryGradingJob job, Instant now) {
        return job.getStatus() == GradingJobStatus.PROCESSING
                && job.getProcessingStartedAt() != null
                && now.isBefore(job.getProcessingStartedAt().plus(gradingProperties.processingTimeout()));
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record Evaluation(
            boolean gradingReady,
            boolean completed,
            AttemptGroupCompletionEvidence completionEvidence,
            AttemptGroupFailureCode failureCode,
            boolean activeWork
    ) {
    }

    private record ResultEvidence(boolean queryable, boolean integrityViolation,
                                  Set<Integer> completedQuestions) {
    }

    private record SummaryEvidence(boolean queryable, boolean validScore,
                                   boolean integrityViolation) {
    }
}
