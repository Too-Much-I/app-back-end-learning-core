package web.tosunsaeng.domain.exams.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
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
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;
import web.tosunsaeng.domain.exams.domain.repository.AzureResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ExamOwnershipServiceTest {

    private static final String EXAM_ID = "ex_ownership_001";
    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String OTHER_USER_ID = "00000000-0000-0000-0000-000000000002";

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
    private MockExamCatalogService mockExamCatalogService;

    @Mock
    private ModelAnswerCatalogService modelAnswerCatalogService;

    @Mock
    private SpeechAceResultRepository speechAceResultRepository;

    @Mock
    private AzureResultRepository azureResultRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private ExamGradingService gradingService;

    @Mock
    private ExamSessionManager examSessionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ExamServiceImpl examService;

    @BeforeEach
    void setUp() {
        examService = new ExamServiceImpl(
                redisTemplate,
                s3Presigner,
                gradingService,
                examSessionManager,
                examResultRepository,
                examSummaryRepository,
                examSessionRepository,
                mockExamCatalogService,
                modelAnswerCatalogService,
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
    void ownerCanReadPartSpecificPromptFromSessionAssignedMockExam() throws Exception {
        ExamSession selectedSession = ExamSession.builder()
                .examId(EXAM_ID)
                .userId(OWNER_USER_ID)
                .mockExamId("mock_exam_002")
                .active(true)
                .build();
        Question question = Question.builder()
                .partNumber(3)
                .questionNumber(5)
                .question("Answer the following questions.")
                .partIntroText("Respond to questions 5 through 7.")
                .imageUrl("question-image-placeholder")
                .prepTimeSec(3)
                .speakTimeSec(15)
                .build();
        MockExam selectedPaper = MockExam.builder()
                .mockExamId("mock_exam_002")
                .questions(List.of(question))
                .build();

        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(selectedSession));
        when(currentUserProvider.getCurrentUserId()).thenReturn(OWNER_USER_ID);
        when(mockExamCatalogService.getRequiredExam("mock_exam_002")).thenReturn(selectedPaper);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGetObjectRequest);
        when(presignedGetObjectRequest.url()).thenReturn(
                URI.create("https://example.com/question-audio").toURL(),
                URI.create("https://example.com/guide-audio").toURL()
        );

        ExamResponseDTO.QuestionDTO result = examService.getQuestionPrompt(EXAM_ID, 5);

        assertAll(
                () -> assertEquals(3, result.getPart()),
                () -> assertEquals(5, result.getQuestionNumber()),
                () -> assertEquals("Answer the following questions.", result.getText()),
                () -> assertEquals("Respond to questions 5 through 7.", result.getPartIntroText()),
                () -> assertEquals("question-image-placeholder", result.getImageUrl()),
                () -> assertEquals(3, result.getPrepTimeSec()),
                () -> assertEquals(15, result.getSpeakTimeSec()),
                () -> assertEquals("https://example.com/question-audio", result.getAudioUrl()),
                () -> assertEquals("https://example.com/guide-audio", result.getGuideAudioUrl())
        );

        ArgumentCaptor<GetObjectPresignRequest> requestCaptor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner, times(2)).presignGetObject(requestCaptor.capture());
        assertEquals(
                List.of(
                        "questions/mock_exam_002/q_5.wav",
                        "questions/mock_exam_002/part3_intro.wav"
                ),
                requestCaptor.getAllValues().stream()
                        .map(request -> request.getObjectRequest().key())
                        .toList()
        );
        verify(mockExamCatalogService).getRequiredExam("mock_exam_002");
        verify(mockExamCatalogService, never()).getRequiredExam("mock_exam_003");
    }

    @Test
    void partFourPromptReturnsStoredOpaqueTableContextWithoutTableImage() throws Exception {
        stubOwnedSession();
        Map<String, Object> storedTableContext = Map.of(
                "resume_owner", "Maya Bennett",
                "education_history", List.of(Map.of(
                        "graduation_year", 2022,
                        "university_name", "Example University"
                ))
        );
        Question question = Question.builder()
                .partNumber(4)
                .questionNumber(8)
                .question("Part 4 prompt")
                .tableImageUrl("https://cdn.example.com/legacy-table-image.png")
                .tableContext(storedTableContext)
                .build();
        when(mockExamCatalogService.getRequiredExam("mock_exam_003")).thenReturn(MockExam.builder()
                .mockExamId("mock_exam_003")
                .questions(List.of(question))
                .build());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGetObjectRequest);
        when(presignedGetObjectRequest.url())
                .thenReturn(URI.create("https://example.com/question-audio").toURL());

        ExamResponseDTO.QuestionDTO result = examService.getQuestionPrompt(EXAM_ID, 8);
        JsonNode resultJson = objectMapper.valueToTree(result);

        assertAll(
                () -> assertEquals(4, result.getPart()),
                () -> assertEquals(8, result.getQuestionNumber()),
                () -> assertSame(storedTableContext, result.getTableContext()),
                () -> assertEquals(
                        objectMapper.valueToTree(storedTableContext),
                        resultJson.get("tableContext")
                ),
                () -> assertFalse(resultJson.has("tableImageUrl"))
        );
    }

    @Test
    void partFourPromptWithoutTableContextUsesCatalogConfigurationError() {
        stubOwnedSession();
        Question question = Question.builder()
                .partNumber(4)
                .questionNumber(8)
                .tableImageUrl("https://cdn.example.com/legacy-table-image.png")
                .build();
        when(mockExamCatalogService.getRequiredExam("mock_exam_003")).thenReturn(MockExam.builder()
                .mockExamId("mock_exam_003")
                .questions(List.of(question))
                .build());

        ExamsException exception = assertThrows(
                ExamsException.class,
                () -> examService.getQuestionPrompt(EXAM_ID, 8)
        );

        assertSame(ErrorStatus._EXAM_CATALOG_CONFIGURATION_ERROR, exception.getCode());
        verifyNoInteractions(s3Presigner);
    }

    @Test
    void missingPromptQuestionFailsBeforeCreatingPresignedUrls() {
        stubOwnedSession();
        when(mockExamCatalogService.getRequiredExam("mock_exam_003")).thenReturn(mockExam());

        ExamsException exception = assertThrows(
                ExamsException.class,
                () -> examService.getQuestionPrompt(EXAM_ID, 2)
        );

        assertSame(ErrorStatus._QUESTION_NOT_FOUND, exception.getCode());
        verify(mockExamCatalogService).getRequiredExam("mock_exam_003");
        verifyNoInteractions(s3Presigner);
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
    void examSummaryPartScoresIncludeOnlyInitialAttempts() {
        stubOwnedSession();
        ExamSummary summary = ExamSummary.builder()
                .examId(EXAM_ID)
                .userId(OWNER_USER_ID)
                .totalScore(180)
                .build();
        ExamResult initialResult = ExamResult.builder()
                .examId(EXAM_ID)
                .userId(OWNER_USER_ID)
                .partNumber(1)
                .questionNumber(1)
                .retryCount(0)
                .score(5.0)
                .build();
        ExamResult retriedResult = ExamResult.builder()
                .examId(EXAM_ID)
                .userId(OWNER_USER_ID)
                .partNumber(1)
                .questionNumber(1)
                .retryCount(1)
                .score(9.0)
                .build();
        ExamResult legacyResultWithoutRetryCount = ExamResult.builder()
                .examId(EXAM_ID)
                .userId(OWNER_USER_ID)
                .partNumber(2)
                .questionNumber(3)
                .score(4.0)
                .build();
        when(examResultRepository.findByExamId(EXAM_ID))
                .thenReturn(List.of(initialResult, retriedResult, legacyResultWithoutRetryCount));
        when(examSummaryRepository.findFirstByExamIdOrderByIdDesc(EXAM_ID))
                .thenReturn(Optional.of(summary));

        ExamResponseDTO.SummaryResult result = examService.getExamSummary(EXAM_ID);

        assertAll(
                () -> assertEquals(5.0, result.getPartScores().get("part1")),
                () -> assertFalse(result.getPartScores().containsKey("part2")),
                () -> assertEquals(1, result.getTotalSolvedQuestions())
        );
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
        when(mockExamCatalogService.getRequiredExam("mock_exam_003"))
                .thenReturn(mockExam());
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
        verify(mockExamCatalogService).getRequiredExam("mock_exam_003");
        verify(s3Presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void questionResultUsesMockExamIdStoredInSession() throws Exception {
        ExamSession selectedSession = ExamSession.builder()
                .examId(EXAM_ID)
                .userId(OWNER_USER_ID)
                .mockExamId("mock_exam_002")
                .active(true)
                .build();
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(selectedSession));
        when(currentUserProvider.getCurrentUserId()).thenReturn(OWNER_USER_ID);
        when(examResultRepository.findByExamId(EXAM_ID)).thenReturn(List.of());
        when(examResultRepository.findFirstByExamIdAndQuestionNumberAndRetryCountInOrderByIdDesc(
                EXAM_ID,
                1,
                Arrays.asList(0, null)
        )).thenReturn(Optional.empty());
        when(azureResultRepository.findFirstByExamIdAndQuestionNumberAndRetryCountOrderByIdDesc(EXAM_ID, 1, 0))
                .thenReturn(Optional.empty());
        MockExam selectedPaper = MockExam.builder()
                .mockExamId("mock_exam_002")
                .questions(List.of(Question.builder()
                        .partNumber(1)
                        .questionNumber(1)
                        .question("Selected paper question")
                        .build()))
                .build();
        when(mockExamCatalogService.getRequiredExam("mock_exam_002"))
                .thenReturn(selectedPaper);

        ExamResponseDTO.QuestionResult result = examService.getExamQuestion(EXAM_ID, 1, 0);

        assertEquals("Selected paper question", result.getQuestion().getQuestionInfo().getText());
        verify(mockExamCatalogService).getRequiredExam("mock_exam_002");
        verify(mockExamCatalogService, never()).getRequiredExam("mock_exam_003");
    }

    @Test
    void partFourQuestionInfoReturnsStoredOpaqueTableContextWithoutTableImage() {
        String storedUrl = "https://cdn.example.com/mock-exam/001/part4/q8.png";
        Map<String, Object> tableContext = Map.of(
                "resume_owner", "Maya Bennett",
                "work_experience", List.of(Map.of(
                        "section_name", "Work Experience",
                        "details", List.of("Batch cooking", "Knife safety")
                ))
        );
        Question partFour = Question.builder()
                .partNumber(4)
                .questionNumber(8)
                .question("Legacy table question text")
                .referenceText("Legacy reference")
                .partIntroText("Legacy introduction")
                .audioUrl("legacy-question-audio")
                .guideAudioUrl("legacy-guide-audio")
                .imageUrl("legacy-image")
                .tableImageUrl(storedUrl)
                .tableContext(tableContext)
                .prepTimeSec(30)
                .speakTimeSec(15)
                .build();
        stubQuestionResultPaper(partFour);

        ExamResponseDTO.QuestionResult result = examService.getExamQuestion(EXAM_ID, 8, 0);

        ExamResponseDTO.QuestionDTO questionInfo = result.getQuestion().getQuestionInfo();
        JsonNode questionInfoJson = objectMapper.valueToTree(questionInfo);
        assertAll(
                () -> assertEquals(4, questionInfo.getPart()),
                () -> assertEquals(8, questionInfo.getQuestionNumber()),
                () -> assertSame(tableContext, questionInfo.getTableContext()),
                () -> assertEquals(3, questionInfoJson.size()),
                () -> assertEquals(
                        objectMapper.valueToTree(tableContext),
                        questionInfoJson.get("tableContext")
                ),
                () -> assertFalse(questionInfoJson.has("tableImageUrl")),
                () -> assertFalse(questionInfoJson.has("table_image_url")),
                () -> assertFalse(questionInfoJson.has("text")),
                () -> assertFalse(questionInfoJson.has("referenceText")),
                () -> assertFalse(questionInfoJson.has("partIntroText")),
                () -> assertFalse(questionInfoJson.has("audioUrl")),
                () -> assertFalse(questionInfoJson.has("guideAudioUrl")),
                () -> assertFalse(questionInfoJson.has("imageUrl")),
                () -> assertFalse(questionInfoJson.has("prepTimeSec")),
                () -> assertFalse(questionInfoJson.has("speakTimeSec"))
        );
    }

    @Test
    void partFourQuestionWithoutTableContextUsesCatalogConfigurationError() {
        Question partFour = Question.builder()
                .partNumber(4)
                .questionNumber(8)
                .tableImageUrl("https://cdn.example.com/legacy-table-image.png")
                .build();
        stubQuestionResultPaper(partFour);

        ExamsException exception = assertThrows(
                ExamsException.class,
                () -> examService.getExamQuestion(EXAM_ID, 8, 0)
        );

        assertSame(ErrorStatus._EXAM_CATALOG_CONFIGURATION_ERROR, exception.getCode());
        verifyNoInteractions(s3Presigner);
    }

    @Test
    void partFourQuestionWithEmptyTableContextReturnsEmptyObject() {
        Question partFour = Question.builder()
                .partNumber(4)
                .questionNumber(8)
                .tableContext(Map.of())
                .build();
        stubQuestionResultPaper(partFour);

        ExamResponseDTO.QuestionResult result = examService.getExamQuestion(EXAM_ID, 8, 0);

        JsonNode tableContext = objectMapper.valueToTree(
                result.getQuestion().getQuestionInfo().getTableContext()
        );
        assertAll(
                () -> assertTrue(result.getQuestion().getQuestionInfo().getTableContext().isEmpty()),
                () -> assertTrue(tableContext.isObject()),
                () -> assertEquals(0, tableContext.size())
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 6, 7})
    void nonPartFourQuestionInfoKeepsExistingFields(int partNumber) {
        Map<String, Object> existingTableContext = Map.of("existing_context", true);
        Question question = Question.builder()
                .partNumber(partNumber)
                .questionNumber(partNumber)
                .question("Existing text")
                .referenceText("Existing reference")
                .partIntroText("Existing introduction")
                .audioUrl("existing-audio")
                .guideAudioUrl("existing-guide")
                .imageUrl("existing-image")
                .tableContext(existingTableContext)
                .prepTimeSec(30)
                .speakTimeSec(45)
                .build();
        stubQuestionResultPaper(question);

        ExamResponseDTO.QuestionResult result =
                examService.getExamQuestion(EXAM_ID, partNumber, 0);

        ExamResponseDTO.QuestionDTO questionInfo = result.getQuestion().getQuestionInfo();
        assertAll(
                () -> assertEquals(partNumber, questionInfo.getPart()),
                () -> assertEquals(partNumber, questionInfo.getQuestionNumber()),
                () -> assertEquals("Existing text", questionInfo.getText()),
                () -> assertEquals("Existing reference", questionInfo.getReferenceText()),
                () -> assertEquals("Existing introduction", questionInfo.getPartIntroText()),
                () -> assertEquals("existing-audio", questionInfo.getAudioUrl()),
                () -> assertEquals("existing-guide", questionInfo.getGuideAudioUrl()),
                () -> assertEquals("existing-image", questionInfo.getImageUrl()),
                () -> assertSame(existingTableContext, questionInfo.getTableContext()),
                () -> assertEquals(30, questionInfo.getPrepTimeSec()),
                () -> assertEquals(45, questionInfo.getSpeakTimeSec())
        );
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
        when(mockExamCatalogService.getRequiredExam("mock_exam_003"))
                .thenReturn(mockExam());
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
    void questionResultIncludesLatestScoreForEachRetryInRetryOrder() throws Exception {
        stubOwnedSession();
        ExamResult legacyRetryZero = scoredResult(
                "000000000000000000000001", 1, null, 1.0,
                feedbackScores(7.1, 7.2, 7.6, 7.3));
        ExamResult latestRetryZero = scoredResult(
                "000000000000000000000002", 1, 0, 2.0,
                feedbackScores(8.1, 8.2, 8.6, 8.3));
        ExamResult retryOne = scoredResult(
                "000000000000000000000003", 1, 1, 2.0,
                feedbackScores(8.5, 8.6, 8.7, 8.8));
        ExamResult retryTwo = scoredResult("000000000000000000000004", 1, 2, 1.0);
        ExamResult olderRetryThree = scoredResult("000000000000000000000005", 1, 3, 2.5);
        ExamResult latestRetryThree = scoredResult(
                "000000000000000000000006", 1, 3, 3.0,
                feedbackScores(9.0, 8.8, 9.1, 8.9));
        ExamResult otherQuestion = scoredResult("000000000000000000000007", 2, 0, 9.0);
        ExamResult latestRetryFourWithoutScore = scoredResult(
                "000000000000000000000009", 1, 4, null);
        ExamResult olderRetryFourWithScore = scoredResult(
                "000000000000000000000008", 1, 4, 4.0);

        when(examResultRepository.findByExamId(EXAM_ID)).thenReturn(List.of(
                retryTwo,
                legacyRetryZero,
                olderRetryThree,
                latestRetryFourWithoutScore,
                retryOne,
                latestRetryThree,
                otherQuestion,
                latestRetryZero,
                olderRetryFourWithScore
        ));
        when(examResultRepository.findFirstByExamIdAndQuestionNumberAndRetryCountInOrderByIdDesc(
                EXAM_ID,
                1,
                List.of(3)
        )).thenReturn(Optional.of(latestRetryThree));
        when(azureResultRepository.findFirstByExamIdAndQuestionNumberAndRetryCountOrderByIdDesc(EXAM_ID, 1, 3))
                .thenReturn(Optional.empty());
        when(mockExamCatalogService.getRequiredExam("mock_exam_003"))
                .thenReturn(mockExam());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGetObjectRequest);
        when(presignedGetObjectRequest.url())
                .thenReturn(URI.create("https://example.com/submitted-audio.wav").toURL());

        ExamResponseDTO.QuestionResult result = examService.getExamQuestion(EXAM_ID, 1, 3);

        List<ExamResponseDTO.RetryScoreDTO> retryScores = result.getQuestion().getRetryScores();
        List<ExamResponseDTO.RetryFeedbackScoreDTO> retryFeedbackScores =
                result.getQuestion().getRetryFeedbackScores();
        assertAll(
                () -> assertEquals(List.of(0, 1, 2, 3), retryScores.stream()
                        .map(ExamResponseDTO.RetryScoreDTO::getRetryCount)
                        .toList()),
                () -> assertEquals(List.of(2.0, 2.0, 1.0, 3.0), retryScores.stream()
                        .map(ExamResponseDTO.RetryScoreDTO::getScore)
                        .toList()),
                () -> assertEquals(9.0,
                        result.getQuestion().getFeedback().getPronunciationFluencyScore()),
                () -> assertEquals(1, retryFeedbackScores.size()),
                () -> assertEquals(0, retryFeedbackScores.get(0).getRetryCount()),
                () -> assertEquals(8.1, retryFeedbackScores.get(0).getPronunciationFluencyScore()),
                () -> assertEquals(8.2, retryFeedbackScores.get(0).getContentRelevanceScore()),
                () -> assertEquals(List.of(
                                Map.of("accuracy_score", 8.6),
                                Map.of("fluency_score", 8.3)
                        ), retryFeedbackScores.get(0).getDetailedScores())
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

    @Test
    void ownerCannotRetryAbandonedExam() {
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(ExamSession.builder()
                .examId(EXAM_ID)
                .userId(OWNER_USER_ID)
                .active(false)
                .status(ExamSessionStatus.ABANDONED)
                .build()));
        when(currentUserProvider.getCurrentUserId()).thenReturn(OWNER_USER_ID);

        ExamsException exception = assertThrows(
                ExamsException.class,
                () -> examService.retryGrading(EXAM_ID)
        );

        assertSame(ErrorStatus._EXAM_ABANDONED, exception.getCode());
        verify(gradingService, never()).retryExam(EXAM_ID);
    }

    @Test
    void ownerCannotRetryCompletedExam() {
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(ExamSession.builder()
                .examId(EXAM_ID)
                .userId(OWNER_USER_ID)
                .active(false)
                .status(ExamSessionStatus.COMPLETED)
                .completedAt(LocalDateTime.of(2026, 7, 23, 13, 0))
                .build()));
        when(currentUserProvider.getCurrentUserId()).thenReturn(OWNER_USER_ID);

        ExamsException exception = assertThrows(
                ExamsException.class,
                () -> examService.retryGrading(EXAM_ID)
        );

        assertSame(ErrorStatus._EXAM_ALREADY_COMPLETED, exception.getCode());
        verify(gradingService, never()).retryExam(EXAM_ID);
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
                mockExamCatalogService,
                modelAnswerCatalogService,
                gradingService
        );
    }

    @ParameterizedTest
    @EnumSource(UserExamApi.class)
    void anotherUsersSessionIsForbiddenBeforeAnyDownstreamOperation(
            UserExamApi api,
            CapturedOutput output) {
        when(examSessionRepository.findById(EXAM_ID))
                .thenReturn(Optional.of(sessionFor(OTHER_USER_ID)));
        when(currentUserProvider.getCurrentUserId()).thenReturn(OWNER_USER_ID);

        ExamsException exception = assertThrows(ExamsException.class, () -> invoke(api));

        assertSame(ErrorStatus._FORBIDDEN, exception.getCode());
        assertTrue(output.getOut().contains(
                "시험 소유권 검증 실패 "
                        + "event=exam.access outcome=denied reason=ownership_mismatch examId=" + EXAM_ID
        ));
        assertFalse(output.getOut().contains(OWNER_USER_ID));
        assertFalse(output.getOut().contains(OTHER_USER_ID));
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
                mockExamCatalogService,
                modelAnswerCatalogService,
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
        when(examSessionRepository.findById(EXAM_ID))
                .thenReturn(Optional.of(sessionFor(OWNER_USER_ID)));

        examService.saveSpeechAceResult(request);

        ArgumentCaptor<SpeechAceResult> resultCaptor = ArgumentCaptor.forClass(SpeechAceResult.class);
        verify(speechAceResultRepository).insert(resultCaptor.capture());
        assertAll(
                () -> assertEquals(EXAM_ID, resultCaptor.getValue().getExamId()),
                () -> assertEquals(1, resultCaptor.getValue().getQuestionNumber()),
                () -> assertEquals(0, resultCaptor.getValue().getRetryCount())
        );
        verify(examSessionRepository).findById(EXAM_ID);
        verifyNoInteractions(currentUserProvider);
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
        when(examSessionRepository.findById(EXAM_ID))
                .thenReturn(Optional.of(sessionFor(OWNER_USER_ID)));

        examService.processAzureCallback(rawPayload);

        ArgumentCaptor<AzureResult> resultCaptor = ArgumentCaptor.forClass(AzureResult.class);
        verify(azureResultRepository).insert(resultCaptor.capture());
        assertAll(
                () -> assertEquals(EXAM_ID, resultCaptor.getValue().getExamId()),
                () -> assertEquals(1, resultCaptor.getValue().getQuestionNumber()),
                () -> assertEquals(0, resultCaptor.getValue().getRetryCount()),
                () -> assertSame(rawPayload, resultCaptor.getValue().getRawData())
        );
        verify(examSessionRepository).findById(EXAM_ID);
        verifyNoInteractions(currentUserProvider);
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
        when(mockExamCatalogService.getRequiredExam("mock_exam_003"))
                .thenReturn(mockExam());
    }

    private void stubQuestionResultPaper(Question question) {
        stubOwnedSession();
        when(examResultRepository.findByExamId(EXAM_ID)).thenReturn(List.of());
        when(examResultRepository.findFirstByExamIdAndQuestionNumberAndRetryCountInOrderByIdDesc(
                EXAM_ID,
                question.getQuestionNumber(),
                Arrays.asList(0, null)
        )).thenReturn(Optional.empty());
        when(azureResultRepository.findFirstByExamIdAndQuestionNumberAndRetryCountOrderByIdDesc(
                EXAM_ID,
                question.getQuestionNumber(),
                0
        )).thenReturn(Optional.empty());
        when(mockExamCatalogService.getRequiredExam("mock_exam_003")).thenReturn(MockExam.builder()
                .mockExamId("mock_exam_003")
                .questions(List.of(question))
                .build());
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

    private ExamResult scoredResult(
            String id,
            Integer questionNumber,
            Integer retryCount,
            Double score) {
        return scoredResult(id, questionNumber, retryCount, score, null);
    }

    private ExamResult scoredResult(
            String id,
            Integer questionNumber,
            Integer retryCount,
            Double score,
            ExamResult.ItemFeedback feedback) {
        return ExamResult.builder()
                .id(id)
                .examId(EXAM_ID)
                .userId(OWNER_USER_ID)
                .questionNumber(questionNumber)
                .retryCount(retryCount)
                .score(score)
                .feedback(feedback)
                .build();
    }

    private ExamResult.ItemFeedback feedbackScores(
            Double pronunciationFluencyScore,
            Double contentRelevanceScore,
            Double accuracyScore,
            Double fluencyScore) {
        return ExamResult.ItemFeedback.builder()
                .pronunciationFluencyScore(pronunciationFluencyScore)
                .contentRelevanceScore(contentRelevanceScore)
                .accuracyScore(accuracyScore)
                .fluencyScore(fluencyScore)
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
            case QUESTION_PROMPT -> examService.getQuestionPrompt(EXAM_ID, 1);
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
        QUESTION_PROMPT,
        EXAM_SUMMARY,
        QUESTION_RESULT,
        QUESTION_STATUS
    }
}
