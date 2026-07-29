package web.tosunsaeng.domain.exams.application;

import web.tosunsaeng.domain.exams.domain.entity.SummaryGradingJob;

import java.time.Instant;

public record SummaryDispatchClaim(
        String jobId,
        int dispatchAttempt,
        Instant claimedAt,
        String examId
) {

    public static SummaryDispatchClaim from(SummaryGradingJob job) {
        return new SummaryDispatchClaim(
                job.getJobId(),
                job.getDispatchAttempt(),
                job.getLastDispatchedAt(),
                job.getExamId()
        );
    }
}
