package web.tosunsaeng.domain.exams.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.Question;
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
import web.tosunsaeng.global.config.GradingProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExamGradingServiceTest {

    private static final String EXAM_ID = "ex_grading_001";
    private static final Instant NOW = Instant.parse("2026-07-28T06:00:00Z");

    @Mock
    private QuestionGradingJobRepository questionJobRepository;

    @Mock
    private SummaryGradingJobRepository summaryJobRepository;

    @Mock
    private ExamResultRepository examResultRepository;

    @Mock
    private ExamSummaryRepository examSummaryRepository;

    @Mock
    private MockExamRepository mockExamRepository;

    @Mock
    private S3Client s3Client;

    @Mock
    private GradingDispatchService dispatchService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private final Map<String, QuestionGradingJob> questionJobs = new ConcurrentHashMap<>();
    private final Map<String, SummaryGradingJob> summaryJobs = new ConcurrentHashMap<>();
    private List<Integer> expectedQuestionNumbers;
    private ExamGradingService service;

    @BeforeEach
    void setUp() {
        expectedQuestionNumbers = List.of(1);
        installQuestionRepositoryStore();
        installSummaryRepositoryStore();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(mockExamRepository.findByMockExamId(GradingKeys.MOCK_EXAM_ID))
                .thenAnswer(invocation -> Optional.of(mockExam(expectedQuestionNumbers)));

        service = new ExamGradingService(
                questionJobRepository,
                summaryJobRepository,
                examResultRepository,
                examSummaryRepository,
                mockExamRepository,
                s3Client,
                dispatchService,
                redisTemplate,
                new GradingProperties(Duration.ofMinutes(1), Duration.ofMinutes(3), 3),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(service, "bucketName", "test-learning-core-bucket");
    }

    @Test
    void firstSubmitCreatesProcessingJobAndDispatchesOnce() {
        ExamStatus status = service.submitQuestion(EXAM_ID, 1, 0);

        QuestionGradingJob stored = storedQuestion(1, 0);
        assertAll(
                () -> assertEquals(ExamStatus.PROCESSING, status),
                () -> assertEquals(GradingJobStatus.PROCESSING, stored.getStatus()),
                () -> assertEquals(1, stored.getDispatchAttempt()),
                () -> assertEquals(NOW, stored.getProcessingStartedAt()),
                () -> assertEquals(NOW, stored.getLastDispatchedAt()),
                () -> assertEquals("temp/" + EXAM_ID + "/q_1_r0.wav", stored.getFileKey())
        );
        verify(dispatchService).dispatchQuestion(any(QuestionGradingJob.class));
    }

    @Test
    void repeatedSubmitDoesNotDispatchAgain() {
        service.submitQuestion(EXAM_ID, 1, 0);
        ExamStatus repeatedStatus = service.submitQuestion(EXAM_ID, 1, 0);

        assertEquals(ExamStatus.PROCESSING, repeatedStatus);
        verify(dispatchService, times(1)).dispatchQuestion(any(QuestionGradingJob.class));
    }

    @Test
    void concurrentSubmitDispatchesOnlyOnce() throws Exception {
        runConcurrently(
                () -> service.submitQuestion(EXAM_ID, 1, 0),
                () -> service.submitQuestion(EXAM_ID, 1, 0)
        );

        verify(dispatchService, times(1)).dispatchQuestion(any(QuestionGradingJob.class));
        assertEquals(1, storedQuestion(1, 0).getDispatchAttempt());
    }

    @ParameterizedTest
    @EnumSource(value = GradingJobStatus.class, names = {"PENDING", "PROCESSING", "COMPLETED"})
    void existingNonFailedSubmitJobNeverDispatches(GradingJobStatus status) {
        putQuestion(questionJob(1, 0, status, 1, NOW.minusSeconds(600)));

        ExamStatus result = service.submitQuestion(EXAM_ID, 1, 0);

        assertEquals(ExamStatus.valueOf(status.name()), result);
        verify(dispatchService, never()).dispatchQuestion(any());
    }

    @Test
    void retryDispatchesFailedQuestionOnly() {
        putQuestion(questionJob(1, 0, GradingJobStatus.FAILED, 1, NOW.minusSeconds(600)));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertEquals(List.of(1), result.getRetriedQuestionNumbers());
        assertEquals(SummaryAction.NOT_READY, result.getSummaryAction());
        verify(dispatchService).dispatchQuestion(any(QuestionGradingJob.class));
    }

    @Test
    void retryWaitsForProcessingQuestionBeforeTimeout() {
        putQuestion(questionJob(1, 0, GradingJobStatus.PROCESSING, 1, NOW.minus(Duration.ofMinutes(2))));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertEquals(List.of(1), result.getWaitingQuestionNumbers());
        assertTrue(result.getRetriedQuestionNumbers().isEmpty());
        verify(dispatchService, never()).dispatchQuestion(any());
    }

    @Test
    void retryDispatchesProcessingQuestionAtTimeout() {
        putQuestion(questionJob(1, 0, GradingJobStatus.PROCESSING, 1, NOW.minus(Duration.ofMinutes(3))));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertEquals(List.of(1), result.getRetriedQuestionNumbers());
        assertEquals(2, storedQuestion(1, 0).getDispatchAttempt());
    }

    @Test
    void retryWaitsForPendingQuestionBeforeTimeout() {
        putQuestion(questionJob(1, 0, GradingJobStatus.PENDING, 0, NOW.minusSeconds(59)));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertEquals(List.of(1), result.getWaitingQuestionNumbers());
        verify(dispatchService, never()).dispatchQuestion(any());
    }

    @Test
    void retryDispatchesPendingQuestionAtTimeout() {
        putQuestion(questionJob(1, 0, GradingJobStatus.PENDING, 0, NOW.minus(Duration.ofMinutes(1))));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertEquals(List.of(1), result.getRetriedQuestionNumbers());
        assertEquals(1, storedQuestion(1, 0).getDispatchAttempt());
    }

    @Test
    void retryStopsAtMaximumDispatchAttempts() {
        putQuestion(questionJob(1, 0, GradingJobStatus.FAILED, 3, NOW.minusSeconds(600)));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertAll(
                () -> assertTrue(result.getRetriedQuestionNumbers().isEmpty()),
                () -> assertEquals(ExamStatus.FAILED, result.getOverallStatus()),
                () -> assertEquals("MAX_DISPATCH_ATTEMPTS", storedQuestion(1, 0).getFailureReason())
        );
        verify(dispatchService, never()).dispatchQuestion(any());
    }

    @Test
    void freshProcessingAtAttemptLimitStillWaitsForCurrentAttempt() {
        putQuestion(questionJob(1, 0, GradingJobStatus.PROCESSING, 3, NOW.minus(Duration.ofMinutes(2))));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertAll(
                () -> assertEquals(List.of(1), result.getWaitingQuestionNumbers()),
                () -> assertEquals(GradingJobStatus.PROCESSING, storedQuestion(1, 0).getStatus())
        );
        verify(dispatchService, never()).dispatchQuestion(any());
    }

    @Test
    void missingJobWithS3ObjectIsRecoveredAndDispatched() {
        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertAll(
                () -> assertEquals(List.of(1), result.getRetriedQuestionNumbers()),
                () -> assertTrue(result.getMissingSubmissionQuestionNumbers().isEmpty()),
                () -> assertEquals("temp/" + EXAM_ID + "/q_1_r0.wav", storedQuestion(1, 0).getFileKey())
        );
        ArgumentCaptor<HeadObjectRequest> headRequest = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(headRequest.capture());
        assertAll(
                () -> assertEquals("test-learning-core-bucket", headRequest.getValue().bucket()),
                () -> assertEquals("temp/" + EXAM_ID + "/q_1_r0.wav", headRequest.getValue().key())
        );
        verify(dispatchService).dispatchQuestion(any(QuestionGradingJob.class));
    }

    @Test
    void missingJobWithoutS3ObjectIsClassifiedAsMissingSubmission() {
        doThrow(S3Exception.builder().statusCode(404).message("not found").build())
                .when(s3Client).headObject(any(HeadObjectRequest.class));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertAll(
                () -> assertEquals(List.of(1), result.getMissingSubmissionQuestionNumbers()),
                () -> assertTrue(result.getRetriedQuestionNumbers().isEmpty()),
                () -> assertEquals(ExamStatus.PENDING, result.getOverallStatus())
        );
        verify(dispatchService, never()).dispatchQuestion(any());
    }

    @Test
    void s3AuthorizationFailureIsNotMisclassifiedAsMissingSubmission() {
        doThrow(S3Exception.builder().statusCode(403).message("forbidden").build())
                .when(s3Client).headObject(any(HeadObjectRequest.class));

        assertThrows(S3Exception.class, () -> service.retryExam(EXAM_ID));

        verify(dispatchService, never()).dispatchQuestion(any());
    }

    @Test
    void examRetryDoesNotIncludeUserRerecordJob() {
        putQuestion(questionJob(1, 1, GradingJobStatus.FAILED, 1, NOW.minusSeconds(600)));
        doThrow(S3Exception.builder().statusCode(404).message("not found").build())
                .when(s3Client).headObject(any(HeadObjectRequest.class));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertEquals(List.of(1), result.getMissingSubmissionQuestionNumbers());
        assertEquals(GradingJobStatus.FAILED, storedQuestion(1, 1).getStatus());
        verify(dispatchService, never()).dispatchQuestion(any());
    }

    @Test
    void concurrentExamRetryDispatchesQuestionOnce() throws Exception {
        putQuestion(questionJob(1, 0, GradingJobStatus.PROCESSING, 1, NOW.minus(Duration.ofMinutes(4))));

        runConcurrently(
                () -> service.retryExam(EXAM_ID),
                () -> service.retryExam(EXAM_ID)
        );

        verify(dispatchService, times(1)).dispatchQuestion(any(QuestionGradingJob.class));
        assertEquals(2, storedQuestion(1, 0).getDispatchAttempt());
    }

    @Test
    void examRetryNeverDispatchesSummaryInTheSameScanThatRedispatchedAQuestion() {
        putQuestion(questionJob(1, 0, GradingJobStatus.PROCESSING, 1, NOW.minus(Duration.ofMinutes(4))));
        doAnswer(invocation -> {
            service.completeQuestion(EXAM_ID, 1, 0);
            return null;
        }).when(dispatchService).dispatchQuestion(any(QuestionGradingJob.class));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertEquals(SummaryAction.NOT_READY, result.getSummaryAction());
        verify(dispatchService, never()).dispatchSummary(anyString());
    }

    @Test
    void callbackCompletionRecoversMissingLegacyJob() {
        service.completeQuestion(EXAM_ID, 1, 0);

        QuestionGradingJob stored = storedQuestion(1, 0);
        assertAll(
                () -> assertEquals(GradingJobStatus.COMPLETED, stored.getStatus()),
                () -> assertEquals("question:" + EXAM_ID + ":1:0", stored.getJobId()),
                () -> assertEquals(NOW, stored.getCompletedAt())
        );
    }

    @Test
    void retryRecognizesLegacyNullRetryResultAndBackfillsCompletedJob() {
        ExamResult legacyResult = ExamResult.builder()
                .examId(EXAM_ID)
                .questionNumber(1)
                .retryCount(null)
                .build();
        lenient().when(examResultRepository.existsByExamIdAndQuestionNumberAndRetryCountIn(
                eq(EXAM_ID), eq(1), any()))
                .thenReturn(true);
        lenient().when(examResultRepository.findByExamId(EXAM_ID)).thenReturn(List.of(legacyResult));
        lenient().when(examSummaryRepository.existsByExamId(EXAM_ID)).thenReturn(true);

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertAll(
                () -> assertEquals(GradingJobStatus.COMPLETED, storedQuestion(1, 0).getStatus()),
                () -> assertEquals(SummaryAction.ALREADY_COMPLETED, result.getSummaryAction()),
                () -> assertEquals(ExamStatus.COMPLETED, result.getOverallStatus())
        );
        verify(dispatchService, never()).dispatchQuestion(any());
    }

    @Test
    void summaryCallbackCompletionRecoversMissingLegacyJob() {
        service.completeSummary(EXAM_ID);

        SummaryGradingJob stored = storedSummary();
        assertAll(
                () -> assertEquals(GradingJobStatus.COMPLETED, stored.getStatus()),
                () -> assertEquals(1, stored.getSummaryVersion()),
                () -> assertEquals(NOW, stored.getCompletedAt())
        );
    }

    @Test
    void completingOnlyQuestionElevenDoesNotTriggerSummary() {
        expectedQuestionNumbers = List.of(1, 11);
        putQuestion(questionJob(11, 0, GradingJobStatus.COMPLETED, 1, NOW.minusSeconds(10)));

        service.tryDispatchOverallSummary(EXAM_ID);

        verify(dispatchService, never()).dispatchSummary(anyString());
        assertTrue(summaryJobs.isEmpty());
    }

    @Test
    void allRequiredQuestionsTriggerSummaryOnlyOnce() {
        expectedQuestionNumbers = List.of(1, 2);
        putCompletedQuestions(expectedQuestionNumbers);

        service.tryDispatchOverallSummary(EXAM_ID);
        service.tryDispatchOverallSummary(EXAM_ID);

        verify(dispatchService, times(1)).dispatchSummary(EXAM_ID);
        SummaryGradingJob stored = storedSummary();
        assertAll(
                () -> assertEquals(GradingJobStatus.PROCESSING, stored.getStatus()),
                () -> assertEquals(1, stored.getDispatchAttempt()),
                () -> assertEquals("summary:" + EXAM_ID + ":v1", stored.getJobId())
        );
    }

    @Test
    void retryWaitsForFreshSummaryProcessingJob() {
        putCompletedQuestions(expectedQuestionNumbers);
        putSummary(summaryJob(GradingJobStatus.PROCESSING, 1, NOW.minus(Duration.ofMinutes(2))));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertEquals(SummaryAction.WAITING, result.getSummaryAction());
        verify(dispatchService, never()).dispatchSummary(anyString());
    }

    @Test
    void freshSummaryAtAttemptLimitStillWaitsForCurrentAttempt() {
        putCompletedQuestions(expectedQuestionNumbers);
        putSummary(summaryJob(GradingJobStatus.PROCESSING, 3, NOW.minus(Duration.ofMinutes(2))));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertAll(
                () -> assertEquals(SummaryAction.WAITING, result.getSummaryAction()),
                () -> assertEquals(GradingJobStatus.PROCESSING, storedSummary().getStatus())
        );
        verify(dispatchService, never()).dispatchSummary(anyString());
    }

    @ParameterizedTest
    @EnumSource(value = GradingJobStatus.class, names = {"PROCESSING", "FAILED"})
    void retryRedispatchesTimedOutOrFailedSummary(GradingJobStatus status) {
        putCompletedQuestions(expectedQuestionNumbers);
        putSummary(summaryJob(status, 1, NOW.minus(Duration.ofMinutes(3))));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertEquals(SummaryAction.RETRIED, result.getSummaryAction());
        assertEquals(2, storedSummary().getDispatchAttempt());
        verify(dispatchService).dispatchSummary(EXAM_ID);
    }

    @Test
    void retryDoesNotDispatchCompletedSummaryAgain() {
        putCompletedQuestions(expectedQuestionNumbers);
        putSummary(summaryJob(GradingJobStatus.COMPLETED, 1, NOW.minusSeconds(10)));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertEquals(SummaryAction.ALREADY_COMPLETED, result.getSummaryAction());
        assertEquals(ExamStatus.COMPLETED, result.getOverallStatus());
        verify(dispatchService, never()).dispatchSummary(anyString());
    }

    @Test
    void overallStatusIsDerivedFromQuestionAndSummaryJobsAndCachedWithExistingKey() {
        assertEquals(ExamStatus.PENDING, service.calculateAndCacheOverallStatus(EXAM_ID));

        putQuestion(questionJob(1, 0, GradingJobStatus.PROCESSING, 1, NOW));
        assertEquals(ExamStatus.PROCESSING, service.calculateAndCacheOverallStatus(EXAM_ID));

        putQuestion(questionJob(1, 0, GradingJobStatus.FAILED, 1, NOW));
        assertEquals(ExamStatus.FAILED, service.calculateAndCacheOverallStatus(EXAM_ID));

        putQuestion(questionJob(1, 0, GradingJobStatus.COMPLETED, 1, NOW));
        assertEquals(ExamStatus.PROCESSING, service.calculateAndCacheOverallStatus(EXAM_ID));

        putSummary(summaryJob(GradingJobStatus.COMPLETED, 1, NOW));
        assertEquals(ExamStatus.COMPLETED, service.calculateAndCacheOverallStatus(EXAM_ID));

        verify(valueOperations, times(5)).set(
                eq("exam:status:" + EXAM_ID),
                anyString(),
                eq(1L),
                eq(TimeUnit.HOURS)
        );
    }

    private void installQuestionRepositoryStore() {
        lenient().when(questionJobRepository.findById(anyString())).thenAnswer(invocation -> {
            synchronized (questionJobs) {
                QuestionGradingJob stored = questionJobs.get(invocation.getArgument(0));
                return Optional.ofNullable(stored == null ? null : copy(stored, stored.getVersion()));
            }
        });
        lenient().when(questionJobRepository.findByExamIdAndRetryCount(anyString(), eq(0)))
                .thenAnswer(invocation -> {
                    synchronized (questionJobs) {
                        return questionJobs.values().stream()
                                .filter(job -> Objects.equals(invocation.getArgument(0), job.getExamId()))
                                .filter(job -> Objects.equals(0, job.getRetryCount()))
                                .map(job -> copy(job, job.getVersion()))
                                .toList();
                    }
                });
        lenient().when(questionJobRepository.insert(any(QuestionGradingJob.class))).thenAnswer(invocation -> {
            synchronized (questionJobs) {
                QuestionGradingJob candidate = invocation.getArgument(0);
                if (questionJobs.containsKey(candidate.getJobId())) {
                    throw new DuplicateKeyException("duplicate question job");
                }
                QuestionGradingJob stored = copy(candidate, 0L);
                questionJobs.put(stored.getJobId(), stored);
                return copy(stored, stored.getVersion());
            }
        });
        lenient().when(questionJobRepository.save(any(QuestionGradingJob.class))).thenAnswer(invocation -> {
            synchronized (questionJobs) {
                QuestionGradingJob candidate = invocation.getArgument(0);
                QuestionGradingJob current = questionJobs.get(candidate.getJobId());
                if (current == null || !Objects.equals(current.getVersion(), candidate.getVersion())) {
                    throw new OptimisticLockingFailureException("question job version conflict");
                }
                QuestionGradingJob stored = copy(candidate, current.getVersion() + 1);
                questionJobs.put(stored.getJobId(), stored);
                return copy(stored, stored.getVersion());
            }
        });
    }

    private void installSummaryRepositoryStore() {
        lenient().when(summaryJobRepository.findById(anyString())).thenAnswer(invocation -> {
            synchronized (summaryJobs) {
                SummaryGradingJob stored = summaryJobs.get(invocation.getArgument(0));
                return Optional.ofNullable(stored == null ? null : copy(stored, stored.getVersion()));
            }
        });
        lenient().when(summaryJobRepository.insert(any(SummaryGradingJob.class))).thenAnswer(invocation -> {
            synchronized (summaryJobs) {
                SummaryGradingJob candidate = invocation.getArgument(0);
                if (summaryJobs.containsKey(candidate.getJobId())) {
                    throw new DuplicateKeyException("duplicate summary job");
                }
                SummaryGradingJob stored = copy(candidate, 0L);
                summaryJobs.put(stored.getJobId(), stored);
                return copy(stored, stored.getVersion());
            }
        });
        lenient().when(summaryJobRepository.save(any(SummaryGradingJob.class))).thenAnswer(invocation -> {
            synchronized (summaryJobs) {
                SummaryGradingJob candidate = invocation.getArgument(0);
                SummaryGradingJob current = summaryJobs.get(candidate.getJobId());
                if (current == null || !Objects.equals(current.getVersion(), candidate.getVersion())) {
                    throw new OptimisticLockingFailureException("summary job version conflict");
                }
                SummaryGradingJob stored = copy(candidate, current.getVersion() + 1);
                summaryJobs.put(stored.getJobId(), stored);
                return copy(stored, stored.getVersion());
            }
        });
    }

    private void putCompletedQuestions(List<Integer> questionNumbers) {
        questionNumbers.forEach(questionNumber ->
                putQuestion(questionJob(questionNumber, 0, GradingJobStatus.COMPLETED, 1, NOW.minusSeconds(10))));
    }

    private void putQuestion(QuestionGradingJob job) {
        questionJobs.put(job.getJobId(), copy(job, 0L));
    }

    private void putSummary(SummaryGradingJob job) {
        summaryJobs.put(job.getJobId(), copy(job, 0L));
    }

    private QuestionGradingJob storedQuestion(int questionNumber, int retryCount) {
        return questionJobs.get(GradingKeys.questionJobId(EXAM_ID, questionNumber, retryCount));
    }

    private SummaryGradingJob storedSummary() {
        return summaryJobs.get(GradingKeys.summaryJobId(EXAM_ID));
    }

    private QuestionGradingJob questionJob(
            int questionNumber,
            int retryCount,
            GradingJobStatus status,
            int dispatchAttempt,
            Instant statusAt) {
        return QuestionGradingJob.builder()
                .jobId(GradingKeys.questionJobId(EXAM_ID, questionNumber, retryCount))
                .examId(EXAM_ID)
                .questionNumber(questionNumber)
                .retryCount(retryCount)
                .fileKey(GradingKeys.questionFileKey(EXAM_ID, questionNumber, retryCount))
                .status(status)
                .dispatchAttempt(dispatchAttempt)
                .pendingAt(statusAt)
                .processingStartedAt(status == GradingJobStatus.PROCESSING ? statusAt : null)
                .lastDispatchedAt(dispatchAttempt > 0 ? statusAt : null)
                .completedAt(status == GradingJobStatus.COMPLETED ? statusAt : null)
                .failedAt(status == GradingJobStatus.FAILED ? statusAt : null)
                .failureReason(status == GradingJobStatus.FAILED ? "TEST_FAILURE" : null)
                .build();
    }

    private SummaryGradingJob summaryJob(GradingJobStatus status, int dispatchAttempt, Instant statusAt) {
        return SummaryGradingJob.builder()
                .jobId(GradingKeys.summaryJobId(EXAM_ID))
                .examId(EXAM_ID)
                .summaryVersion(1)
                .status(status)
                .dispatchAttempt(dispatchAttempt)
                .pendingAt(statusAt)
                .processingStartedAt(status == GradingJobStatus.PROCESSING ? statusAt : null)
                .lastDispatchedAt(dispatchAttempt > 0 ? statusAt : null)
                .completedAt(status == GradingJobStatus.COMPLETED ? statusAt : null)
                .failedAt(status == GradingJobStatus.FAILED ? statusAt : null)
                .failureReason(status == GradingJobStatus.FAILED ? "TEST_FAILURE" : null)
                .build();
    }

    private static QuestionGradingJob copy(QuestionGradingJob source, Long version) {
        return QuestionGradingJob.builder()
                .jobId(source.getJobId())
                .examId(source.getExamId())
                .questionNumber(source.getQuestionNumber())
                .retryCount(source.getRetryCount())
                .fileKey(source.getFileKey())
                .status(source.getStatus())
                .dispatchAttempt(source.getDispatchAttempt())
                .pendingAt(source.getPendingAt())
                .processingStartedAt(source.getProcessingStartedAt())
                .lastDispatchedAt(source.getLastDispatchedAt())
                .completedAt(source.getCompletedAt())
                .failedAt(source.getFailedAt())
                .failureReason(source.getFailureReason())
                .version(version)
                .build();
    }

    private static SummaryGradingJob copy(SummaryGradingJob source, Long version) {
        return SummaryGradingJob.builder()
                .jobId(source.getJobId())
                .examId(source.getExamId())
                .summaryVersion(source.getSummaryVersion())
                .status(source.getStatus())
                .dispatchAttempt(source.getDispatchAttempt())
                .pendingAt(source.getPendingAt())
                .processingStartedAt(source.getProcessingStartedAt())
                .lastDispatchedAt(source.getLastDispatchedAt())
                .completedAt(source.getCompletedAt())
                .failedAt(source.getFailedAt())
                .failureReason(source.getFailureReason())
                .version(version)
                .build();
    }

    private static MockExam mockExam(List<Integer> questionNumbers) {
        List<Question> questions = questionNumbers.stream()
                .map(questionNumber -> Question.builder()
                        .questionNumber(questionNumber)
                        .partNumber(GradingDispatchService.partNumber(questionNumber))
                        .build())
                .toList();
        return MockExam.builder()
                .mockExamId(GradingKeys.MOCK_EXAM_ID)
                .questions(questions)
                .build();
    }

    private static <T> List<T> runConcurrently(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<T>> futures = new ArrayList<>();
            futures.add(executor.submit(first));
            futures.add(executor.submit(second));
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(5, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
