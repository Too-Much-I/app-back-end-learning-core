package web.tosunsaeng.domain.exams.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.SummaryGradingJob;
import web.tosunsaeng.domain.exams.domain.enums.GradingJobStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.SummaryGradingJobRepository;
import web.tosunsaeng.global.config.GradingProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
public class SummaryDispatchScheduler {

    static final String SUMMARY_DISPATCH_FAILED = "SUMMARY_DISPATCH_FAILED";
    static final String EXAM_ABANDONED = "EXAM_ABANDONED";

    private final TaskExecutor taskExecutor;
    private final SummaryGradingJobRepository summaryJobRepository;
    private final ExamSessionRepository examSessionRepository;
    private final GradingDispatchService dispatchService;
    private final GradingProperties properties;
    private final Clock clock;

    public SummaryDispatchScheduler(
            @Qualifier("summaryDispatchExecutor") TaskExecutor taskExecutor,
            SummaryGradingJobRepository summaryJobRepository,
            ExamSessionRepository examSessionRepository,
            GradingDispatchService dispatchService,
            GradingProperties properties,
            Clock clock) {
        this.taskExecutor = taskExecutor;
        this.summaryJobRepository = summaryJobRepository;
        this.examSessionRepository = examSessionRepository;
        this.dispatchService = dispatchService;
        this.properties = properties;
        this.clock = clock;
    }

    public boolean schedulePending(String jobId) {
        return schedule(jobId, DispatchMode.PENDING_ONLY);
    }

    public boolean scheduleRetry(String jobId) {
        return schedule(jobId, DispatchMode.RETRY_ELIGIBLE);
    }

    private boolean schedule(String jobId, DispatchMode mode) {
        try {
            taskExecutor.execute(() -> claimAndDispatch(jobId, mode));
            return true;
        } catch (TaskRejectedException rejected) {
            log.warn(
                    "event=grading.summary.schedule outcome=rejected reason=executor_rejected "
                            + "jobId={} mode={}",
                    jobId, mode
            );
            return false;
        }
    }

    private void claimAndDispatch(String jobId, DispatchMode mode) {
        Optional<SummaryGradingJob> existing = summaryJobRepository.findById(jobId);
        if (existing.isEmpty()) {
            return;
        }

        SummaryGradingJob job = existing.get();
        if (!isSessionInProgress(job.getExamId())) {
            return;
        }
        Instant now = clock.instant();
        if (!isEligible(job, mode, now)
                || job.getDispatchAttempt() >= properties.maxDispatchAttempts()) {
            return;
        }

        SummaryGradingJob claimed;
        try {
            job.startProcessing(now);
            claimed = summaryJobRepository.save(job);
        } catch (OptimisticLockingFailureException lostClaim) {
            return;
        }

        SummaryDispatchClaim claim = SummaryDispatchClaim.from(claimed, resolveMockExamId(claimed));
        if (!isSessionInProgress(claim.examId())) {
            summaryJobRepository.failClaimedAttempt(
                    claim.jobId(),
                    claim.dispatchAttempt(),
                    clock.instant(),
                    EXAM_ABANDONED
            );
            return;
        }
        long startedAt = System.nanoTime();
        try {
            dispatchService.dispatchSummary(claim);
            log.info(
                    "event=grading.summary.dispatch outcome=success jobId={} examId={} "
                            + "dispatchAttempt={} durationMs={}",
                    claim.jobId(),
                    claim.examId(),
                    claim.dispatchAttempt(),
                    elapsedMillis(startedAt)
            );
        } catch (RuntimeException dispatchFailure) {
            long durationMs = elapsedMillis(startedAt);
            long updated = summaryJobRepository.failClaimedAttempt(
                    claim.jobId(),
                    claim.dispatchAttempt(),
                    clock.instant(),
                    SUMMARY_DISPATCH_FAILED
            );
            if (updated == 0) {
                log.debug(
                        "event=grading.summary.dispatch outcome=stale_failure_ignored "
                                + "jobId={} dispatchAttempt={} durationMs={} errorType={}",
                        claim.jobId(),
                        claim.dispatchAttempt(),
                        durationMs,
                        dispatchFailure.getClass().getName()
                );
            } else {
                log.error(
                        "event=grading.summary.dispatch outcome=failure reason={} jobId={} examId={} "
                                + "dispatchAttempt={} durationMs={} stage={} stageDurationMs={} "
                                + "errorType={} rootCauseType={}",
                        SUMMARY_DISPATCH_FAILED,
                        claim.jobId(),
                        claim.examId(),
                        claim.dispatchAttempt(),
                        durationMs,
                        GradingDispatchException.stageCode(dispatchFailure),
                        GradingDispatchException.stageDurationMs(dispatchFailure),
                        dispatchFailure.getClass().getName(),
                        rootCauseType(dispatchFailure)
                );
            }
        }
    }

    private boolean isEligible(SummaryGradingJob job, DispatchMode mode, Instant now) {
        if (mode == DispatchMode.PENDING_ONLY) {
            return job.getStatus() == GradingJobStatus.PENDING;
        }
        return switch (job.getStatus()) {
            case FAILED -> true;
            case PENDING -> timedOut(job.getPendingAt(), properties.pendingTimeout(), now);
            case PROCESSING -> timedOut(job.getProcessingStartedAt(), properties.processingTimeout(), now);
            case COMPLETED -> false;
        };
    }

    private String resolveMockExamId(SummaryGradingJob job) {
        if (job.getMockExamId() != null && !job.getMockExamId().isBlank()) {
            return job.getMockExamId();
        }
        return examSessionRepository.findById(job.getExamId())
                .map(session -> GradingKeys.effectiveMockExamId(session.getMockExamId()))
                .orElse(GradingKeys.LEGACY_MOCK_EXAM_ID);
    }

    private boolean isSessionInProgress(String examId) {
        return examSessionRepository.findById(examId)
                .map(ExamSession::isInProgress)
                .orElse(false);
    }

    private static boolean timedOut(Instant startedAt, Duration timeout, Instant now) {
        return startedAt == null || !startedAt.plus(timeout).isAfter(now);
    }

    private static long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private static String rootCauseType(Throwable failure) {
        Throwable rootCause = failure;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getClass().getName();
    }

    private enum DispatchMode {
        PENDING_ONLY,
        RETRY_ELIGIBLE
    }
}
