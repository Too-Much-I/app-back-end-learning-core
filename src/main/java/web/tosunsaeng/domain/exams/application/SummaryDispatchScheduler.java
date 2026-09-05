package web.tosunsaeng.domain.exams.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.SummaryGradingJob;
import web.tosunsaeng.domain.exams.domain.enums.GradingJobStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.SummaryGradingJobRepository;
import web.tosunsaeng.global.config.GradingProperties;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardException;

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

    @Autowired(required = false)
    private UserOwnedTransactionExecutor userOwnedTransactionExecutor;

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

    public boolean schedulePending(String jobId, int generationAttempt) {
        return schedule(jobId, generationAttempt, DispatchMode.PENDING_ONLY);
    }

    public boolean scheduleRetry(String jobId, int generationAttempt) {
        return schedule(jobId, generationAttempt, DispatchMode.RETRY_ELIGIBLE);
    }

    private boolean schedule(String jobId, int generationAttempt, DispatchMode mode) {
        try {
            taskExecutor.execute(() -> claimAndDispatch(jobId, generationAttempt, mode));
            return true;
        } catch (TaskRejectedException rejected) {
            log.warn(
                    "요약 채점 실행 예약 거절 event=grading.summary.schedule "
                            + "outcome=rejected reason=executor_rejected "
                            + "jobId={} generationAttempt={} mode={}",
                    jobId, generationAttempt, mode
            );
            return false;
        }
    }

    private void claimAndDispatch(String jobId, int generationAttempt, DispatchMode mode) {
        SummaryDispatchClaim claim = claim(jobId, generationAttempt, mode);
        if (claim == null) {
            return;
        }
        if (!isSessionInProgress(claim.examId())) {
            failClaim(claim, EXAM_ABANDONED);
            return;
        }
        if (!isCurrentDispatchClaim(claim)) {
            log.debug(
                    "이전 요약 채점 작업 무시 event=grading.summary.dispatch "
                            + "outcome=stale_claim_ignored jobId={} "
                            + "generationAttempt={} dispatchAttempt={}",
                    claim.jobId(), claim.generationAttempt(), claim.dispatchAttempt()
            );
            return;
        }
        long startedAt = System.nanoTime();
        try {
            dispatchService.dispatchSummary(claim);
            log.info(
                    "요약 채점 요청 전송 완료 event=grading.summary.dispatch "
                            + "outcome=success jobId={} examId={} "
                            + "generationAttempt={} dispatchAttempt={} durationMs={}",
                    claim.jobId(),
                    claim.examId(),
                    claim.generationAttempt(),
                    claim.dispatchAttempt(),
                    elapsedMillis(startedAt)
            );
        } catch (RuntimeException dispatchFailure) {
            long durationMs = elapsedMillis(startedAt);
            long updated = failClaim(claim, SUMMARY_DISPATCH_FAILED);
            if (updated == 0) {
                log.debug(
                        "이전 요약 채점 전송 실패 무시 event=grading.summary.dispatch "
                                + "outcome=stale_failure_ignored "
                                + "jobId={} generationAttempt={} dispatchAttempt={} "
                                + "durationMs={} errorType={}",
                        claim.jobId(), claim.generationAttempt(), claim.dispatchAttempt(),
                        durationMs, dispatchFailure.getClass().getName()
                );
            } else {
                log.error(
                        "요약 채점 요청 전송 실패 event=grading.summary.dispatch "
                                + "outcome=failure reason={} jobId={} examId={} "
                                + "generationAttempt={} dispatchAttempt={} durationMs={} "
                                + "stage={} stageDurationMs={} "
                                + "errorType={} rootCauseType={}",
                        SUMMARY_DISPATCH_FAILED,
                        claim.jobId(),
                        claim.examId(),
                        claim.generationAttempt(),
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

    private SummaryDispatchClaim claim(String jobId, int generationAttempt, DispatchMode mode) {
        Optional<SummaryGradingJob> existing = summaryJobRepository.findById(jobId);
        if (existing.isEmpty()) {
            return null;
        }

        SummaryGradingJob job = existing.get();
        if (job.effectiveGenerationAttempt() != generationAttempt) {
            log.debug(
                    "이전 요약 채점 예약 무시 event=grading.summary.schedule "
                            + "outcome=stale_claim_ignored jobId={} "
                            + "generationAttempt={} currentGenerationAttempt={}",
                    jobId, generationAttempt, job.effectiveGenerationAttempt()
            );
            return null;
        }
        if (!isSessionInProgress(job.getExamId())) {
            return null;
        }
        return inCurrentOwnerTransaction(job.getExamId(), () -> {
            SummaryGradingJob current = summaryJobRepository.findById(jobId).orElse(null);
            Instant now = clock.instant();
            if (current == null
                    || current.effectiveGenerationAttempt() != generationAttempt
                    || !isEligible(current, mode, now)
                    || current.getDispatchAttempt() >= properties.maxDispatchAttempts()) {
                return null;
            }
            current.startProcessing(now);
            SummaryGradingJob claimed = summaryJobRepository.save(current);
            return SummaryDispatchClaim.from(claimed, resolveMockExamId(claimed));
        });
    }

    private long failClaim(SummaryDispatchClaim claim, String reason) {
        return inCurrentOwnerTransaction(claim.examId(), () -> summaryJobRepository.failClaimedAttempt(
                claim.jobId(),
                claim.generationAttempt(),
                claim.dispatchAttempt(),
                clock.instant(),
                reason
        ));
    }

    private <T> T inCurrentOwnerTransaction(String examId, java.util.function.Supplier<T> command) {
        if (userOwnedTransactionExecutor == null || !userOwnedTransactionExecutor.enabled()) {
            return command.get();
        }
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            ExamSession observed = examSessionRepository.findById(examId).orElse(null);
            if (observed == null) {
                return null;
            }
            try {
                return userOwnedTransactionExecutor.execute(observed.getUserId(), () -> {
                    ExamSession current = examSessionRepository.findById(examId).orElse(null);
                    if (current == null
                            || !java.util.Objects.equals(current.getUserId(), observed.getUserId())) {
                        throw new SummaryOwnerChangedException();
                    }
                    return command.get();
                });
            } catch (UserOwnershipGuardException | SummaryOwnerChangedException race) {
                lastFailure = race;
            }
        }
        throw lastFailure == null
                ? new IllegalStateException("Summary owner convergence failed")
                : lastFailure;
    }

    private static final class SummaryOwnerChangedException extends RuntimeException {
    }

    private boolean isEligible(SummaryGradingJob job, DispatchMode mode, Instant now) {
        if (job.isCompletionClaimedFor(job.effectiveGenerationAttempt())) {
            return false;
        }
        if (mode == DispatchMode.PENDING_ONLY) {
            return job.getStatus() == GradingJobStatus.PENDING;
        }
        if (job.getStatus() == GradingJobStatus.FAILED
                && ExamGradingService.FEEDBACK_GENERATION_FAILED.equals(job.getFailureReason())) {
            return false;
        }
        return switch (job.getStatus()) {
            case FAILED -> true;
            case PENDING -> timedOut(job.getPendingAt(), properties.pendingTimeout(), now);
            case PROCESSING -> timedOut(job.getProcessingStartedAt(), properties.processingTimeout(), now);
            case COMPLETED -> false;
        };
    }

    private boolean isCurrentDispatchClaim(SummaryDispatchClaim claim) {
        return summaryJobRepository.findById(claim.jobId())
                .filter(job -> job.effectiveGenerationAttempt() == claim.generationAttempt())
                .filter(job -> job.getStatus() == GradingJobStatus.PROCESSING)
                .filter(job -> job.getDispatchAttempt() == claim.dispatchAttempt())
                .filter(job -> !job.isCompletionClaimedFor(claim.generationAttempt()))
                .isPresent();
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
