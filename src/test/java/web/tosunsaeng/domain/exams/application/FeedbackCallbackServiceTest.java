package web.tosunsaeng.domain.exams.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;
import web.tosunsaeng.domain.exams.domain.repository.AzureResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.MockExamRepository;
import web.tosunsaeng.domain.exams.domain.repository.SpeechAceResultRepository;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.auth.CurrentUserProvider;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackCallbackServiceTest {

    private static final String EXAM_ID = "ex_callback_001";
    private static final String USER_ID = "00000000-0000-0000-0000-000000000042";
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
    private RestTemplate restTemplate;

    @Mock
    private ExamResultRepository examResultRepository;

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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ExamServiceImpl examService;

    @BeforeEach
    void setUp() {
        examService = new ExamServiceImpl(
                redisTemplate,
                s3Presigner,
                restTemplate,
                examResultRepository,
                examSessionRepository,
                mockExamRepository,
                speechAceResultRepository,
                azureResultRepository,
                currentUserProvider
        );
        ReflectionTestUtils.setField(examService, "bucketName", "test-learning-core-bucket");
    }

    @Test
    void itemFeedbackCallbackSavesSessionUserIdWithoutChangingExistingMappings() throws Exception {
        ExamRequestDTO.AiResultReq req = objectMapper.readValue("""
                {
                  "user_id": "ex_callback_001",
                  "mock_exam_id": "mock_exam_003",
                  "part_number": 2,
                  "question_number": 4,
                  "retry_count": 2,
                  "score": 8.5,
                  "max_score": 10.0,
                  "transcript": "synthetic test transcript",
                  "feedback": {
                    "summary": "test summary",
                    "level": "Advanced",
                    "pronunciation_fluency_score": 8.1,
                    "content_relevance_score": 8.2,
                    "fluency_score": 8.3,
                    "completeness_score": 8.4,
                    "prosody_score": 8.5,
                    "accuracy_score": 8.6,
                    "strengths": ["clear structure"],
                    "weaknesses": ["test weakness"],
                    "pronunciation": "test pronunciation feedback",
                    "fluency": "test fluency feedback",
                    "content": "test content feedback",
                    "grammar_vocabulary": "test grammar feedback",
                    "action_items": ["test action"],
                    "correction_items": [{
                      "type": "grammar",
                      "original": "test original",
                      "issue": "test issue",
                      "explanation": "test explanation",
                      "suggested": "test suggestion",
                      "severity": "minor"
                    }],
                    "off_topic_items": ["test off-topic item"],
                    "recommended_answer": "test recommended answer",
                    "next_strategy": "test next strategy"
                  },
                  "spoken_word_sequence": [{
                    "index": 0,
                    "segment_index": 1,
                    "word_index": 2,
                    "word": "test",
                    "offset": 100,
                    "duration": 200,
                    "accuracy_score": 91.0,
                    "pronunciation_score": 92.0,
                    "error_type": "None"
                  }]
                }
                """, ExamRequestDTO.AiResultReq.class);
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(examSession()));

        examService.updateExamResult(req);

        ArgumentCaptor<ExamResult> resultCaptor = ArgumentCaptor.forClass(ExamResult.class);
        InOrder callbackOrder = inOrder(examSessionRepository, examResultRepository);
        callbackOrder.verify(examSessionRepository).findById(EXAM_ID);
        callbackOrder.verify(examResultRepository).save(resultCaptor.capture());

        ExamResult savedResult = resultCaptor.getValue();
        ExamResult.ItemFeedback feedback = savedResult.getFeedback();
        ExamResult.CorrectionItem correctionItem = feedback.getCorrectionItems().getFirst();
        ExamResult.SpokenWord spokenWord = savedResult.getSpokenWordSequence().getFirst();

        assertAll(
                () -> assertEquals(EXAM_ID, savedResult.getExamId()),
                () -> assertEquals(USER_ID, savedResult.getUserId()),
                () -> assertNotEquals(savedResult.getExamId(), savedResult.getUserId()),
                () -> assertEquals("mock_exam_003", savedResult.getMockExamId()),
                () -> assertEquals(2, savedResult.getPartNumber()),
                () -> assertEquals(4, savedResult.getQuestionNumber()),
                () -> assertEquals(2, savedResult.getRetryCount()),
                () -> assertEquals(8.5, savedResult.getScore()),
                () -> assertEquals(10.0, savedResult.getMaxScore()),
                () -> assertEquals("synthetic test transcript", savedResult.getTranscript()),
                () -> assertEquals("test summary", feedback.getSummary()),
                () -> assertEquals("Advanced", feedback.getLevel()),
                () -> assertEquals(8.1, feedback.getPronunciationFluencyScore()),
                () -> assertEquals(8.2, feedback.getContentRelevanceScore()),
                () -> assertEquals(8.3, feedback.getFluencyScore()),
                () -> assertEquals(8.4, feedback.getCompletenessScore()),
                () -> assertEquals(8.5, feedback.getProsodyScore()),
                () -> assertEquals(8.6, feedback.getAccuracyScore()),
                () -> assertEquals(List.of("clear structure"), feedback.getStrengths()),
                () -> assertEquals(List.of("test weakness"), feedback.getWeaknesses()),
                () -> assertEquals("test pronunciation feedback", feedback.getPronunciation()),
                () -> assertEquals("test fluency feedback", feedback.getFluency()),
                () -> assertEquals("test content feedback", feedback.getContent()),
                () -> assertEquals("test grammar feedback", feedback.getGrammarVocabulary()),
                () -> assertEquals(List.of("test action"), feedback.getActionItems()),
                () -> assertEquals(List.of("test off-topic item"), feedback.getOffTopicItems()),
                () -> assertEquals("test recommended answer", feedback.getRecommendedAnswer()),
                () -> assertEquals("test next strategy", feedback.getNextStrategy()),
                () -> assertEquals("grammar", correctionItem.getType()),
                () -> assertEquals("test original", correctionItem.getOriginal()),
                () -> assertEquals("test issue", correctionItem.getIssue()),
                () -> assertEquals("test explanation", correctionItem.getExplanation()),
                () -> assertEquals("test suggestion", correctionItem.getSuggested()),
                () -> assertEquals("minor", correctionItem.getSeverity()),
                () -> assertEquals(0, spokenWord.getIndex()),
                () -> assertEquals(1, spokenWord.getSegmentIndex()),
                () -> assertEquals(2, spokenWord.getWordIndex()),
                () -> assertEquals("test", spokenWord.getWord()),
                () -> assertEquals(100L, spokenWord.getOffset()),
                () -> assertEquals(200L, spokenWord.getDuration()),
                () -> assertEquals(91.0, spokenWord.getAccuracyScore()),
                () -> assertEquals(92.0, spokenWord.getPronunciationScore()),
                () -> assertEquals("None", spokenWord.getErrorType())
        );
        verifyNoInteractions(currentUserProvider, redisTemplate, restTemplate);
    }

    @Test
    void overallFeedbackCallbackSavesSameSessionUserIdAndCompletesExam() throws Exception {
        ExamRequestDTO.AiResultReq req = objectMapper.readValue("""
                {
                  "user_id": "ex_callback_001",
                  "mock_exam_id": "mock_exam_003",
                  "suggested_total_score": 170,
                  "level_estimate": "Advanced Mid",
                  "summary": "test overall summary",
                  "overall_feedback": "test overall feedback",
                  "part_feedback": {"part1": "test part feedback"},
                  "strengths": ["test strength"],
                  "weaknesses": ["test weakness"],
                  "recommended_practice": ["test practice"],
                  "part_number": 0,
                  "question_number": 0
                }
                """, ExamRequestDTO.AiResultReq.class);
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(examSession()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        examService.updateExamResult(req);

        ArgumentCaptor<ExamResult> resultCaptor = ArgumentCaptor.forClass(ExamResult.class);
        InOrder callbackOrder = inOrder(examSessionRepository, valueOperations, examResultRepository);
        callbackOrder.verify(examSessionRepository).findById(EXAM_ID);
        callbackOrder.verify(valueOperations).set(
                "exam:status:" + EXAM_ID,
                ExamStatus.COMPLETED.name(),
                1,
                TimeUnit.HOURS
        );
        callbackOrder.verify(examResultRepository).save(resultCaptor.capture());

        ExamResult savedResult = resultCaptor.getValue();
        assertAll(
                () -> assertEquals(EXAM_ID, savedResult.getExamId()),
                () -> assertEquals(USER_ID, savedResult.getUserId()),
                () -> assertNotEquals(savedResult.getExamId(), savedResult.getUserId()),
                () -> assertEquals("mock_exam_003", savedResult.getMockExamId()),
                () -> assertEquals(0, savedResult.getPartNumber()),
                () -> assertEquals(0, savedResult.getQuestionNumber()),
                () -> assertEquals(0, savedResult.getRetryCount()),
                () -> assertEquals(170, savedResult.getTotalScore()),
                () -> assertEquals("Advanced Mid", savedResult.getLevelEstimate()),
                () -> assertEquals("test overall summary", savedResult.getSummary()),
                () -> assertEquals("test overall feedback", savedResult.getOverallFeedback()),
                () -> assertEquals(Map.of("part1", "test part feedback"), savedResult.getPartFeedback()),
                () -> assertEquals(List.of("test strength"), savedResult.getStrengths()),
                () -> assertEquals(List.of("test weakness"), savedResult.getWeaknesses()),
                () -> assertEquals(List.of("test practice"), savedResult.getRecommendedPractice())
        );
        verifyNoInteractions(currentUserProvider, restTemplate);
    }

    @Test
    void missingExamSessionThrowsExistingErrorAndDoesNotSaveResult() throws Exception {
        ExamRequestDTO.AiResultReq req = objectMapper.readValue("""
                {
                  "user_id": "ex_callback_001",
                  "mock_exam_id": "mock_exam_003",
                  "question_number": 5,
                  "retry_count": 0,
                  "score": 7.5
                }
                """, ExamRequestDTO.AiResultReq.class);
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.empty());

        ExamsException exception = assertThrows(
                ExamsException.class,
                () -> examService.updateExamResult(req)
        );

        assertSame(ErrorStatus._EXAM_NOT_FOUND, exception.getCode());
        verify(examSessionRepository).findById(EXAM_ID);
        verifyNoInteractions(examResultRepository, redisTemplate, currentUserProvider, restTemplate);
    }

    @Test
    void feedbackJsonStillDeserializesExternalUserIdAsExamId() throws Exception {
        ExamRequestDTO.AiResultReq req = objectMapper.readValue("""
                {
                  "user_id": "ex_callback_001",
                  "mock_exam_id": "mock_exam_003",
                  "part_number": 3,
                  "question_number": 6,
                  "retry_count": 1,
                  "score": 9.0,
                  "max_score": 10.0
                }
                """, ExamRequestDTO.AiResultReq.class);

        JsonNode serializedRequest = objectMapper.valueToTree(req);
        assertAll(
                () -> assertEquals(EXAM_ID, req.getExamId()),
                () -> assertEquals("mock_exam_003", req.getMockExamId()),
                () -> assertEquals(3, req.getPartNumber()),
                () -> assertEquals(6, req.getQuestionNumber()),
                () -> assertEquals(1, req.getRetryCount()),
                () -> assertTrue(serializedRequest.has("user_id")),
                () -> assertFalse(serializedRequest.has("examId")),
                () -> assertFalse(serializedRequest.has("userId"))
        );
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void multipartAiRequestKeepsExamIdAsExternalUserId() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGetObjectRequest);
        when(presignedGetObjectRequest.url())
                .thenReturn(URI.create("https://example.com/test-audio.wav").toURL());
        when(restTemplate.getForObject(any(URI.class), eq(byte[].class)))
                .thenReturn(new byte[]{1, 2, 3});

        examService.submitAudio(EXAM_ID, 4, 2);

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(AI_SERVER_URL), requestCaptor.capture(), eq(String.class));

        Object rawBody = requestCaptor.getValue().getBody();
        MultiValueMap<String, Object> body = (MultiValueMap<String, Object>)
                assertInstanceOf(MultiValueMap.class, rawBody);
        assertAll(
                () -> assertEquals(EXAM_ID, body.getFirst("user_id")),
                () -> assertNotEquals(USER_ID, body.getFirst("user_id")),
                () -> assertEquals("mock_exam_003", body.getFirst("mock_exam_id")),
                () -> assertEquals(2, body.getFirst("part_number")),
                () -> assertEquals(4, body.getFirst("question_number")),
                () -> assertEquals(2, body.getFirst("retry_count")),
                () -> assertInstanceOf(ByteArrayResource.class, body.getFirst("audio_file"))
        );
        verifyNoInteractions(currentUserProvider, examSessionRepository);
    }

    @Test
    void overallSummaryAiRequestKeepsExamIdAndExistingZeroFlags() {
        ReflectionTestUtils.invokeMethod(
                examService,
                "requestOverallSummary",
                EXAM_ID,
                null
        );

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(AI_SERVER_URL), requestCaptor.capture(), eq(String.class));

        Object rawBody = requestCaptor.getValue().getBody();
        Map<?, ?> body = assertInstanceOf(Map.class, rawBody);
        assertAll(
                () -> assertEquals(EXAM_ID, body.get("user_id")),
                () -> assertNotEquals(USER_ID, body.get("user_id")),
                () -> assertEquals("mock_exam_003", body.get("mock_exam_id")),
                () -> assertEquals(0, body.get("question_number")),
                () -> assertEquals(0, body.get("part_number")),
                () -> assertNull(body.get("retry_count"))
        );
        verifyNoInteractions(currentUserProvider, examSessionRepository);
    }

    private ExamSession examSession() {
        return ExamSession.builder()
                .examId(EXAM_ID)
                .userId(USER_ID)
                .createdAt(LocalDateTime.of(2026, 7, 23, 12, 0))
                .build();
    }
}
