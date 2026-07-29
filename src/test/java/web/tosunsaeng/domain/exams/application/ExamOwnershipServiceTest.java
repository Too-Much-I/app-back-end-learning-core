package web.tosunsaeng.domain.exams.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import web.tosunsaeng.domain.exams.domain.entity.AzureResult;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.domain.entity.SpeechAceResult;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;
import web.tosunsaeng.domain.exams.domain.repository.AzureResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
import web.tosunsaeng.domain.exams.domain.repository.MockExamRepository;
import web.tosunsaeng.domain.exams.domain.repository.SpeechAceResultRepository;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.auth.CurrentUserProvider;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamOwnershipServiceTest {

    private static final String EXAM_ID = "ex_ownership_001";
    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String OTHER_USER_ID = "00000000-0000-0000-0000-000000000002";
    private static final String AI_SERVER_URL = "http://ai-server:8000/evaluations";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedGetObjectRequest presignedGetObjectRequest;

    @Mock
    private PresignedPutObjectRequest presignedPutObjectRequest;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ExamResultRepository examResultRepository;

    @Mock
    private ExamSummaryRepository examSummaryRepository;

    @Mock
    private ExamSessionRepository examSessionRepository;

    @Mock
    private MockExamRepository mockExamRepository;

    @Mock
    private SpeechAceResultRepository speechAceResultRepository;

    @Mock
    private AzureResultRepository azureResultRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private ExamGradingService gradingService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ExamServiceImpl examService;

    @BeforeEach
    void setUp() {
        examService = new ExamServiceImpl(
                redisTemplate,
                s3Presigner,
                gradingService,
                examResultRepository,
                examSummaryRepository,
                examSessionRepository,
                mockExamRepository,
                speechAceResultRepository,
                azureResultRepository,
                currentUserProvider
        );
        ReflectionTestUtils.setField(examService, "bucketName", "test-learning-core-bucket");
    }

    @Test
    void ownerCanIssueUploadUrlAfterOwnershipCheck() throws Exception {
        stubOwnedSession();
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedPutObjectRequest);
        when(presignedPutObjectRequest.url())
                .thenReturn(URI.create("https://example.com/upload").toURL());

        ExamResponseDTO.UploadUrlResult result = examService.getPresignedUrl(EXAM_ID, 3, 2);

        assertAll(
                () -> assertEquals("https://example.com/upload", result.getUploadUrl()),
                () -> assertEquals("temp/" + EXAM_ID + "/q_3_r2.wav", result.getFileKey()),
                () -> assertEquals(60, result.getExpiresIn())
        );
        InOrder order = inOrder(examSessionRepository, currentUserProvider, s3Presigner);
        order.verify(examSessionRepository).findById(EXAM_ID);
        order.verify(currentUserProvider).getCurrentUserId();
        order.verify(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void ownerCanSubmitAudioAndAiUserIdRemainsExamId() throws Exception {
        stubOwnedSession();
        when(gradingService.submitQuestion(EXAM_ID, 4, 2)).thenReturn(ExamStatus.PROCESSING);

        ExamResponseDTO.SubmitResult result = examService.submitAudio(EXAM_ID, 4, 2);

        assertEquals(ExamStatus.PROCESSING, result.getStatus());
        verify(gradingService).submitQuestion(EXAM_ID, 4, 2);
    }

    @Test
    void ownerCanReadExamStatusAfterOwnershipCheck() {
        stubOwnedSession();
        when(gradingService.calculateAndCacheOverallStatus(EXAM_ID)).thenReturn(ExamStatus.PROCESSING);

        ExamResponseDTO.StatusResult result = examService.getExamStatus(EXAM_ID);

        assertAll(
                () -> assertEquals(EXAM_ID, result.getExamId()),
                () -> assertEquals(ExamStatus.PROCESSING, result.getOverallStatus()),
                () -> assertEquals(60, result.getProgressPercent())
        );
        verify(gradingService).calculateAndCacheOverallStatus(EXAM_ID);
    }

    @Test
    void ownerCanReadExamSummaryAfterOwnershipCheck() {
        stubOwnedSession();
        ExamSummary summary = ExamSummary.builder()
                .examId(EXAM_ID)
                .userId(OWNER_USER_ID)
                .totalScore(180)
                .levelEstimate("Advanced")
                .build();
        ExamResult questionResult = ExamResult.builder()
                .examId(EXAM_ID)
                .userId(OWNER_USER_ID)
                .partNumber(1)
                .questionNumber(1)
                .retryCount(0)
                .score(5.0)
                .build();
        when(examResultRepository.findByExamId(EXAM_ID))
                .thenReturn(List.of(questionResult));
        when(examSummaryRepository.findFirstByExamIdOrderByIdDesc(EXAM_ID))
                .thenReturn(Optional.of(summary));

        ExamResponseDTO.SummaryResult result = examService.getExamSummary(EXAM_ID);

        assertAll(
                () -> assertEquals(EXAM_ID, result.getExamId()),
                () -> assertEquals(180, result.getTotalScore()),
                () -> assertEquals(1, result.getTotalSolvedQuestions()),
                () -> assertEquals(5.0, result.getPartScores().get("part1"))
        );
        verify(examResultRepository).findByExamId(EXAM_ID);
        verify(examSummaryRepository).findFirstByExamIdOrderByIdDesc(EXAM_ID);
    }

    @Test
    void latestOverallFeedbackFromSeparateCollectionIsUsed() {
        stubOwnedSession();
        ExamSummary latestSummary = ExamSummary.builder()
                .id("000000000000000000000002")
                .examId(EXAM_ID)
                .userId(OWNER_USER_ID)
                .totalScore(190)
                .summary("latest overall summary")
                .build();
        when(examResultRepository.findByExamId(EXAM_ID)).thenReturn(List.of());
        when(examSummaryRepository.findFirstByExamIdOrderByIdDesc(EXAM_ID))
                .thenReturn(Optional.of(latestSummary));

        ExamResponseDTO.SummaryResult result = examService.getExamSummary(EXAM_ID);

        assertAll(
                () -> assertEquals(190, result.getTotalScore()),
                () -> assertEquals("latest overall summary", result.getSummary()),
                () -> assertEquals(0, result.getTotalSolvedQuestions())
        );
        verify(examSummaryRepository).findFirstByExamIdOrderByIdDesc(EXAM_ID);
    }

    @Test
    void latestLegacyOverallFeedbackIsUsedWhenSeparateCollectionIsEmpty() {
        stubOwnedSession();
        ExamResult latestLegacySummary = ExamResult.builder()
                .id("000000000000000000000002")
                .examId(EXAM_ID)
                .userId(OWNER_USER_ID)
                .totalScore(175)
                .summary("latest legacy overall summary")
                .build();
        when(examResultRepository.findByExamId(EXAM_ID)).thenReturn(List.of());
        when(examSummaryRepository.findFirstByExamIdOrderByIdDesc(EXAM_ID))
                .thenReturn(Optional.empty());
        when(examResultRepository.findFirstByExamIdAndTotalScoreIsNotNullOrderByIdDesc(EXAM_ID))
                .thenReturn(Optional.of(latestLegacySummary));

        ExamResponseDTO.SummaryResult result = examService.getExamSummary(EXAM_ID);

        assertAll(
                () -> assertEquals(175, result.getTotalScore()),
                () -> assertEquals("latest legacy overall summary", result.getSummary())
        );
        verify(examSummaryRepository).findFirstByExamIdOrderByIdDesc(EXAM_ID);
        verify(examResultRepository)
                .findFirstByExamIdAndTotalScoreIsNotNullOrderByIdDesc(EXAM_ID);
    }

    @Test
    void ownerCanReadQuestionResultAfterOwnershipCheck() throws Exception {
        stubOwnedSession();
        ExamResult questionResult = ExamResult.builder()
                .examId(EXAM_ID)
                .userId(OWNER_USER_ID)
                .partNumber(1)
                .questionNumber(1)
                .retryCount(0)
                .score(5.0)
                .build();
        when(examResultRepository.findByExamId(EXAM_ID)).thenReturn(List.of(questionResult));
        when(examResultRepository.findFirstByExamIdAndQuestionNumberAndRetryCountInOrderByIdDesc(
                EXAM_ID,
                1,
                Arrays.asList(0, null)
        )).thenReturn(Optional.of(questionResult));
        when(azureResultRepository.findFirstByExamIdAndQuestionNumberAndRetryCountOrderByIdDesc(EXAM_ID, 1, 0))
                .thenReturn(Optional.empty());
        when(mockExamRepository.findByMockExamId("mock_exam_003"))
                .thenReturn(Optional.of(mockExam()));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGetObjectRequest);
        when(presignedGetObjectRequest.url())
                .thenReturn(URI.create("https://example.com/submitted-audio.wav").toURL());

        ExamResponseDTO.QuestionResult result = examService.getExamQuestion(EXAM_ID, 1, 0);

        assertAll(
                () -> assertEquals(EXAM_ID, result.getExamId()),
                () -> assertEquals(1, result.getQuestion().getQuestionNumber()),
                () -> assertEquals(0, result.getQuestion().getRetryCount()),
                () -> assertEquals(5.0, result.getQuestion().getScore())
        );
        verify(examResultRepository).findByExamId(EXAM_ID);
        verify(examResultRepository).findFirstByExamIdAndQuestionNumberAndRetryCountInOrderByIdDesc(
                EXAM_ID,
                1,
                Arrays.asList(0, null)
        );
        verify(azureResultRepository)
                .findFirstByExamIdAndQuestionNumberAndRetryCountOrderByIdDesc(EXAM_ID, 1, 0);
        verify(mockExamRepository).findByMockExamId("mock_exam_003");
        verify(s3Presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void retryZeroQuestionReadReturnsLegacyAzureWithNullRetryCount() throws Exception {
        stubEmptyQuestionRead(0);
        AzureResult legacyNull = legacyAzure("legacy-null", null, 7);
        when(azureResultRepository.findFirstLegacyNullRetryCount(EXAM_ID, 1))
                .thenReturn(Optional.of(legacyNull));

        ExamResponseDTO.QuestionResult result = examService.getExamQuestion(EXAM_ID, 1, 0);

        assertAll(
                () -> assertNotNull(result.getQuestion().getAzureFeedback()),
                () -> assertEquals(
                        7,
                        result.getQuestion().getAzureFeedback().getErrorCounts().getOmission()
                )
        );
        verify(azureResultRepository, never()).findFirstLegacyMissingRetryCount(EXAM_ID, 1);
    }

    @Test
    void retryZeroQuestionReadFallsBackToLegacyAzureWithMissingRetryCount() throws Exception {
        stubEmptyQuestionRead(0);
        AzureResult legacyMissing = legacyAzure("legacy-missing", null, 8);
        when(azureResultRepository.findFirstLegacyNullRetryCount(EXAM_ID, 1))
                .thenReturn(Optional.empty());
        when(azureResultRepository.findFirstLegacyMissingRetryCount(EXAM_ID, 1))
                .thenReturn(Optional.of(legacyMissing));

        ExamResponseDTO.QuestionResult result = examService.getExamQuestion(EXAM_ID, 1, 0);

        assertAll(
                () -> assertNotNull(result.getQuestion().getAzureFeedback()),
                () -> assertEquals(
                        8,
                        result.getQuestion().getAzureFeedback().getErrorCounts().getOmission()
                )
        );
        verify(azureResultRepository).findFirstLegacyMissingRetryCount(EXAM_ID, 1);
    }

    @Test
    void positiveRetryQuestionReadNeverReturnsLegacyZeroAzure() throws Exception {
        stubEmptyQuestionRead(1);

        ExamResponseDTO.QuestionResult result = examService.getExamQuestion(EXAM_ID, 1, 1);

        assertNull(result.getQuestion().getAzureFeedback());
        verify(azureResultRepository, never()).findFirstLegacyNullRetryCount(EXAM_ID, 1);
        verify(azureResultRepository, never()).findFirstLegacyMissingRetryCount(EXAM_ID, 1);
    }

    @Test
    void latestAiQuestionFeedbackIsUsedWhenTheSameRetryWasSavedMoreThanOnce() throws Exception {
        stubOwnedSession();
        ExamResult olderResult = ExamResult.builder()
                .id("000000000000000000000001")
                .examId(EXAM_ID)
                .userId(OWNER_USER_ID)
                .partNumber(1)
                .questionNumber(1)
                .retryCount(2)
                .score(3.0)
                .build();
        ExamResult latestResult = ExamResult.builder()
                .id("000000000000000000000002")
                .examId(EXAM_ID)
                .userId(OWNER_USER_ID)
                .partNumber(1)
                .questionNumber(1)
                .retryCount(2)
                .score(9.0)
                .build();
        when(examResultRepository.findByExamId(EXAM_ID))
                .thenReturn(List.of(olderResult, latestResult));
        when(examResultRepository.findFirstByExamIdAndQuestionNumberAndRetryCountInOrderByIdDesc(
                EXAM_ID,
                1,
                List.of(2)
        )).thenReturn(Optional.of(latestResult));
        when(azureResultRepository.findFirstByExamIdAndQuestionNumberAndRetryCountOrderByIdDesc(EXAM_ID, 1, 2))
                .thenReturn(Optional.empty());
        when(mockExamRepository.findByMockExamId("mock_exam_003"))
                .thenReturn(Optional.of(mockExam()));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGetObjectRequest);
        when(presignedGetObjectRequest.url())
                .thenReturn(URI.create("https://example.com/submitted-audio.wav").toURL());

        ExamResponseDTO.QuestionResult result = examService.getExamQuestion(EXAM_ID, 1, 2);

        assertAll(
                () -> assertEquals(9.0, result.getQuestion().getScore()),
                () -> assertEquals(2, result.getQuestion().getRetryCount()),
                () -> assertEquals(3, result.getQuestion().getTotalRetryCount())
        );
        verify(examResultRepository).findFirstByExamIdAndQuestionNumberAndRetryCountInOrderByIdDesc(
                EXAM_ID,
                1,
                List.of(2)
        );
    }

    @Test
    void ownerCanPollQuestionStatusAfterOwnershipCheck() {
        stubOwnedSession();
        when(gradingService.getQuestionStatus(EXAM_ID, 1, 0)).thenReturn(ExamStatus.COMPLETED);

        ExamResponseDTO.QuestionPollResult result =
                examService.getQuestionProcessingStatus(EXAM_ID, 1, 0);

        assertAll(
                () -> assertEquals(EXAM_ID, result.getExamId()),
                () -> assertEquals(1, result.getQuestionNumber()),
                () -> assertEquals(0, result.getRetryCount()),
                () -> assertEquals(ExamStatus.COMPLETED, result.getStatus())
        );
        verify(gradingService).getQuestionStatus(EXAM_ID, 1, 0);
    }

    @ParameterizedTest
    @EnumSource(UserExamApi.class)
    void missingSessionIsRejectedBeforeAnyDownstreamOperation(UserExamApi api) {
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.empty());

        ExamsException exception = assertThrows(ExamsException.class, () -> invoke(api));

        assertSame(ErrorStatus._EXAM_NOT_FOUND, exception.getCode());
        verify(examSessionRepository).findById(EXAM_ID);
        verifyNoInteractions(
                currentUserProvider,
                redisTemplate,
                valueOperations,
                s3Presigner,
                restTemplate,
                examResultRepository,
                examSummaryRepository,
                azureResultRepository,
                speechAceResultRepository,
                mockExamRepository,
                gradingService
        );
    }

    @ParameterizedTest
    @EnumSource(UserExamApi.class)
    void anotherUsersSessionIsForbiddenBeforeAnyDownstreamOperation(UserExamApi api) {
        when(examSessionRepository.findById(EXAM_ID))
                .thenReturn(Optional.of(sessionFor(OTHER_USER_ID)));
        when(currentUserProvider.getCurrentUserId()).thenReturn(OWNER_USER_ID);

        ExamsException exception = assertThrows(ExamsException.class, () -> invoke(api));

        assertSame(ErrorStatus._FORBIDDEN, exception.getCode());
        verify(examSessionRepository).findById(EXAM_ID);
        verify(currentUserProvider).getCurrentUserId();
        verifyNoInteractions(
                redisTemplate,
                valueOperations,
                s3Presigner,
                restTemplate,
                examResultRepository,
                examSummaryRepository,
                azureResultRepository,
                speechAceResultRepository,
                mockExamRepository,
                gradingService
        );
    }

    @Test
    void feedbackCallbackResolvesSessionUserWithoutCurrentUserProvider() throws Exception {
        ExamRequestDTO.AiResultReq request = objectMapper.readValue("""
                {
                  "user_id": "ex_ownership_001",
                  "mock_exam_id": "mock_exam_003",
                  "part_number": 1,
                  "question_number": 1,
                  "retry_count": 0,
                  "score": 5.0
                }
                """, ExamRequestDTO.AiResultReq.class);
        when(examSessionRepository.findById(EXAM_ID))
                .thenReturn(Optional.of(sessionFor(OWNER_USER_ID)));

        examService.updateExamResult(request);

        ArgumentCaptor<ExamResult> resultCaptor = ArgumentCaptor.forClass(ExamResult.class);
        InOrder order = inOrder(examSessionRepository, examResultRepository);
        order.verify(examSessionRepository).findById(EXAM_ID);
        order.verify(examResultRepository).insert(resultCaptor.capture());
        assertAll(
                () -> assertEquals(EXAM_ID, resultCaptor.getValue().getExamId()),
                () -> assertEquals(OWNER_USER_ID, resultCaptor.getValue().getUserId()),
                () -> assertNotEquals(
                        resultCaptor.getValue().getExamId(),
                        resultCaptor.getValue().getUserId()
                )
        );
        verify(gradingService).completeQuestion(EXAM_ID, 1, 0);
        verify(gradingService).ensureSummaryStartedIfReady(EXAM_ID);
        verifyNoInteractions(
                examSummaryRepository,
                currentUserProvider,
                redisTemplate,
                s3Presigner,
                restTemplate
        );
    }

    @Test
    void feedbackCallbackMissingSessionUsesExamNotFoundWithoutCurrentUserProvider() throws Exception {
        ExamRequestDTO.AiResultReq request = objectMapper.readValue("""
                {
                  "user_id": "ex_ownership_001",
                  "question_number": 1,
                  "retry_count": 0
                }
                """, ExamRequestDTO.AiResultReq.class);
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.empty());

        ExamsException exception = assertThrows(
                ExamsException.class,
                () -> examService.updateExamResult(request)
        );

        assertSame(ErrorStatus._EXAM_NOT_FOUND, exception.getCode());
        verify(examSessionRepository).findById(EXAM_ID);
        verifyNoInteractions(
                currentUserProvider,
                redisTemplate,
                s3Presigner,
                restTemplate,
                examResultRepository,
                examSummaryRepository
        );
    }

    @Test
    void speechAceCallbackDoesNotUseCurrentUserProvider() throws Exception {
        ExamRequestDTO.SpeechAceReq request = objectMapper.readValue("""
                {
                  "user_id": "ex_ownership_001",
                  "question_number": 1,
                  "retry_count": 0,
                  "speechace_result": {"score": 90}
                }
                """, ExamRequestDTO.SpeechAceReq.class);

        examService.saveSpeechAceResult(request);

        ArgumentCaptor<SpeechAceResult> resultCaptor = ArgumentCaptor.forClass(SpeechAceResult.class);
        verify(speechAceResultRepository).insert(resultCaptor.capture());
        assertAll(
                () -> assertEquals(EXAM_ID, resultCaptor.getValue().getExamId()),
                () -> assertEquals(1, resultCaptor.getValue().getQuestionNumber()),
                () -> assertEquals(0, resultCaptor.getValue().getRetryCount())
        );
        verifyNoInteractions(currentUserProvider, examSessionRepository);
    }

    @Test
    void azureCallbackDoesNotUseCurrentUserProvider() {
        Map<String, Object> rawPayload = Map.of(
                "metadata", Map.of(
                        "user_id", EXAM_ID,
                        "question_number", 1,
                        "retry_count", 0
                ),
                "azure_speech_result", Map.of("recognition_status", "Success")
        );

        examService.processAzureCallback(rawPayload);

        ArgumentCaptor<AzureResult> resultCaptor = ArgumentCaptor.forClass(AzureResult.class);
        verify(azureResultRepository).insert(resultCaptor.capture());
        assertAll(
                () -> assertEquals(EXAM_ID, resultCaptor.getValue().getExamId()),
                () -> assertEquals(1, resultCaptor.getValue().getQuestionNumber()),
                () -> assertEquals(0, resultCaptor.getValue().getRetryCount()),
                () -> assertSame(rawPayload, resultCaptor.getValue().getRawData())
        );
        verifyNoInteractions(currentUserProvider, examSessionRepository);
    }

    private void stubOwnedSession() {
        when(examSessionRepository.findById(EXAM_ID))
                .thenReturn(Optional.of(sessionFor(OWNER_USER_ID)));
        when(currentUserProvider.getCurrentUserId()).thenReturn(OWNER_USER_ID);
    }

    private void stubEmptyQuestionRead(int retryCount) throws Exception {
        stubOwnedSession();
        when(examResultRepository.findByExamId(EXAM_ID)).thenReturn(List.of());
        when(examResultRepository.findFirstByExamIdAndQuestionNumberAndRetryCountInOrderByIdDesc(
                EXAM_ID,
                1,
                retryCount == 0 ? Arrays.asList(0, null) : List.of(retryCount)
        )).thenReturn(Optional.empty());
        when(mockExamRepository.findByMockExamId("mock_exam_003"))
                .thenReturn(Optional.of(mockExam()));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGetObjectRequest);
        when(presignedGetObjectRequest.url())
                .thenReturn(URI.create("https://example.com/submitted-audio.wav").toURL());
    }

    private AzureResult legacyAzure(String id, Integer retryCount, int omissionCount) {
        return AzureResult.builder()
                .id(id)
                .examId(EXAM_ID)
                .questionNumber(1)
                .retryCount(retryCount)
                .rawData(Map.of(
                        "azure_speech_result",
                        Map.of("error_counts", Map.of("omission", omissionCount))
                ))
                .build();
    }

    private ExamSession sessionFor(String userId) {
        return ExamSession.builder()
                .examId(EXAM_ID)
                .userId(userId)
                .createdAt(LocalDateTime.of(2026, 7, 23, 12, 0))
                .build();
    }

    private MockExam mockExam() {
        Question question = Question.builder()
                .partNumber(1)
                .questionNumber(1)
                .question("Test question")
                .build();
        return MockExam.builder()
                .mockExamId("mock_exam_003")
                .title("Test mock exam")
                .questions(List.of(question))
                .build();
    }

    private void invoke(UserExamApi api) {
        switch (api) {
            case UPLOAD_URL -> examService.getPresignedUrl(EXAM_ID, 1, 0);
            case SUBMIT_AUDIO -> examService.submitAudio(EXAM_ID, 1, 0);
            case RETRY_GRADING -> examService.retryGrading(EXAM_ID);
            case EXAM_STATUS -> examService.getExamStatus(EXAM_ID);
            case EXAM_SUMMARY -> examService.getExamSummary(EXAM_ID);
            case QUESTION_RESULT -> examService.getExamQuestion(EXAM_ID, 1, 0);
            case QUESTION_STATUS -> examService.getQuestionProcessingStatus(EXAM_ID, 1, 0);
        }
    }

    private enum UserExamApi {
        UPLOAD_URL,
        SUBMIT_AUDIO,
        RETRY_GRADING,
        EXAM_STATUS,
        EXAM_SUMMARY,
        QUESTION_RESULT,
        QUESTION_STATUS
    }
}
