package web.tosunsaeng.domain.exams.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.QuestionGradingJob;
import web.tosunsaeng.domain.exams.domain.entity.SummaryGradingJob;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;
import web.tosunsaeng.domain.exams.domain.enums.GradingJobStatus;
import web.tosunsaeng.domain.exams.domain.enums.SummaryAction;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
import web.tosunsaeng.domain.exams.domain.repository.QuestionGradingJobRepository;
import web.tosunsaeng.domain.exams.domain.repository.SummaryGradingJobRepository;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.config.GradingProperties;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamGradingService {

    private static final int CALLBACK_COMPLETION_RETRIES = 5;
    private static final String QUESTION_DISPATCH_FAILED = "QUESTION_DISPATCH_FAILED";
    private static final String MAX_DISPATCH_ATTEMPTS = "MAX_DISPATCH_ATTEMPTS";
    private static final String EXAM_ABANDONED = "EXAM_ABANDONED";

    private final QuestionGradingJobRepository questionJobRepository;
    private final SummaryGradingJobRepository summaryJobRepository;
    private final ExamResultRepository examResultRepository;
    private final ExamSummaryRepository examSummaryRepository;
    private final ExamSessionRepository examSessionRepository;
    private final MockExamCatalogService mockExamCatalogService;
    private final S3Client s3Client;
    private final GradingDispatchService dispatchService;
    private final SummaryDispatchScheduler summaryDispatchScheduler;
    private final RedisTemplate<String, Object> redisTemplate;
    private final GradingProperties properties;
    private final Clock clock;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    public ExamStatus submitQuestion(String examId, Integer questionNumber, Integer retryCount) {
        requireNotAbandonedSession(examId);
        int canonicalRetryCount = GradingKeys.canonicalRetryCount(retryCount);
        String mockExamId = resolveMockExamId(examId);
        String jobId = GradingKeys.questionJobId(examId, questionNumber, canonicalRetryCount);
        if (hasQuestionResult(examId, questionNumber, canonicalRetryCount)) {
            completeQuestion(examId, questionNumber, canonicalRetryCount);
            calculateAndCacheOverallStatus(examId);
            return ExamStatus.COMPLETED;
        }

        Instant now = clock.instant();
        QuestionGradingJob pending = QuestionGradingJob.pending(
                jobId,
                examId,
                questionNumber,
                canonicalRetryCount,
                GradingKeys.questionFileKey(examId, questionNumber, canonicalRetryCount),
                mockExamId,
                now
        );

        QuestionGradingJob inserted;
        try {
            inserted = questionJobRepository.insert(pending);
            log.debug(
                    "문항 채점 작업 생성 event=grading.question.job outcome=created "
                            + "jobId={} examId={} "
                            + "questionNumber={} retryCount={}",
                    jobId, examId, questionNumber, canonicalRetryCount
            );
        } catch (DuplicateKeyException duplicate) {
            QuestionGradingJob existing = questionJobRepository.findById(jobId)
                    .orElse(null);
            log.debug(
                    "기존 문항 채점 작업 재사용 event=grading.question.job outcome=reused "
                            + "jobId={} status={} dispatchAttempt={}",
                    jobId,
                    existing == null ? null : existing.getStatus(),
                    existing == null ? null : existing.getDispatchAttempt()
            );
            ExamStatus status = existing == null
                    ? ExamStatus.PENDING
                    : toExamStatus(existing.getStatus());
            calculateAndCacheOverallStatus(examId);
            return status;
        }

        if (hasQuestionResult(examId, questionNumber, canonicalRetryCount)) {
            completeQuestion(examId, questionNumber, canonicalRetryCount);
            calculateAndCacheOverallStatus(examId);
            return ExamStatus.COMPLETED;
        }

        QuestionGradingJob claimed;
        try {
            inserted.startProcessing(now);
            claimed = questionJobRepository.save(inserted);
        } catch (OptimisticLockingFailureException lostClaim) {
            ExamStatus status = questionJobRepository.findById(jobId)
                    .map(job -> toExamStatus(job.getStatus()))
                    .orElse(ExamStatus.PENDING);
            calculateAndCacheOverallStatus(examId);
            return status;
        }

        QuestionDispatchClaim claim = QuestionDispatchClaim.from(claimed);
        if (!canProcessSession(examId)) {
            failQuestionClaim(claim, EXAM_ABANDONED);
            throw new ExamsException(ErrorStatus._EXAM_ABANDONED);
        }
        if (!dispatchQuestion(claim)) {
            calculateAndCacheOverallStatus(examId);
            throw new ExamsException(ErrorStatus._AI_SERVER_CONNECTION_ERROR);
        }

        calculateAndCacheOverallStatus(examId);
        return ExamStatus.PROCESSING;
    }

    public ExamResponseDTO.GradingRetryResult retryExam(String examId) {
        requireInProgressSession(examId);
        List<Integer> questionNumbers = expectedQuestionNumbers(examId);
        List<Integer> retried = new ArrayList<>();
        List<Integer> waiting = new ArrayList<>();
        List<Integer> missing = new ArrayList<>();

        for (Integer questionNumber : questionNumbers) {
            if (hasQuestionResult(examId, questionNumber)) {
                completeQuestion(examId, questionNumber, 0);
                continue;
            }

            String jobId = GradingKeys.questionJobId(examId, questionNumber, 0);
            Optional<QuestionGradingJob> existing = questionJobRepository.findById(jobId);
            if (existing.isEmpty()) {
                recoverMissingJob(examId, questionNumber, retried, waiting, missing);
                continue;
            }

            handleRetryCandidate(existing.get(), retried, waiting);
        }

        boolean questionWorkRemains = !retried.isEmpty() || !waiting.isEmpty() || !missing.isEmpty();
        SummaryAction summaryAction = !questionWorkRemains && allQuestionsComplete(examId, questionNumbers)
                ? retrySummaryIfEligible(examId)
                : SummaryAction.NOT_READY;
        ExamStatus overallStatus = calculateAndCacheOverallStatus(examId, questionNumbers);
        log.info(
                "시험 단위 채점 재시도 처리 완료 event=grading.exam.retry "
                        + "outcome=completed examId={} retriedCount={} "
                        + "waitingCount={} missingSubmissionCount={} summaryAction={} overallStatus={}",
                examId,
                retried.size(),
                waiting.size(),
                missing.size(),
                summaryAction,
                overallStatus
        );

        return ExamResponseDTO.GradingRetryResult.builder()
                .examId(examId)
                .overallStatus(overallStatus)
                .retriedQuestionNumbers(List.copyOf(retried))
                .waitingQuestionNumbers(List.copyOf(waiting))
                .missingSubmissionQuestionNumbers(List.copyOf(missing))
                .summaryAction(summaryAction)
                .build();
    }

    public void completeQuestion(String examId, Integer questionNumber, Integer retryCount) {
        if (!canProcessSession(examId)) {
            return;
        }
        int canonicalRetryCount = GradingKeys.canonicalRetryCount(retryCount);
        String mockExamId = resolveMockExamId(examId);
        String jobId = GradingKeys.questionJobId(examId, questionNumber, canonicalRetryCount);

        for (int attempt = 0; attempt < CALLBACK_COMPLETION_RETRIES; attempt++) {
            Instant now = clock.instant();
            Optional<QuestionGradingJob> existing = questionJobRepository.findById(jobId);
            if (existing.isEmpty()) {
                try {
                    QuestionGradingJob completed = QuestionGradingJob.completed(
                            jobId,
                            examId,
                            questionNumber,
                            canonicalRetryCount,
                            GradingKeys.questionFileKey(examId, questionNumber, canonicalRetryCount),
                            mockExamId,
                            now
                    );
                    questionJobRepository.insert(completed);
                    log.info(
                            "문항 채점 작업 완료 event=grading.question.job outcome=completed "
                                    + "jobId={} examId={} "
                                    + "questionNumber={} retryCount={} dispatchAttempt={} "
                                    + "fromStatus=MISSING toStatus=COMPLETED callbackLatencyMs={}",
                            jobId,
                            examId,
                            questionNumber,
                            canonicalRetryCount,
                            completed.getDispatchAttempt(),
                            -1L
                    );
                    return;
                } catch (DuplicateKeyException concurrentInsert) {
                    continue;
                }
            }

            QuestionGradingJob job = existing.get();
            if (job.getStatus() == GradingJobStatus.COMPLETED) {
                return;
            }
            GradingJobStatus fromStatus = job.getStatus();
            Instant processingStartedAt = job.getProcessingStartedAt();
            int dispatchAttempt = job.getDispatchAttempt();
            job.complete(now);
            try {
                questionJobRepository.save(job);
                log.info(
                        "문항 채점 작업 완료 event=grading.question.job outcome=completed "
                                + "jobId={} examId={} "
                                + "questionNumber={} retryCount={} dispatchAttempt={} "
                                + "fromStatus={} toStatus=COMPLETED callbackLatencyMs={}",
                        jobId,
                        examId,
                        questionNumber,
                        canonicalRetryCount,
                        dispatchAttempt,
                        fromStatus,
                        elapsedSince(processingStartedAt, now)
                );
                return;
            } catch (OptimisticLockingFailureException concurrentUpdate) {
                // Re-read and converge on COMPLETED without overwriting a newer document version.
            }
        }
        log.warn(
                "문항 채점 완료 전환 경합 감지 event=grading.question.job "
                        + "outcome=completion_race jobId={} examId={} "
                        + "questionNumber={} retryCount={}",
                jobId, examId, questionNumber, canonicalRetryCount
        );
    }

    public void completeSummary(String examId) {
        if (!canProcessSession(examId)) {
            return;
        }
        String jobId = GradingKeys.summaryJobId(examId);
        String mockExamId = resolveMockExamId(examId);

        for (int attempt = 0; attempt < CALLBACK_COMPLETION_RETRIES; attempt++) {
            Instant now = clock.instant();
            Optional<SummaryGradingJob> existing = summaryJobRepository.findById(jobId);
            if (existing.isEmpty()) {
                try {
                    SummaryGradingJob completed =
                            SummaryGradingJob.completed(jobId, examId, mockExamId, now);
                    summaryJobRepository.insert(completed);
                    log.info(
                            "요약 채점 작업 완료 event=grading.summary.job outcome=completed "
                                    + "jobId={} examId={} "
                                    + "dispatchAttempt={} fromStatus=MISSING toStatus=COMPLETED "
                                    + "callbackLatencyMs={}",
                            jobId,
                            examId,
                            completed.getDispatchAttempt(),
                            -1L
                    );
                    return;
                } catch (DuplicateKeyException concurrentInsert) {
                    continue;
                }
            }

            SummaryGradingJob job = existing.get();
            if (job.getStatus() == GradingJobStatus.COMPLETED) {
                return;
            }
            GradingJobStatus fromStatus = job.getStatus();
            Instant processingStartedAt = job.getProcessingStartedAt();
            int dispatchAttempt = job.getDispatchAttempt();
            job.complete(now);
            try {
                summaryJobRepository.save(job);
                log.info(
                        "요약 채점 작업 완료 event=grading.summary.job outcome=completed "
                                + "jobId={} examId={} "
                                + "dispatchAttempt={} fromStatus={} toStatus=COMPLETED "
                                + "callbackLatencyMs={}",
                        jobId,
                        examId,
                        dispatchAttempt,
                        fromStatus,
                        elapsedSince(processingStartedAt, now)
                );
                return;
            } catch (OptimisticLockingFailureException concurrentUpdate) {
                // Re-read and converge on COMPLETED without overwriting a newer document version.
            }
        }
        log.warn(
                "요약 채점 완료 전환 경합 감지 event=grading.summary.job "
                        + "outcome=completion_race jobId={} examId={}",
                jobId, examId
        );
    }

    public void ensureSummaryStartedIfReady(String examId) {
        if (!canProcessSession(examId)) {
            log.debug(
                    "처리할 수 없는 시험 세션의 요약 채점 시작 무시 "
                            + "event=grading.summary.trigger outcome=ignored "
                            + "reason=session_not_processable examId={}",
                    examId
            );
            return;
        }
        List<Integer> questionNumbers = expectedQuestionNumbers(examId);
        QuestionCompletionSnapshot completionSnapshot = loadQuestionCompletionSnapshot(examId);
        long completedCount = questionNumbers.stream()
                .filter(completionSnapshot::isComplete)
                .count();
        if (completedCount != questionNumbers.size()) {
            log.debug(
                    "요약 채점 시작 조건 미충족 event=grading.summary.trigger "
                            + "outcome=not_ready examId={} "
                            + "completedQuestionCount={} expectedQuestionCount={}",
                    examId, completedCount, questionNumbers.size()
            );
            calculateAndCacheOverallStatus(examId, questionNumbers);
            return;
        }

        if (hasSummaryResult(examId)) {
            completeSummary(examId);
            log.debug(
                    "요약 채점 결과 이미 완료 event=grading.summary.trigger "
                            + "outcome=already_completed examId={} jobId={}",
                    examId, GradingKeys.summaryJobId(examId)
            );
            calculateAndCacheOverallStatus(examId, questionNumbers);
            return;
        }

        String jobId = GradingKeys.summaryJobId(examId);
        Optional<SummaryGradingJob> existing = summaryJobRepository.findById(jobId);
        SummaryGradingJob summaryJob = null;
        boolean created = false;
        if (existing.isEmpty()) {
            SummaryGradingJob pending = SummaryGradingJob.pending(
                    jobId,
                    examId,
                    resolveMockExamId(examId),
                    clock.instant()
            );
            try {
                summaryJob = summaryJobRepository.insert(pending);
                created = true;
            } catch (DuplicateKeyException concurrentInsert) {
                summaryJob = summaryJobRepository.findById(jobId).orElse(null);
            }
        } else {
            summaryJob = existing.get();
        }

        if (summaryJob != null && summaryJob.getStatus() == GradingJobStatus.PENDING) {
            boolean scheduled = summaryDispatchScheduler.schedulePending(jobId);
            if (created && scheduled) {
                log.info(
                        "요약 채점 실행 예약 완료 event=grading.summary.trigger "
                                + "outcome=scheduled examId={} jobId={} "
                                + "completedQuestionCount={} expectedQuestionCount={}",
                        examId, jobId, completedCount, questionNumbers.size()
                );
            } else {
                log.debug(
                        "요약 채점 실행 예약 상태 확인 event=grading.summary.trigger "
                                + "outcome={} examId={} jobId={} "
                                + "jobStatus={} scheduleAccepted={}",
                        scheduled ? "already_scheduled" : "schedule_rejected",
                        examId,
                        jobId,
                        summaryJob.getStatus(),
                        scheduled
                );
            }
        } else if (summaryJob == null) {
            log.warn(
                    "요약 채점 작업 생성 경합 후 조회 실패 event=grading.summary.trigger "
                            + "outcome=skipped reason=job_missing_after_insert_race "
                            + "examId={} jobId={}",
                    examId, jobId
            );
        } else {
            log.debug(
                    "대기 상태가 아닌 요약 채점 작업 시작 생략 event=grading.summary.trigger "
                            + "outcome=skipped reason=job_not_pending "
                            + "examId={} jobId={} jobStatus={}",
                    examId, jobId, summaryJob.getStatus()
            );
        }

        calculateAndCacheOverallStatus(examId, questionNumbers);
    }

    public ExamStatus calculateAndCacheOverallStatus(String examId) {
        return calculateAndCacheOverallStatus(examId, expectedQuestionNumbers(examId));
    }

    public ExamStatus getQuestionStatus(String examId, Integer questionNumber, Integer retryCount) {
        int canonicalRetryCount = GradingKeys.canonicalRetryCount(retryCount);
        if (hasQuestionResult(examId, questionNumber, canonicalRetryCount)) {
            return ExamStatus.COMPLETED;
        }
        return questionJobRepository.findById(
                        GradingKeys.questionJobId(examId, questionNumber, canonicalRetryCount)
                )
                .map(job -> toExamStatus(job.getStatus()))
                .orElse(ExamStatus.PROCESSING);
    }

    private void recoverMissingJob(
            String examId,
            Integer questionNumber,
            List<Integer> retried,
            List<Integer> waiting,
            List<Integer> missing) {
        String fileKey = GradingKeys.questionFileKey(examId, questionNumber, 0);
        if (!s3ObjectExists(fileKey)) {
            missing.add(questionNumber);
            return;
        }

        QuestionGradingJob pending = QuestionGradingJob.pending(
                GradingKeys.questionJobId(examId, questionNumber, 0),
                examId,
                questionNumber,
                0,
                fileKey,
                resolveMockExamId(examId),
                clock.instant()
        );
        try {
            QuestionGradingJob inserted = questionJobRepository.insert(pending);
            DispatchOutcome outcome = claimAndDispatchQuestion(inserted, true);
            recordOutcome(questionNumber, outcome, retried, waiting);
        } catch (DuplicateKeyException concurrentInsert) {
            questionJobRepository.findById(pending.getJobId())
                    .filter(job -> job.getStatus() == GradingJobStatus.PENDING
                            || job.getStatus() == GradingJobStatus.PROCESSING)
                    .ifPresent(job -> waiting.add(questionNumber));
        }
    }

    private void handleRetryCandidate(
            QuestionGradingJob job,
            List<Integer> retried,
            List<Integer> waiting) {
        if (job.getStatus() == GradingJobStatus.COMPLETED) {
            return;
        }

        Instant now = clock.instant();
        boolean eligible = switch (job.getStatus()) {
            case FAILED -> true;
            case PENDING -> timedOut(job.getPendingAt(), properties.pendingTimeout(), now);
            case PROCESSING -> timedOut(job.getProcessingStartedAt(), properties.processingTimeout(), now);
            case COMPLETED -> false;
        };

        if (!eligible) {
            waiting.add(job.getQuestionNumber());
            return;
        }
        if (job.getDispatchAttempt() >= properties.maxDispatchAttempts()) {
            markAttemptLimitReached(job);
            return;
        }

        DispatchOutcome outcome = claimAndDispatchQuestion(job, false);
        recordOutcome(job.getQuestionNumber(), outcome, retried, waiting);
    }

    private DispatchOutcome claimAndDispatchQuestion(QuestionGradingJob job, boolean initialDispatch) {
        if (!canProcessSession(job.getExamId())) {
            return DispatchOutcome.FAILED;
        }
        if (!initialDispatch && job.getDispatchAttempt() >= properties.maxDispatchAttempts()) {
            markAttemptLimitReached(job);
            return DispatchOutcome.FAILED;
        }

        QuestionGradingJob claimed;
        try {
            job.startProcessing(clock.instant());
            claimed = questionJobRepository.save(job);
        } catch (OptimisticLockingFailureException lostClaim) {
            return DispatchOutcome.CLAIM_LOST;
        }

        QuestionDispatchClaim claim = QuestionDispatchClaim.from(claimed, resolveMockExamId(claimed));
        if (!canProcessSession(claim.examId())) {
            failQuestionClaim(claim, EXAM_ABANDONED);
            return DispatchOutcome.FAILED;
        }
        return dispatchQuestion(claim)
                ? DispatchOutcome.DISPATCHED
                : DispatchOutcome.FAILED;
    }

    private boolean dispatchQuestion(QuestionDispatchClaim claim) {
        long startedAt = System.nanoTime();
        try {
            dispatchService.dispatchQuestion(claim);
            log.info(
                    "문항 채점 요청 전송 완료 event=grading.question.dispatch "
                            + "outcome=success jobId={} examId={} "
                            + "questionNumber={} retryCount={} dispatchAttempt={} durationMs={}",
                    claim.jobId(),
                    claim.examId(),
                    claim.questionNumber(),
                    claim.retryCount(),
                    claim.dispatchAttempt(),
                    elapsedMillis(startedAt)
            );
            return true;
        } catch (RuntimeException dispatchFailure) {
            long durationMs = elapsedMillis(startedAt);
            if (failQuestionClaim(claim, QUESTION_DISPATCH_FAILED)) {
                log.error(
                        "문항 채점 요청 전송 실패 event=grading.question.dispatch "
                                + "outcome=failure reason={} jobId={} examId={} "
                                + "questionNumber={} retryCount={} dispatchAttempt={} durationMs={} "
                                + "stage={} stageDurationMs={} errorType={} rootCauseType={}",
                        QUESTION_DISPATCH_FAILED,
                        claim.jobId(),
                        claim.examId(),
                        claim.questionNumber(),
                        claim.retryCount(),
                        claim.dispatchAttempt(),
                        durationMs,
                        GradingDispatchException.stageCode(dispatchFailure),
                        GradingDispatchException.stageDurationMs(dispatchFailure),
                        dispatchFailure.getClass().getName(),
                        rootCauseType(dispatchFailure)
                );
            } else {
                log.debug(
                        "이전 문항 채점 전송 실패 무시 event=grading.question.dispatch "
                                + "outcome=stale_failure_ignored "
                                + "jobId={} dispatchAttempt={} durationMs={} errorType={}",
                        claim.jobId(),
                        claim.dispatchAttempt(),
                        durationMs,
                        dispatchFailure.getClass().getName()
                );
            }
            return false;
        }
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private static long elapsedSince(Instant startedAt, Instant completedAt) {
        if (startedAt == null || completedAt == null || completedAt.isBefore(startedAt)) {
            return -1L;
        }
        return Duration.between(startedAt, completedAt).toMillis();
    }

    private static String rootCauseType(Throwable failure) {
        Throwable rootCause = failure;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getClass().getName();
    }

    private boolean failQuestionClaim(QuestionDispatchClaim claim, String reason) {
        long updated = questionJobRepository.failClaimedAttempt(
                claim.jobId(),
                claim.dispatchAttempt(),
                clock.instant(),
                reason
        );
        return updated == 1;
    }

    private void markAttemptLimitReached(QuestionGradingJob job) {
        if (job.getStatus() == GradingJobStatus.FAILED
                && MAX_DISPATCH_ATTEMPTS.equals(job.getFailureReason())) {
            return;
        }
        GradingJobStatus fromStatus = job.getStatus();
        job.fail(clock.instant(), MAX_DISPATCH_ATTEMPTS);
        try {
            questionJobRepository.save(job);
            log.warn(
                    "문항 채점 최대 전송 시도 도달 event=grading.question.job "
                            + "outcome=max_attempts reason={} jobId={} examId={} "
                            + "questionNumber={} retryCount={} dispatchAttempt={} fromStatus={} toStatus=FAILED",
                    MAX_DISPATCH_ATTEMPTS,
                    job.getJobId(),
                    job.getExamId(),
                    job.getQuestionNumber(),
                    job.getRetryCount(),
                    job.getDispatchAttempt(),
                    fromStatus
            );
        } catch (OptimisticLockingFailureException concurrentUpdate) {
            // A concurrent retry or callback owns the newer state.
        }
    }

    private ExamSession requireInProgressSession(String examId) {
        ExamSession session = requireNotAbandonedSession(examId);
        if (session.isCompleted()) {
            throw new ExamsException(ErrorStatus._EXAM_ALREADY_COMPLETED);
        }
        return session;
    }

    private ExamSession requireNotAbandonedSession(String examId) {
        ExamSession session = examSessionRepository.findById(examId)
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_NOT_FOUND));
        if (session.isAbandoned()) {
            throw new ExamsException(ErrorStatus._EXAM_ABANDONED);
        }
        return session;
    }

    private boolean canProcessSession(String examId) {
        return examSessionRepository.findById(examId)
                .map(session -> !session.isAbandoned())
                .orElse(false);
    }

    private SummaryAction retrySummaryIfEligible(String examId) {
        if (hasSummaryResult(examId)) {
            completeSummary(examId);
            return SummaryAction.ALREADY_COMPLETED;
        }

        String jobId = GradingKeys.summaryJobId(examId);
        Optional<SummaryGradingJob> existing = summaryJobRepository.findById(jobId);
        if (existing.isEmpty()) {
            try {
                SummaryGradingJob inserted = summaryJobRepository.insert(
                        SummaryGradingJob.pending(
                                jobId,
                                examId,
                                resolveMockExamId(examId),
                                clock.instant()
                        )
                );
                return summaryDispatchScheduler.schedulePending(inserted.getJobId())
                        ? SummaryAction.RETRIED
                        : SummaryAction.WAITING;
            } catch (DuplicateKeyException concurrentInsert) {
                return SummaryAction.WAITING;
            }
        }

        SummaryGradingJob job = existing.get();
        if (job.getStatus() == GradingJobStatus.COMPLETED) {
            return SummaryAction.ALREADY_COMPLETED;
        }
        if (!isSummaryRetryEligible(job, clock.instant())) {
            return SummaryAction.WAITING;
        }
        return summaryDispatchScheduler.scheduleRetry(job.getJobId())
                ? SummaryAction.RETRIED
                : SummaryAction.WAITING;
    }

    private boolean isSummaryRetryEligible(SummaryGradingJob job, Instant now) {
        boolean eligible = switch (job.getStatus()) {
            case FAILED -> true;
            case PENDING -> timedOut(job.getPendingAt(), properties.pendingTimeout(), now);
            case PROCESSING -> timedOut(job.getProcessingStartedAt(), properties.processingTimeout(), now);
            case COMPLETED -> false;
        };
        if (!eligible) {
            return false;
        }
        if (job.getDispatchAttempt() >= properties.maxDispatchAttempts()) {
            markSummaryAttemptLimitReached(job);
            return false;
        }
        return true;
    }


    private void markSummaryAttemptLimitReached(SummaryGradingJob job) {
        if (job.getStatus() == GradingJobStatus.FAILED
                && MAX_DISPATCH_ATTEMPTS.equals(job.getFailureReason())) {
            return;
        }
        GradingJobStatus fromStatus = job.getStatus();
        job.fail(clock.instant(), MAX_DISPATCH_ATTEMPTS);
        try {
            summaryJobRepository.save(job);
            log.warn(
                    "요약 채점 최대 전송 시도 도달 event=grading.summary.job "
                            + "outcome=max_attempts reason={} jobId={} examId={} "
                            + "dispatchAttempt={} fromStatus={} toStatus=FAILED",
                    MAX_DISPATCH_ATTEMPTS,
                    job.getJobId(),
                    job.getExamId(),
                    job.getDispatchAttempt(),
                    fromStatus
            );
        } catch (OptimisticLockingFailureException concurrentUpdate) {
            // A concurrent retry or callback owns the newer state.
        }
    }

    private ExamStatus calculateAndCacheOverallStatus(String examId, List<Integer> questionNumbers) {
        boolean hasMissingQuestion = false;
        boolean hasActiveQuestion = false;
        boolean hasFailedQuestion = false;
        QuestionCompletionSnapshot snapshot = loadQuestionCompletionSnapshot(examId);

        for (Integer questionNumber : questionNumbers) {
            if (snapshot.hasResult(questionNumber)) {
                continue;
            }
            QuestionGradingJob job = snapshot.jobsByQuestionNumber().get(questionNumber);
            if (job == null) {
                hasMissingQuestion = true;
                continue;
            }
            switch (job.getStatus()) {
                case COMPLETED -> {
                }
                case FAILED -> hasFailedQuestion = true;
                case PENDING, PROCESSING -> hasActiveQuestion = true;
            }
        }

        ExamStatus status;
        if (hasFailedQuestion) {
            status = ExamStatus.FAILED;
        } else if (hasActiveQuestion) {
            status = ExamStatus.PROCESSING;
        } else if (hasMissingQuestion) {
            status = ExamStatus.PENDING;
        } else if (hasSummaryResult(examId)) {
            status = ExamStatus.COMPLETED;
        } else {
            status = summaryJobRepository.findById(GradingKeys.summaryJobId(examId))
                    .map(job -> switch (job.getStatus()) {
                        case COMPLETED -> ExamStatus.COMPLETED;
                        case FAILED -> ExamStatus.FAILED;
                        case PENDING, PROCESSING -> ExamStatus.PROCESSING;
                    })
                    .orElse(ExamStatus.PROCESSING);
        }

        redisTemplate.opsForValue().set(
                redisStatusKey(examId),
                status.name(),
                1,
                TimeUnit.HOURS
        );
        return status;
    }

    private boolean allQuestionsComplete(String examId, List<Integer> questionNumbers) {
        QuestionCompletionSnapshot snapshot = loadQuestionCompletionSnapshot(examId);
        return questionNumbers.stream().allMatch(snapshot::isComplete);
    }

    private boolean hasQuestionResult(String examId, Integer questionNumber) {
        return hasQuestionResult(examId, questionNumber, 0);
    }

    private boolean hasQuestionResult(String examId, Integer questionNumber, int retryCount) {
        return examResultRepository.existsByExamIdAndQuestionNumberAndRetryCountIn(
                examId,
                questionNumber,
                compatibleRetryCounts(retryCount)
        );
    }

    private boolean hasSummaryResult(String examId) {
        return examSummaryRepository.existsByExamId(examId)
                || examResultRepository.findFirstByExamIdAndTotalScoreIsNotNullOrderByIdDesc(examId).isPresent();
    }

    private QuestionCompletionSnapshot loadQuestionCompletionSnapshot(String examId) {
        List<ExamResult> results = Optional.ofNullable(examResultRepository.findByExamId(examId))
                .orElseGet(Collections::emptyList);
        Set<Integer> completedResultQuestions = results.stream()
                .filter(result -> result.getQuestionNumber() != null && result.getQuestionNumber() > 0)
                .filter(result -> result.getRetryCount() == null || result.getRetryCount() == 0)
                .map(ExamResult::getQuestionNumber)
                .collect(Collectors.toSet());

        List<QuestionGradingJob> jobs = Optional.ofNullable(
                        questionJobRepository.findByExamIdAndRetryCount(examId, 0)
                )
                .orElseGet(Collections::emptyList);
        Map<Integer, QuestionGradingJob> jobsByQuestionNumber = jobs.stream()
                .filter(job -> job.getQuestionNumber() != null)
                .collect(Collectors.toMap(
                        QuestionGradingJob::getQuestionNumber,
                        Function.identity(),
                        (first, ignored) -> first
                ));
        return new QuestionCompletionSnapshot(completedResultQuestions, jobsByQuestionNumber);
    }

    private List<Integer> expectedQuestionNumbers(String examId) {
        String mockExamId = resolveMockExamId(examId);
        MockExam mockExam = mockExamCatalogService.getRequiredExam(mockExamId);
        List<Integer> questionNumbers = Optional.ofNullable(mockExam.getQuestions())
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(question -> question != null)
                .map(question -> question.getQuestionNumber())
                .filter(questionNumber -> questionNumber != null && questionNumber > 0)
                .distinct()
                .sorted()
                .toList();
        if (questionNumbers.isEmpty()) {
            throw new ExamsException(ErrorStatus._QUESTION_NOT_FOUND);
        }
        return questionNumbers;
    }

    private String resolveMockExamId(String examId) {
        return examSessionRepository.findById(examId)
                .map(session -> GradingKeys.effectiveMockExamId(session.getMockExamId()))
                .orElse(GradingKeys.LEGACY_MOCK_EXAM_ID);
    }

    private String resolveMockExamId(QuestionGradingJob job) {
        if (job.getMockExamId() != null && !job.getMockExamId().isBlank()) {
            return job.getMockExamId();
        }
        return resolveMockExamId(job.getExamId());
    }

    private boolean s3ObjectExists(String fileKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw exception;
        }
    }

    private static boolean timedOut(Instant startedAt, Duration timeout, Instant now) {
        return startedAt == null || !startedAt.plus(timeout).isAfter(now);
    }

    private static List<Integer> compatibleRetryCounts(int retryCount) {
        return retryCount == 0 ? Arrays.asList(0, null) : List.of(retryCount);
    }

    private static ExamStatus toExamStatus(GradingJobStatus status) {
        return ExamStatus.valueOf(status.name());
    }

    private static String redisStatusKey(String examId) {
        return "exam:status:" + examId;
    }

    private static void recordOutcome(
            Integer questionNumber,
            DispatchOutcome outcome,
            List<Integer> retried,
            List<Integer> waiting) {
        if (outcome == DispatchOutcome.DISPATCHED) {
            retried.add(questionNumber);
        } else if (outcome == DispatchOutcome.CLAIM_LOST) {
            waiting.add(questionNumber);
        }
    }

    private enum DispatchOutcome {
        DISPATCHED,
        CLAIM_LOST,
        FAILED
    }

    private record QuestionCompletionSnapshot(
            Set<Integer> completedResultQuestions,
            Map<Integer, QuestionGradingJob> jobsByQuestionNumber) {

        boolean hasResult(Integer questionNumber) {
            return completedResultQuestions.contains(questionNumber);
        }

        boolean isComplete(Integer questionNumber) {
            if (hasResult(questionNumber)) {
                return true;
            }
            QuestionGradingJob job = jobsByQuestionNumber.get(questionNumber);
            return job != null && job.getStatus() == GradingJobStatus.COMPLETED;
        }
    }
}
