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
        String fileKey
) {

    public static QuestionDispatchClaim from(QuestionGradingJob job) {
        return new QuestionDispatchClaim(
                job.getJobId(),
                job.getDispatchAttempt(),
                job.getLastDispatchedAt(),
                job.getExamId(),
                job.getQuestionNumber(),
                job.getRetryCount(),
                job.getFileKey()
        );
    }
}
