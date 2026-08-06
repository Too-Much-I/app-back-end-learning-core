package web.tosunsaeng.domain.exams.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.domain.entity.QuestionGradingJob;
import web.tosunsaeng.domain.exams.domain.entity.SummaryGradingJob;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
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
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
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
    private ExamSessionRepository examSessionRepository;

    @Mock
    private MockExamCatalogService mockExamCatalogService;

    @Mock
    private S3Client s3Client;

    @Mock
    private GradingDispatchService dispatchService;

    @Mock
    private SummaryDispatchScheduler summaryDispatchScheduler;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private final Map<String, QuestionGradingJob> questionJobs = new ConcurrentHashMap<>();
    private final Map<String, SummaryGradingJob> summaryJobs = new ConcurrentHashMap<>();
    private List<Integer> expectedQuestionNumbers;
    private MutableClock clock;
    private ExamGradingService service;

    @BeforeEach
    void setUp() {
        expectedQuestionNumbers = List.of(1);
        clock = new MutableClock(NOW);
        installQuestionRepositoryStore();
        installSummaryRepositoryStore();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(examSessionRepository.findById(EXAM_ID))
                .thenReturn(Optional.of(web.tosunsaeng.domain.exams.domain.entity.ExamSession.builder()
                        .examId(EXAM_ID)
                        .mockExamId(GradingKeys.LEGACY_MOCK_EXAM_ID)
                        .build()));
        lenient().when(mockExamCatalogService.getRequiredExam(GradingKeys.LEGACY_MOCK_EXAM_ID))
                .thenAnswer(invocation -> mockExam(expectedQuestionNumbers));
        lenient().when(summaryDispatchScheduler.schedulePending(anyString())).thenReturn(true);
        lenient().when(summaryDispatchScheduler.scheduleRetry(anyString())).thenReturn(true);

        service = new ExamGradingService(
                questionJobRepository,
                summaryJobRepository,
                examResultRepository,
                examSummaryRepository,
                examSessionRepository,
                mockExamCatalogService,
                s3Client,
                dispatchService,
                summaryDispatchScheduler,
                redisTemplate,
                new GradingProperties(
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(3),
                        3,
                        URI.create("http://test-ai:8000"),
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(30),
                        2,
                        100
                ),
                clock
        );
        ReflectionTestUtils.setField(service, "bucketName", "test-learning-core-bucket");
    }

    @Test
    void firstSubmitCreatesProcessingJobAndDispatchesOnce(CapturedOutput output) {
        ExamStatus status = service.submitQuestion(EXAM_ID, 1, 0);

        QuestionGradingJob stored = storedQuestion(1, 0);
        assertAll(
                () -> assertEquals(ExamStatus.PROCESSING, status),
                () -> assertEquals(GradingJobStatus.PROCESSING, stored.getStatus()),
                () -> assertEquals(1, stored.getDispatchAttempt()),
                () -> assertEquals(NOW, stored.getProcessingStartedAt()),
                () -> assertEquals(NOW, stored.getLastDispatchedAt()),
                () -> assertEquals(0, stored.getRetryCount()),
                () -> assertEquals("temp/" + EXAM_ID + "/q_1_r0.wav", stored.getFileKey()),
                () -> assertTrue(output.getOut().contains(
                        "채점 submit 시작: jobId=question:" + EXAM_ID + ":1:0")),
                () -> assertTrue(output.getOut().contains(
                        "신규 채점 Job 생성: jobId=question:" + EXAM_ID + ":1:0")),
                () -> assertTrue(output.getOut().contains(
                        "AI 전송 호출 직전: jobId=question:" + EXAM_ID + ":1:0")),
                () -> assertTrue(output.getOut().contains(
                        "AI 전송 성공: jobId=question:" + EXAM_ID + ":1:0"))
        );
        verify(dispatchService).dispatchQuestion(any(QuestionDispatchClaim.class));
    }

    @Test
    void submitStoresAndDispatchesSessionMockExamId() {
        stubSelectedPaper("mock_exam_002", List.of(1));

        service.submitQuestion(EXAM_ID, 1, 0);

        ArgumentCaptor<QuestionDispatchClaim> claimCaptor =
                ArgumentCaptor.forClass(QuestionDispatchClaim.class);
        verify(dispatchService).dispatchQuestion(claimCaptor.capture());
        assertAll(
                () -> assertEquals("mock_exam_002", storedQuestion(1, 0).getMockExamId()),
                () -> assertEquals("mock_exam_002", claimCaptor.getValue().mockExamId()),
                () -> assertEquals(EXAM_ID, claimCaptor.getValue().examId())
        );
    }

    @Test
    void repeatedSubmitDoesNotDispatchAgain(CapturedOutput output) {
        service.submitQuestion(EXAM_ID, 1, 0);
        ExamStatus repeatedStatus = service.submitQuestion(EXAM_ID, 1, 0);

        assertAll(
                () -> assertEquals(ExamStatus.PROCESSING, repeatedStatus),
                () -> assertTrue(output.getOut().contains(
                        "기존 채점 Job 반환: jobId=question:" + EXAM_ID
                                + ":1:0, status=PROCESSING, dispatchAttempt=1"))
        );
        verify(dispatchService, times(1)).dispatchQuestion(any(QuestionDispatchClaim.class));
    }

    @Test
    void dispatchFailureLogRedactsPresignedUrl(CapturedOutput output) {
        doThrow(new RuntimeException(
                "GET https://example.com/audio.wav?X-Amz-Signature=should-not-be-logged failed"
        )).when(dispatchService).dispatchQuestion(any(QuestionDispatchClaim.class));

        assertThrows(ExamsException.class, () -> service.submitQuestion(EXAM_ID, 1, 0));

        assertAll(
                () -> assertTrue(output.getOut().contains(
                        "AI 전송 실패: jobId=question:" + EXAM_ID + ":1:0")),
                () -> assertTrue(output.getOut().contains("type=java.lang.RuntimeException")),
                () -> assertTrue(output.getOut().contains("message=GET [redacted-uri]")),
                () -> assertFalse(output.getOut().contains("X-Amz-Signature")),
                () -> assertFalse(output.getOut().contains("should-not-be-logged"))
        );
    }

    @Test
    void concurrentSubmitDispatchesOnlyOnce() throws Exception {
        runConcurrently(
                () -> service.submitQuestion(EXAM_ID, 1, 0),
                () -> service.submitQuestion(EXAM_ID, 1, 0)
        );

        verify(dispatchService, times(1)).dispatchQuestion(any(QuestionDispatchClaim.class));
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
        verify(dispatchService).dispatchQuestion(any(QuestionDispatchClaim.class));
    }

    @Test
    void abandonedExamRetryIsRejectedWithoutDispatch() {
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(ExamSession.builder()
                .examId(EXAM_ID)
                .active(false)
                .status(ExamSessionStatus.ABANDONED)
                .build()));
        putQuestion(questionJob(1, 0, GradingJobStatus.FAILED, 1, NOW.minusSeconds(600)));

        ExamsException exception = assertThrows(ExamsException.class, () -> service.retryExam(EXAM_ID));

        assertSame(ErrorStatus._EXAM_ABANDONED, exception.getCode());
        verify(dispatchService, never()).dispatchQuestion(any());
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void completedExamRetryIsRejectedWithoutDispatch() {
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(ExamSession.builder()
                .examId(EXAM_ID)
                .active(false)
                .status(ExamSessionStatus.COMPLETED)
                .build()));
        putQuestion(questionJob(1, 0, GradingJobStatus.FAILED, 1, NOW.minusSeconds(600)));

        ExamsException exception = assertThrows(ExamsException.class, () -> service.retryExam(EXAM_ID));

        assertSame(ErrorStatus._EXAM_ALREADY_COMPLETED, exception.getCode());
        verify(dispatchService, never()).dispatchQuestion(any());
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void abandonedExamSubmitDoesNotCreateOrDispatchJob() {
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(ExamSession.builder()
                .examId(EXAM_ID)
                .active(false)
                .status(ExamSessionStatus.ABANDONED)
                .build()));

        ExamsException exception = assertThrows(
                ExamsException.class,
                () -> service.submitQuestion(EXAM_ID, 1, 0)
        );

        assertSame(ErrorStatus._EXAM_ABANDONED, exception.getCode());
        assertTrue(questionJobs.isEmpty());
        verify(dispatchService, never()).dispatchQuestion(any());
    }

    @Test
    void completedExamStillAllowsUserRerecordSubmitWithNewRetryCount() {
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(ExamSession.builder()
                .examId(EXAM_ID)
                .mockExamId(GradingKeys.LEGACY_MOCK_EXAM_ID)
                .active(false)
                .status(ExamSessionStatus.COMPLETED)
                .build()));

        ExamStatus status = service.submitQuestion(EXAM_ID, 1, 1);

        assertAll(
                () -> assertEquals(ExamStatus.PROCESSING, status),
                () -> assertEquals(1, storedQuestion(1, 1).getRetryCount()),
                () -> assertEquals("question:" + EXAM_ID + ":1:1", storedQuestion(1, 1).getJobId())
        );
        verify(dispatchService).dispatchQuestion(any(QuestionDispatchClaim.class));
    }

    @Test
    void retryUsesSelectedPaperQuestionsAndSessionFallbackForLegacyJob() {
        stubSelectedPaper("mock_exam_002", List.of(7));
        putQuestion(questionJob(7, 0, GradingJobStatus.FAILED, 1, NOW.minusSeconds(600)));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        ArgumentCaptor<QuestionDispatchClaim> claimCaptor =
                ArgumentCaptor.forClass(QuestionDispatchClaim.class);
        verify(dispatchService).dispatchQuestion(claimCaptor.capture());
        assertAll(
                () -> assertEquals(List.of(7), result.getRetriedQuestionNumbers()),
                () -> assertEquals("mock_exam_002", claimCaptor.getValue().mockExamId()),
                () -> assertNull(storedQuestion(7, 0).getMockExamId())
        );
        verify(mockExamCatalogService).getRequiredExam("mock_exam_002");
        verify(mockExamCatalogService, never()).getRequiredExam(GradingKeys.LEGACY_MOCK_EXAM_ID);
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
        verify(dispatchService).dispatchQuestion(any(QuestionDispatchClaim.class));
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

        verify(dispatchService, times(1)).dispatchQuestion(any(QuestionDispatchClaim.class));
        assertEquals(2, storedQuestion(1, 0).getDispatchAttempt());
    }

    @Test
    void examRetryNeverDispatchesSummaryInTheSameScanThatRedispatchedAQuestion() {
        putQuestion(questionJob(1, 0, GradingJobStatus.PROCESSING, 1, NOW.minus(Duration.ofMinutes(4))));
        doAnswer(invocation -> {
            service.completeQuestion(EXAM_ID, 1, 0);
            return null;
        }).when(dispatchService).dispatchQuestion(any(QuestionDispatchClaim.class));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertEquals(SummaryAction.NOT_READY, result.getSummaryAction());
        verify(summaryDispatchScheduler, never()).schedulePending(anyString());
        verify(summaryDispatchScheduler, never()).scheduleRetry(anyString());
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

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = 0)
    void submitBackfillsCompletedJobFromCanonicalLegacyResultWithoutDispatch(Integer storedRetryCount) {
        ExamResult legacyResult = ExamResult.builder()
                .examId(EXAM_ID)
                .questionNumber(1)
                .retryCount(storedRetryCount)
                .build();
        lenient().when(examResultRepository.existsByExamIdAndQuestionNumberAndRetryCountIn(
                        eq(EXAM_ID), eq(1), any()))
                .thenReturn(true);
        lenient().when(examResultRepository.findByExamId(EXAM_ID)).thenReturn(List.of(legacyResult));

        ExamStatus result = service.submitQuestion(EXAM_ID, 1, 0);

        QuestionGradingJob stored = storedQuestion(1, 0);
        assertAll(
                () -> assertEquals(ExamStatus.COMPLETED, result),
                () -> assertEquals(GradingJobStatus.COMPLETED, stored.getStatus()),
                () -> assertEquals(0, stored.getDispatchAttempt()),
                () -> assertEquals(NOW, stored.getCompletedAt())
        );
        verify(dispatchService, never()).dispatchQuestion(any());
    }

    @Test
    void submitRepairsExistingNonCompletedJobWhenCanonicalResultAlreadyExists() {
        ExamResult existingResult = ExamResult.builder()
                .examId(EXAM_ID)
                .questionNumber(1)
                .retryCount(0)
                .build();
        putQuestion(questionJob(1, 0, GradingJobStatus.FAILED, 1, NOW.minusSeconds(30)));
        lenient().when(examResultRepository.existsByExamIdAndQuestionNumberAndRetryCountIn(
                        eq(EXAM_ID), eq(1), any()))
                .thenReturn(true);
        lenient().when(examResultRepository.findByExamId(EXAM_ID)).thenReturn(List.of(existingResult));

        ExamStatus result = service.submitQuestion(EXAM_ID, 1, 0);

        QuestionGradingJob stored = storedQuestion(1, 0);
        assertAll(
                () -> assertEquals(ExamStatus.COMPLETED, result),
                () -> assertEquals(GradingJobStatus.COMPLETED, stored.getStatus()),
                () -> assertEquals(1, stored.getDispatchAttempt()),
                () -> assertNull(stored.getFailedAt()),
                () -> assertNull(stored.getFailureReason())
        );
        verify(dispatchService, never()).dispatchQuestion(any());
    }

    @Test
    void staleQuestionAttemptFailureDoesNotOverwriteNewerProcessingAttempt() {
        CountDownLatch attemptOneStarted = new CountDownLatch(1);
        CountDownLatch releaseAttemptOne = new CountDownLatch(1);
        doAnswer(invocation -> {
            QuestionDispatchClaim claim = invocation.getArgument(0);
            if (claim.dispatchAttempt() == 1) {
                attemptOneStarted.countDown();
                if (!releaseAttemptOne.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("attempt 1 was not released");
                }
                throw new RuntimeException("attempt 1 failed after attempt 2 was claimed");
            }
            return null;
        }).when(dispatchService).dispatchQuestion(any(QuestionDispatchClaim.class));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ExamStatus> attemptOne = executor.submit(() -> service.submitQuestion(EXAM_ID, 1, 0));
            assertTrue(attemptOneStarted.await(5, TimeUnit.SECONDS));
            Instant retryAt = NOW.plus(Duration.ofMinutes(3));
            clock.set(retryAt);

            ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);
            releaseAttemptOne.countDown();
            ExecutionException dispatchFailure = assertThrows(
                    ExecutionException.class,
                    () -> attemptOne.get(5, TimeUnit.SECONDS)
            );

            QuestionGradingJob stored = storedQuestion(1, 0);
            assertAll(
                    () -> assertTrue(dispatchFailure.getCause() instanceof web.tosunsaeng.domain.exams.exception.ExamsException),
                    () -> assertEquals(List.of(1), result.getRetriedQuestionNumbers()),
                    () -> assertEquals(GradingJobStatus.PROCESSING, stored.getStatus()),
                    () -> assertEquals(2, stored.getDispatchAttempt()),
                    () -> assertNull(stored.getFailedAt()),
                    () -> assertNull(stored.getFailureReason())
            );
            verify(questionJobRepository).failClaimedAttempt(
                    GradingKeys.questionJobId(EXAM_ID, 1, 0),
                    1,
                    retryAt,
                    "QUESTION_DISPATCH_FAILED"
            );
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        } finally {
            releaseAttemptOne.countDown();
            executor.shutdownNow();
        }
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

        service.ensureSummaryStartedIfReady(EXAM_ID);
        service.ensureSummaryStartedIfReady(EXAM_ID);

        verify(summaryDispatchScheduler, never()).schedulePending(anyString());
        assertTrue(summaryJobs.isEmpty());
    }

    @Test
    void allRequiredQuestionsCreateOnePendingSummaryJobAndOnlyScheduleIt() {
        expectedQuestionNumbers = List.of(1, 2);
        putCompletedQuestions(expectedQuestionNumbers);

        service.ensureSummaryStartedIfReady(EXAM_ID);
        service.ensureSummaryStartedIfReady(EXAM_ID);

        verify(summaryJobRepository, times(1)).insert(any(SummaryGradingJob.class));
        verify(summaryDispatchScheduler, times(2)).schedulePending(GradingKeys.summaryJobId(EXAM_ID));
        verify(dispatchService, never()).dispatchSummary(any());
        SummaryGradingJob stored = storedSummary();
        assertAll(
                () -> assertEquals(GradingJobStatus.PENDING, stored.getStatus()),
                () -> assertEquals(0, stored.getDispatchAttempt()),
                () -> assertEquals("summary:" + EXAM_ID + ":v1", stored.getJobId())
        );
    }

    @Test
    void summaryJobStoresSelectedSessionMockExamId() {
        stubSelectedPaper("mock_exam_002", List.of(1));
        putCompletedQuestions(List.of(1));

        service.ensureSummaryStartedIfReady(EXAM_ID);

        assertEquals("mock_exam_002", storedSummary().getMockExamId());
    }

    @ParameterizedTest
    @EnumSource(value = GradingJobStatus.class, names = {"FAILED", "PROCESSING"})
    void feedbackGateDoesNotRecoverFailedOrStaleProcessingSummary(GradingJobStatus status) {
        putCompletedQuestions(expectedQuestionNumbers);
        putSummary(summaryJob(status, 1, NOW.minus(Duration.ofMinutes(4))));

        service.ensureSummaryStartedIfReady(EXAM_ID);
        service.ensureSummaryStartedIfReady(EXAM_ID);

        verify(summaryDispatchScheduler, never()).schedulePending(anyString());
        verify(summaryDispatchScheduler, never()).scheduleRetry(anyString());
        verify(dispatchService, never()).dispatchSummary(any());
    }

    @Test
    void retryWaitsForFreshSummaryProcessingJob() {
        putCompletedQuestions(expectedQuestionNumbers);
        putSummary(summaryJob(GradingJobStatus.PROCESSING, 1, NOW.minus(Duration.ofMinutes(2))));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertEquals(SummaryAction.WAITING, result.getSummaryAction());
        verify(summaryDispatchScheduler, never()).scheduleRetry(anyString());
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
        verify(summaryDispatchScheduler, never()).scheduleRetry(anyString());
    }

    @ParameterizedTest
    @EnumSource(value = GradingJobStatus.class, names = {"PROCESSING", "FAILED"})
    void retryRedispatchesTimedOutOrFailedSummary(GradingJobStatus status) {
        putCompletedQuestions(expectedQuestionNumbers);
        putSummary(summaryJob(status, 1, NOW.minus(Duration.ofMinutes(3))));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertEquals(SummaryAction.RETRIED, result.getSummaryAction());
        assertEquals(1, storedSummary().getDispatchAttempt());
        verify(summaryDispatchScheduler).scheduleRetry(GradingKeys.summaryJobId(EXAM_ID));
        verify(dispatchService, never()).dispatchSummary(any());
    }

    @Test
    void retryDoesNotDispatchCompletedSummaryAgain() {
        putCompletedQuestions(expectedQuestionNumbers);
        putSummary(summaryJob(GradingJobStatus.COMPLETED, 1, NOW.minusSeconds(10)));

        ExamResponseDTO.GradingRetryResult result = service.retryExam(EXAM_ID);

        assertEquals(SummaryAction.ALREADY_COMPLETED, result.getSummaryAction());
        assertEquals(ExamStatus.COMPLETED, result.getOverallStatus());
        verify(summaryDispatchScheduler, never()).scheduleRetry(anyString());
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
        lenient().when(questionJobRepository.failClaimedAttempt(
                        anyString(), anyInt(), any(Instant.class), anyString()))
                .thenAnswer(invocation -> {
                    synchronized (questionJobs) {
                        String jobId = invocation.getArgument(0);
                        int claimedAttempt = invocation.getArgument(1);
                        Instant failedAt = invocation.getArgument(2);
                        String reason = invocation.getArgument(3);
                        QuestionGradingJob current = questionJobs.get(jobId);
                        if (current == null
                                || current.getStatus() != GradingJobStatus.PROCESSING
                                || current.getDispatchAttempt() != claimedAttempt) {
                            return 0L;
                        }
                        QuestionGradingJob failed = copy(current, current.getVersion());
                        failed.fail(failedAt, reason);
                        questionJobs.put(jobId, copy(failed, current.getVersion() + 1));
                        return 1L;
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
                .mockExamId(source.getMockExamId())
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
                .mockExamId(source.getMockExamId())
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
                .mockExamId(GradingKeys.LEGACY_MOCK_EXAM_ID)
                .questions(questions)
                .build();
    }

    private void stubSelectedPaper(String mockExamId, List<Integer> questionNumbers) {
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(ExamSession.builder()
                .examId(EXAM_ID)
                .mockExamId(mockExamId)
                .active(true)
                .build()));
        MockExam selected = mockExam(questionNumbers);
        when(mockExamCatalogService.getRequiredExam(mockExamId)).thenReturn(selected);
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

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;

        private MutableClock(Instant initialInstant) {
            instant = new AtomicReference<>(initialInstant);
        }

        void set(Instant newInstant) {
            instant.set(newInstant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
