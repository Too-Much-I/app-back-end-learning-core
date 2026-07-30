package web.tosunsaeng.domain.exams.application;

import web.tosunsaeng.domain.exams.domain.entity.QuestionGradingJob;

import java.time.Instant;

public record QuestionDispatchClaim(
        String jobId,
        int dispatchAttempt,
        Instant claimedAt,
        String examId,
        Integer questionNumber,
        Integer retryCount,
        String fileKey,
        String mockExamId
) {

    public QuestionDispatchClaim(
            String jobId,
            int dispatchAttempt,
            Instant claimedAt,
            String examId,
            Integer questionNumber,
            Integer retryCount,
            String fileKey) {
        this(jobId, dispatchAttempt, claimedAt, examId, questionNumber, retryCount, fileKey, null);
    }

    public static QuestionDispatchClaim from(QuestionGradingJob job) {
        return from(job, GradingKeys.effectiveMockExamId(job.getMockExamId()));
    }

    public static QuestionDispatchClaim from(QuestionGradingJob job, String mockExamId) {
        return new QuestionDispatchClaim(
                job.getJobId(),
                job.getDispatchAttempt(),
                job.getLastDispatchedAt(),
                job.getExamId(),
                job.getQuestionNumber(),
                job.getRetryCount(),
                job.getFileKey(),
                mockExamId
        );
    }
}
