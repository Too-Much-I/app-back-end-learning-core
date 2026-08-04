package web.tosunsaeng.domain.exams.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.domain.repository.AzureResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
import web.tosunsaeng.domain.exams.domain.repository.SpeechAceResultRepository;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.global.auth.CurrentUserProvider;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamQuestionModelAnswerTest {

    private static final String EXAM_ID = "ex_answer_audio_004";
    private static final String OWNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String MOCK_EXAM_ID = "mock_exam_004";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedGetObjectRequest presignedGetObjectRequest;

    @Mock
    private ExamGradingService gradingService;

    @Mock
    private ExamSessionManager examSessionManager;

    @Mock
    private ExamResultRepository examResultRepository;

    @Mock
    private ExamSummaryRepository examSummaryRepository;

    @Mock
    private ExamSessionRepository examSessionRepository;

    @Mock
    private MockExamCatalogService mockExamCatalogService;

    @Mock
    private SpeechAceResultRepository speechAceResultRepository;

    @Mock
    private AzureResultRepository azureResultRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ModelAnswerCatalogService modelAnswerCatalogService;
    private ExamServiceImpl examService;

    @BeforeEach
    void setUp() {
        modelAnswerCatalogService = spy(new ModelAnswerCatalogService(objectMapper));
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

        ExamSession session = ExamSession.builder()
                .examId(EXAM_ID)
                .userId(OWNER_USER_ID)
                .mockExamId(MOCK_EXAM_ID)
                .active(true)
                .build();
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(session));
        when(currentUserProvider.getCurrentUserId()).thenReturn(OWNER_USER_ID);
        lenient().when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGetObjectRequest);
    }

    @Test
    void beforeSubmissionOmitsModelAnswerWithoutPresigningOrCatalogLookup() {
        stubMissingResult(1, 0);

        ExamResponseDTO.QuestionResult result = examService.getExamQuestion(EXAM_ID, 1, 0);
        JsonNode json = objectMapper.valueToTree(result);

        assertAll(
                () -> assertNull(result.getQuestion().getModelAnswer()),
                () -> assertNull(result.getQuestion().getAudioUrl()),
                () -> assertFalse(json.path("question").has("modelAnswer"))
        );
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
        verify(modelAnswerCatalogService, never()).findSpokenWordSequence(anyString(), anyInt());
    }

    @Test
    void nonexistentRetryOmitsModelAnswerButKeepsExistingFeedbackHistory() {
        ExamResult initialResult = userResult(1, 0, "initial-user-recording-word");
        when(examResultRepository.findByExamId(EXAM_ID)).thenReturn(List.of(initialResult));
        when(examResultRepository.findFirstByExamIdAndQuestionNumberAndRetryCountInOrderByIdDesc(
                EXAM_ID,
                1,
                List.of(3)
        )).thenReturn(Optional.empty());
        when(mockExamCatalogService.getRequiredExam(MOCK_EXAM_ID))
                .thenReturn(mockExam(question(1, 1)));

        ExamResponseDTO.QuestionResult result = examService.getExamQuestion(EXAM_ID, 1, 3);

        assertAll(
                () -> assertNull(result.getQuestion().getModelAnswer()),
                () -> assertNull(result.getQuestion().getAudioUrl()),
                () -> assertEquals(List.of(0), result.getQuestion().getRetryScores().stream()
                        .map(ExamResponseDTO.RetryScoreDTO::getRetryCount)
                        .toList()),
                () -> assertEquals(1, result.getQuestion().getRetryFeedbackScores().size()),
                () -> assertNull(result.getQuestion().getFeedback().getSummary())
        );
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
        verify(modelAnswerCatalogService, never()).findSpokenWordSequence(anyString(), anyInt());
    }

    @Test
    void processingAttemptWithoutResultOmitsModelAnswerAndPresignedUrls() {
        stubMissingResult(1, 2);

        ExamResponseDTO.QuestionResult result = examService.getExamQuestion(EXAM_ID, 1, 2);

        assertAll(
                () -> assertNull(result.getQuestion().getModelAnswer()),
                () -> assertNull(result.getQuestion().getAudioUrl())
        );
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
        verify(modelAnswerCatalogService, never()).findSpokenWordSequence(anyString(), anyInt());
    }

    @Test
    void partOneQuestionOneAddsAudioAndItsOwnSpokenWordsWithoutChangingExistingFeedback() throws Exception {
        ExamResult userResult = userResult(1, 0, "user-recording-word");
        stubQuestion(1, 1, 0, userResult);
        stubPresignedUrls(
                "https://example.invalid/user-recording-q1",
                "https://example.invalid/model-answer-q1"
        );

        ExamResponseDTO.QuestionResult result = examService.getExamQuestion(EXAM_ID, 1, 0);

        ExamResponseDTO.PartResultDTO question = result.getQuestion();
        ExamResponseDTO.ModelAnswerResponse modelAnswer = question.getModelAnswer();
        ExamResponseDTO.SpokenWordDTO firstModelWord = modelAnswer.getSpokenWordSequence().getFirst();
        assertAll(
                () -> assertNotNull(modelAnswer),
                () -> assertEquals("https://example.invalid/user-recording-q1", question.getAudioUrl()),
                () -> assertEquals("https://example.invalid/model-answer-q1", modelAnswer.getAudioUrl()),
                () -> assertNotEquals(question.getAudioUrl(), modelAnswer.getAudioUrl()),
                () -> assertEquals("user-recording-word", question.getSpokenWordSequence().getFirst().getWord()),
                () -> assertEquals("welcome", firstModelWord.getWord()),
                () -> assertNotEquals(
                        question.getSpokenWordSequence().getFirst().getWord(),
                        firstModelWord.getWord()
                ),
                () -> assertEquals(0, firstModelWord.getIndex()),
                () -> assertEquals(0, firstModelWord.getSegmentIndex()),
                () -> assertEquals(0, firstModelWord.getWordIndex()),
                () -> assertEquals(400000L, firstModelWord.getOffset()),
                () -> assertEquals(7500000L, firstModelWord.getDuration()),
                () -> assertEquals(94.0, firstModelWord.getAccuracyScore()),
                () -> assertEquals(94.0, firstModelWord.getPronunciationScore()),
                () -> assertEquals("kept feedback", question.getFeedback().getSummary()),
                () -> assertEquals("reference for question 1", question.getQuestionInfo().getReferenceText()),
                () -> assertEquals("question-source-audio-1", question.getQuestionInfo().getAudioUrl())
        );
        assertPresignedKeys(
                "temp/" + EXAM_ID + "/q_1_r0.wav",
                MOCK_EXAM_ID + "/part1_a1.wav"
        );
    }

    @Test
    void partOneQuestionTwoAddsAudioAndQuestionTwoSpokenWords() throws Exception {
        ExamResult userResult = userResult(2, 0, "second-user-recording-word");
        stubQuestion(1, 2, 0, userResult);
        stubPresignedUrls(
                "https://example.invalid/user-recording-q2",
                "https://example.invalid/model-answer-q2"
        );

        ExamResponseDTO.QuestionResult result = examService.getExamQuestion(EXAM_ID, 2, 0);

        ExamResponseDTO.SpokenWordDTO first = result.getQuestion()
                .getModelAnswer()
                .getSpokenWordSequence()
                .getFirst();
        assertAll(
                () -> assertNotNull(result.getQuestion().getModelAnswer()),
                () -> assertEquals("please", first.getWord()),
                () -> assertEquals(0, first.getIndex()),
                () -> assertEquals(0, first.getSegmentIndex()),
                () -> assertEquals(0, first.getWordIndex()),
                () -> assertEquals(700000L, first.getOffset()),
                () -> assertEquals(4800000L, first.getDuration()),
                () -> assertEquals(94.0, first.getAccuracyScore()),
                () -> assertEquals(94.0, first.getPronunciationScore())
        );
        assertPresignedKeys(
                "temp/" + EXAM_ID + "/q_2_r0.wav",
                MOCK_EXAM_ID + "/part1_a2.wav"
        );
    }

    @Test
    void retryCountChangesOnlyTheUserRecordingAndKeepsTheSameModelAnswerSource() throws Exception {
        ExamResult userResult = userResult(1, 0, "user-recording-word");
        when(examResultRepository.findByExamId(EXAM_ID)).thenReturn(List.of(userResult));
        when(examResultRepository.findFirstByExamIdAndQuestionNumberAndRetryCountInOrderByIdDesc(
                eq(EXAM_ID),
                eq(1),
                anyList()
        )).thenReturn(Optional.of(userResult));
        when(azureResultRepository.findFirstByExamIdAndQuestionNumberAndRetryCountOrderByIdDesc(
                eq(EXAM_ID),
                eq(1),
                anyInt()
        )).thenReturn(Optional.empty());
        when(mockExamCatalogService.getRequiredExam(MOCK_EXAM_ID))
                .thenReturn(mockExam(question(1, 1)));
        stubPresignedUrls(
                "https://example.invalid/user-recording-r0",
                "https://example.invalid/model-answer-q1",
                "https://example.invalid/user-recording-r3",
                "https://example.invalid/model-answer-q1"
        );

        ExamResponseDTO.QuestionResult retryZero = examService.getExamQuestion(EXAM_ID, 1, 0);
        ExamResponseDTO.QuestionResult retryThree = examService.getExamQuestion(EXAM_ID, 1, 3);

        assertAll(
                () -> assertEquals(
                        retryZero.getQuestion().getModelAnswer().getAudioUrl(),
                        retryThree.getQuestion().getModelAnswer().getAudioUrl()
                ),
                () -> assertEquals(
                        retryZero.getQuestion().getModelAnswer().getSpokenWordSequence().stream()
                                .map(ExamResponseDTO.SpokenWordDTO::getWord)
                                .toList(),
                        retryThree.getQuestion().getModelAnswer().getSpokenWordSequence().stream()
                                .map(ExamResponseDTO.SpokenWordDTO::getWord)
                                .toList()
                )
        );

        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner, times(4)).presignGetObject(captor.capture());
        assertEquals(List.of(
                        "temp/" + EXAM_ID + "/q_1_r0.wav",
                        MOCK_EXAM_ID + "/part1_a1.wav",
                        "temp/" + EXAM_ID + "/q_1_r3.wav",
                        MOCK_EXAM_ID + "/part1_a1.wav"
                ), captor.getAllValues().stream()
                        .map(request -> request.getObjectRequest().key())
                        .toList());
    }

    @ParameterizedTest
    @MethodSource("questionsWithoutModelAnswer")
    void questionsOutsidePartOneQuestionOneAndTwoOmitTheField(int partNumber, int questionNumber)
            throws Exception {
        ExamResult userResult = userResult(questionNumber, 0, "user-recording-word");
        stubQuestion(partNumber, questionNumber, 0, userResult);
        stubPresignedUrls("https://example.invalid/user-recording-only");

        ExamResponseDTO.QuestionResult result =
                examService.getExamQuestion(EXAM_ID, questionNumber, 0);
        JsonNode json = objectMapper.valueToTree(result);

        assertAll(
                () -> assertNull(result.getQuestion().getModelAnswer()),
                () -> assertFalse(json.path("question").has("modelAnswer"))
        );
        verify(s3Presigner).presignGetObject(any(GetObjectPresignRequest.class));
        verify(modelAnswerCatalogService, never()).findSpokenWordSequence(anyString(), anyInt());
    }

    private static Stream<Arguments> questionsWithoutModelAnswer() {
        return Stream.of(
                Arguments.of(2, 1),
                Arguments.of(1, 3)
        );
    }

    private void stubQuestion(
            int partNumber,
            int questionNumber,
            int retryCount,
            ExamResult userResult) {
        when(examResultRepository.findByExamId(EXAM_ID)).thenReturn(List.of(userResult));
        when(examResultRepository.findFirstByExamIdAndQuestionNumberAndRetryCountInOrderByIdDesc(
                EXAM_ID,
                questionNumber,
                retryCount == 0 ? Arrays.asList(0, null) : List.of(retryCount)
        )).thenReturn(Optional.of(userResult));
        when(azureResultRepository.findFirstByExamIdAndQuestionNumberAndRetryCountOrderByIdDesc(
                EXAM_ID,
                questionNumber,
                retryCount
        )).thenReturn(Optional.empty());
        when(mockExamCatalogService.getRequiredExam(MOCK_EXAM_ID))
                .thenReturn(mockExam(question(partNumber, questionNumber)));
    }

    private void stubMissingResult(int questionNumber, int retryCount) {
        when(examResultRepository.findByExamId(EXAM_ID)).thenReturn(List.of());
        when(examResultRepository.findFirstByExamIdAndQuestionNumberAndRetryCountInOrderByIdDesc(
                EXAM_ID,
                questionNumber,
                retryCount == 0 ? Arrays.asList(0, null) : List.of(retryCount)
        )).thenReturn(Optional.empty());
        when(mockExamCatalogService.getRequiredExam(MOCK_EXAM_ID))
                .thenReturn(mockExam(question(1, questionNumber)));
    }

    private ExamResult userResult(int questionNumber, int retryCount, String word) {
        return ExamResult.builder()
                .examId(EXAM_ID)
                .userId(OWNER_USER_ID)
                .partNumber(questionNumber <= 2 ? 1 : 2)
                .questionNumber(questionNumber)
                .retryCount(retryCount)
                .score(2.8)
                .maxScore(3.0)
                .transcript("user transcript")
                .feedback(ExamResult.ItemFeedback.builder()
                        .summary("kept feedback")
                        .build())
                .spokenWordSequence(List.of(ExamResult.SpokenWord.builder()
                        .index(7)
                        .segmentIndex(3)
                        .wordIndex(4)
                        .word(word)
                        .offset(123L)
                        .duration(456L)
                        .accuracyScore(70.0)
                        .pronunciationScore(71.0)
                        .errorType("None")
                        .build()))
                .build();
    }

    private Question question(int partNumber, int questionNumber) {
        return Question.builder()
                .partNumber(partNumber)
                .questionNumber(questionNumber)
                .question("Question " + questionNumber)
                .referenceText("reference for question " + questionNumber)
                .audioUrl("question-source-audio-" + questionNumber)
                .build();
    }

    private MockExam mockExam(Question question) {
        return MockExam.builder()
                .mockExamId(MOCK_EXAM_ID)
                .title("Mock exam four")
                .questions(List.of(question))
                .build();
    }

    private void stubPresignedUrls(String... urls) throws Exception {
        List<java.net.URL> resolvedUrls = Arrays.stream(urls)
                .map(URI::create)
                .map(uri -> {
                    try {
                        return uri.toURL();
                    } catch (Exception exception) {
                        throw new IllegalArgumentException(exception);
                    }
                })
                .toList();
        AtomicInteger invocation = new AtomicInteger();
        when(presignedGetObjectRequest.url()).thenAnswer(ignored ->
                resolvedUrls.get(invocation.getAndIncrement()));
    }

    private void assertPresignedKeys(String... expectedKeys) {
        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner, times(expectedKeys.length)).presignGetObject(captor.capture());
        assertEquals(List.of(expectedKeys), captor.getAllValues().stream()
                .map(request -> request.getObjectRequest().key())
                .toList());
    }
}
