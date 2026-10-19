package web.tosunsaeng.domain.exams.domain.repository;

import java.time.Instant;

public interface QuestionGradingJobRecovery {
    long reopenCompletedMissingResult(String jobId, int expectedRecoveryCycle, Instant pendingAt);
}
