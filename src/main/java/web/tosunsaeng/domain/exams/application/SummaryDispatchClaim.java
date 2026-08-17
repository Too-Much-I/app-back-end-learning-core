package web.tosunsaeng.domain.exams.application;

import web.tosunsaeng.domain.exams.domain.entity.SummaryGradingJob;

import java.time.Instant;

public record SummaryDispatchClaim(
        String jobId,
        int generationAttempt,
        int dispatchAttempt,
        Instant claimedAt,
        String examId,
        String mockExamId
) {

    public SummaryDispatchClaim(
            String jobId,
            int dispatchAttempt,
            Instant claimedAt,
            String examId,
            String mockExamId) {
        this(jobId, 1, dispatchAttempt, claimedAt, examId, mockExamId);
    }

    public SummaryDispatchClaim(String jobId, int dispatchAttempt, Instant claimedAt, String examId) {
        this(jobId, 1, dispatchAttempt, claimedAt, examId, null);
    }

    public static SummaryDispatchClaim from(SummaryGradingJob job) {
        return from(job, GradingKeys.effectiveMockExamId(job.getMockExamId()));
    }

    public static SummaryDispatchClaim from(SummaryGradingJob job, String mockExamId) {
        return new SummaryDispatchClaim(
                job.getJobId(),
                job.effectiveGenerationAttempt(),
                job.getDispatchAttempt(),
                job.getLastDispatchedAt(),
                job.getExamId(),
                mockExamId
        );
    }
}
