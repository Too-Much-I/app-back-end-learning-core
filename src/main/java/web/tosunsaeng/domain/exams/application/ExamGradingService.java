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
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.QuestionGradingJob;
import web.tosunsaeng.domain.exams.domain.entity.SummaryGradingJob;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;
import web.tosunsaeng.domain.exams.domain.enums.GradingJobStatus;
import web.tosunsaeng.domain.exams.domain.enums.SummaryAction;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
import web.tosunsaeng.domain.exams.domain.repository.MockExamRepository;
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
    private static final String SUMMARY_DISPATCH_FAILED = "SUMMARY_DISPATCH_FAILED";
    private static final String MAX_DISPATCH_ATTEMPTS = "MAX_DISPATCH_ATTEMPTS";

    private final QuestionGradingJobRepository questionJobRepository;
    private final SummaryGradingJobRepository summaryJobRepository;
    private final ExamResultRepository examResultRepository;
    private final ExamSummaryRepository examSummaryRepository;
    private final MockExamRepository mockExamRepository;
    private final S3Client s3Client;
    private final GradingDispatchService dispatchService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final GradingProperties properties;
    private final Clock clock;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    public ExamStatus submitQuestion(String examId, Integer questionNumber, Integer retryCount) {
        int canonicalRetryCount = GradingKeys.canonicalRetryCount(retryCount);
        String jobId = GradingKeys.questionJobId(examId, questionNumber, canonicalRetryCount);
        Instant now = clock.instant();
        QuestionGradingJob pending = QuestionGradingJob.pending(
                jobId,
                examId,
                questionNumber,
                canonicalRetryCount,
                GradingKeys.questionFileKey(examId, questionNumber, canonicalRetryCount),
                now
        );

        QuestionGradingJob inserted;
        try {
            inserted = questionJobRepository.insert(pending);
        } catch (DuplicateKeyException duplicate) {
            ExamStatus status = questionJobRepository.findById(jobId)
                    .map(job -> toExamStatus(job.getStatus()))
                    .orElse(ExamStatus.PENDING);
            calculateAndCacheOverallStatus(examId);
            return status;
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

        try {
            dispatchService.dispatchQuestion(claimed);
        } catch (RuntimeException dispatchFailure) {
            failQuestionJob(claimed.getJobId(), QUESTION_DISPATCH_FAILED);
            calculateAndCacheOverallStatus(examId);
            throw new ExamsException(ErrorStatus._AI_SERVER_CONNECTION_ERROR);
        }

        calculateAndCacheOverallStatus(examId);
        return ExamStatus.PROCESSING;
    }

    public ExamResponseDTO.GradingRetryResult retryExam(String examId) {
        List<Integer> questionNumbers = expectedQuestionNumbers();
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
                ? retrySummary(examId)
                : SummaryAction.NOT_READY;
        ExamStatus overallStatus = calculateAndCacheOverallStatus(examId, questionNumbers);

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
        int canonicalRetryCount = GradingKeys.canonicalRetryCount(retryCount);
        String jobId = GradingKeys.questionJobId(examId, questionNumber, canonicalRetryCount);

        for (int attempt = 0; attempt < CALLBACK_COMPLETION_RETRIES; attempt++) {
            Instant now = clock.instant();
            Optional<QuestionGradingJob> existing = questionJobRepository.findById(jobId);
            if (existing.isEmpty()) {
                try {
                    questionJobRepository.insert(QuestionGradingJob.completed(
                            jobId,
                            examId,
                            questionNumber,
                            canonicalRetryCount,
                            GradingKeys.questionFileKey(examId, questionNumber, canonicalRetryCount),
                            now
                    ));
                    return;
                } catch (DuplicateKeyException concurrentInsert) {
                    continue;
                }
            }

            QuestionGradingJob job = existing.get();
            if (job.getStatus() == GradingJobStatus.COMPLETED) {
                return;
            }
            job.complete(now);
            try {
                questionJobRepository.save(job);
                return;
            } catch (OptimisticLockingFailureException concurrentUpdate) {
                // Re-read and converge on COMPLETED without overwriting a newer document version.
            }
        }
        log.warn("Question Job completion raced repeatedly: jobId={}", jobId);
    }

    public void completeSummary(String examId) {
        String jobId = GradingKeys.summaryJobId(examId);

        for (int attempt = 0; attempt < CALLBACK_COMPLETION_RETRIES; attempt++) {
            Instant now = clock.instant();
            Optional<SummaryGradingJob> existing = summaryJobRepository.findById(jobId);
            if (existing.isEmpty()) {
                try {
                    summaryJobRepository.insert(SummaryGradingJob.completed(jobId, examId, now));
                    return;
                } catch (DuplicateKeyException concurrentInsert) {
                    continue;
                }
            }

            SummaryGradingJob job = existing.get();
            if (job.getStatus() == GradingJobStatus.COMPLETED) {
                return;
            }
            job.complete(now);
            try {
                summaryJobRepository.save(job);
                return;
            } catch (OptimisticLockingFailureException concurrentUpdate) {
                // Re-read and converge on COMPLETED without overwriting a newer document version.
            }
        }
        log.warn("Summary Job completion raced repeatedly: jobId={}", jobId);
    }

    public void tryDispatchOverallSummary(String examId) {
        List<Integer> questionNumbers = expectedQuestionNumbers();
        if (!allQuestionsComplete(examId, questionNumbers)) {
            calculateAndCacheOverallStatus(examId, questionNumbers);
            return;
        }

        if (hasSummaryResult(examId)) {
            completeSummary(examId);
            calculateAndCacheOverallStatus(examId, questionNumbers);
            return;
        }

        String jobId = GradingKeys.summaryJobId(examId);
        Optional<SummaryGradingJob> existing = summaryJobRepository.findById(jobId);
        if (existing.isEmpty()) {
            SummaryGradingJob pending = SummaryGradingJob.pending(jobId, examId, clock.instant());
            try {
                SummaryGradingJob inserted = summaryJobRepository.insert(pending);
                claimAndDispatchSummary(inserted, true);
            } catch (DuplicateKeyException concurrentInsert) {
                // The concurrent creator owns the initial dispatch.
            }
        } else if (isSummaryRetryEligible(existing.get(), clock.instant())) {
            claimAndDispatchSummary(existing.get(), false);
        }

        calculateAndCacheOverallStatus(examId, questionNumbers);
    }

    public ExamStatus calculateAndCacheOverallStatus(String examId) {
        return calculateAndCacheOverallStatus(examId, expectedQuestionNumbers());
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

        try {
            dispatchService.dispatchQuestion(claimed);
            return DispatchOutcome.DISPATCHED;
        } catch (RuntimeException dispatchFailure) {
            failQuestionJob(claimed.getJobId(), QUESTION_DISPATCH_FAILED);
            return DispatchOutcome.FAILED;
        }
    }

    private void failQuestionJob(String jobId, String reason) {
        for (int attempt = 0; attempt < CALLBACK_COMPLETION_RETRIES; attempt++) {
            Optional<QuestionGradingJob> existing = questionJobRepository.findById(jobId);
            if (existing.isEmpty() || existing.get().getStatus() == GradingJobStatus.COMPLETED) {
                return;
            }
            QuestionGradingJob job = existing.get();
            job.fail(clock.instant(), reason);
            try {
                questionJobRepository.save(job);
                return;
            } catch (OptimisticLockingFailureException concurrentUpdate) {
                // Re-read before recording the synchronous dispatch failure.
            }
        }
    }

    private void markAttemptLimitReached(QuestionGradingJob job) {
        if (job.getStatus() == GradingJobStatus.FAILED
                && MAX_DISPATCH_ATTEMPTS.equals(job.getFailureReason())) {
            return;
        }
        job.fail(clock.instant(), MAX_DISPATCH_ATTEMPTS);
        try {
            questionJobRepository.save(job);
        } catch (OptimisticLockingFailureException concurrentUpdate) {
            // A concurrent retry or callback owns the newer state.
        }
    }

    private SummaryAction retrySummary(String examId) {
        if (hasSummaryResult(examId)) {
            completeSummary(examId);
            return SummaryAction.ALREADY_COMPLETED;
        }

        String jobId = GradingKeys.summaryJobId(examId);
        Optional<SummaryGradingJob> existing = summaryJobRepository.findById(jobId);
        if (existing.isEmpty()) {
            try {
                SummaryGradingJob inserted = summaryJobRepository.insert(
                        SummaryGradingJob.pending(jobId, examId, clock.instant())
                );
                return claimAndDispatchSummary(inserted, true)
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
        return claimAndDispatchSummary(job, false)
                ? SummaryAction.RETRIED
                : SummaryAction.WAITING;
    }

    private boolean claimAndDispatchSummary(SummaryGradingJob job, boolean initialDispatch) {
        if (!initialDispatch && job.getDispatchAttempt() >= properties.maxDispatchAttempts()) {
            markSummaryAttemptLimitReached(job);
            return false;
        }

        SummaryGradingJob claimed;
        try {
            job.startProcessing(clock.instant());
            claimed = summaryJobRepository.save(job);
        } catch (OptimisticLockingFailureException lostClaim) {
            return false;
        }

        try {
            dispatchService.dispatchSummary(claimed.getExamId());
            return true;
        } catch (RuntimeException dispatchFailure) {
            failSummaryJob(claimed.getJobId(), SUMMARY_DISPATCH_FAILED);
            return false;
        }
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

    private void failSummaryJob(String jobId, String reason) {
        for (int attempt = 0; attempt < CALLBACK_COMPLETION_RETRIES; attempt++) {
            Optional<SummaryGradingJob> existing = summaryJobRepository.findById(jobId);
            if (existing.isEmpty() || existing.get().getStatus() == GradingJobStatus.COMPLETED) {
                return;
            }
            SummaryGradingJob job = existing.get();
            job.fail(clock.instant(), reason);
            try {
                summaryJobRepository.save(job);
                return;
            } catch (OptimisticLockingFailureException concurrentUpdate) {
                // Re-read before recording the synchronous dispatch failure.
            }
        }
    }

    private void markSummaryAttemptLimitReached(SummaryGradingJob job) {
        if (job.getStatus() == GradingJobStatus.FAILED
                && MAX_DISPATCH_ATTEMPTS.equals(job.getFailureReason())) {
            return;
        }
        job.fail(clock.instant(), MAX_DISPATCH_ATTEMPTS);
        try {
            summaryJobRepository.save(job);
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

    private List<Integer> expectedQuestionNumbers() {
        MockExam mockExam = mockExamRepository.findByMockExamId(GradingKeys.MOCK_EXAM_ID)
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_PAPER_NOT_FOUND));
        List<Integer> questionNumbers = mockExam.getQuestions().stream()
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
