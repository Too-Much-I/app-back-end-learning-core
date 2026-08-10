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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.repository.AzureResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
import web.tosunsaeng.domain.exams.domain.repository.SpeechAceResultRepository;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.auth.CurrentUserProvider;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class FeedbackCallbackServiceTest {

    private static final String EXAM_ID = "ex_callback_001";
    private static final String USER_ID = "00000000-0000-0000-0000-000000000042";

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
        lenient().when(examSessionRepository.findById(EXAM_ID))
                .thenReturn(Optional.of(examSession()));
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
        callbackOrder.verify(examResultRepository).insert(resultCaptor.capture());

        ExamResult savedResult = resultCaptor.getValue();
        ExamResult.ItemFeedback feedback = savedResult.getFeedback();
        ExamResult.CorrectionItem correctionItem = feedback.getCorrectionItems().getFirst();
        ExamResult.SpokenWord spokenWord = savedResult.getSpokenWordSequence().getFirst();

        assertAll(
                () -> assertEquals(EXAM_ID, savedResult.getExamId()),
                () -> assertEquals("feedback:" + EXAM_ID + ":4:2", savedResult.getId()),
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
        verify(gradingService).completeQuestion(EXAM_ID, 4, 2);
        verify(gradingService).ensureSummaryStartedIfReady(EXAM_ID);
        verify(examSessionManager, never()).completeIfIncomplete(any());
        verifyNoInteractions(examSummaryRepository, currentUserProvider, redisTemplate, restTemplate);
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
        examService.updateExamResult(req);

        ArgumentCaptor<ExamSummary> summaryCaptor = ArgumentCaptor.forClass(ExamSummary.class);
        InOrder callbackOrder = inOrder(examSessionRepository, examSummaryRepository, examSessionManager);
        callbackOrder.verify(examSessionRepository).findById(EXAM_ID);
        callbackOrder.verify(examSummaryRepository).insert(summaryCaptor.capture());
        callbackOrder.verify(examSessionManager).completeIfIncomplete(EXAM_ID);

        ExamSummary savedSummary = summaryCaptor.getValue();
        assertAll(
                () -> assertEquals(EXAM_ID, savedSummary.getExamId()),
                () -> assertEquals("summary:" + EXAM_ID + ":v1", savedSummary.getId()),
                () -> assertEquals(USER_ID, savedSummary.getUserId()),
                () -> assertNotEquals(savedSummary.getExamId(), savedSummary.getUserId()),
                () -> assertEquals("mock_exam_003", savedSummary.getMockExamId()),
                () -> assertEquals(170, savedSummary.getTotalScore()),
                () -> assertEquals("Advanced Mid", savedSummary.getLevelEstimate()),
                () -> assertEquals("test overall summary", savedSummary.getSummary()),
                () -> assertEquals("test overall feedback", savedSummary.getOverallFeedback()),
                () -> assertEquals(Map.of("part1", "test part feedback"), savedSummary.getPartFeedback()),
                () -> assertEquals(List.of("test strength"), savedSummary.getStrengths()),
                () -> assertEquals(List.of("test weakness"), savedSummary.getWeaknesses()),
                () -> assertEquals(List.of("test practice"), savedSummary.getRecommendedPractice())
        );
        verify(gradingService).completeSummary(EXAM_ID);
        verify(gradingService).calculateAndCacheOverallStatus(EXAM_ID);
        verifyNoInteractions(currentUserProvider, restTemplate);
    }

    @Test
    void summaryStorageFailureDoesNotCompleteSession() throws Exception {
        ExamRequestDTO.AiResultReq req = objectMapper.readValue("""
                {
                  "user_id": "ex_callback_001",
                  "mock_exam_id": "mock_exam_002",
                  "suggested_total_score": 170,
                  "question_number": 0
                }
                """, ExamRequestDTO.AiResultReq.class);
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(examSession()));
        doThrow(new IllegalStateException("synthetic summary storage failure"))
                .when(examSummaryRepository).insert(any(ExamSummary.class));

        assertThrows(IllegalStateException.class, () -> examService.updateExamResult(req));

        verify(examSessionManager, never()).completeIfIncomplete(EXAM_ID);
        verify(gradingService, never()).completeSummary(EXAM_ID);
    }

    @Test
    void callbackPersistsSessionMockExamIdInsteadOfMismatchedPayload() throws Exception {
        ExamRequestDTO.AiResultReq req = objectMapper.readValue("""
                {
                  "user_id": "ex_callback_001",
                  "mock_exam_id": "mock_exam_999",
                  "question_number": 4,
                  "retry_count": 0,
                  "score": 8.0
                }
                """, ExamRequestDTO.AiResultReq.class);
        ExamSession selectedSession = ExamSession.builder()
                .examId(EXAM_ID)
                .userId(USER_ID)
                .mockExamId("mock_exam_002")
                .active(true)
                .build();
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(selectedSession));

        examService.updateExamResult(req);

        ArgumentCaptor<ExamResult> resultCaptor = ArgumentCaptor.forClass(ExamResult.class);
        verify(examResultRepository).insert(resultCaptor.capture());
        assertEquals("mock_exam_002", resultCaptor.getValue().getMockExamId());
    }

    @Test
    void missingExamSessionThrowsExistingErrorAndDoesNotSaveResult(CapturedOutput output) throws Exception {
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
        assertTrue(output.getOut().contains(
                "채점 콜백 시험 세션 조회 실패 "
                        + "event=grading.callback outcome=rejected reason=exam_not_found "
                        + "callbackType=feedback examId=" + EXAM_ID
                        + " jobId=question:" + EXAM_ID + ":5:0"
        ));
        assertFalse(output.getOut().contains(USER_ID));
        verify(examSessionRepository).findById(EXAM_ID);
        verifyNoInteractions(
                examResultRepository,
                examSummaryRepository,
                redisTemplate,
                currentUserProvider,
                restTemplate
        );
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
    void duplicateFeedbackCallbackStoresOneResultAndDoesNotRetriggerSummary() throws Exception {
        ExamRequestDTO.AiResultReq req = objectMapper.readValue("""
                {
                  "user_id": "ex_callback_001",
                  "mock_exam_id": "mock_exam_003",
                  "question_number": 4,
                  "retry_count": 0,
                  "score": 8.0
                }
                """, ExamRequestDTO.AiResultReq.class);
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(examSession()));
        when(examResultRepository.existsByExamIdAndQuestionNumberAndRetryCountIn(
                eq(EXAM_ID), eq(4), any()))
                .thenReturn(false, true);

        examService.updateExamResult(req);
        examService.updateExamResult(req);

        verify(examResultRepository, times(1)).insert(any(ExamResult.class));
        verify(gradingService, times(2)).completeQuestion(EXAM_ID, 4, 0);
        verify(gradingService, times(2)).ensureSummaryStartedIfReady(EXAM_ID);
    }

    @Test
    void duplicateSpeechAceCallbackStoresOneResult() throws Exception {
        ExamRequestDTO.SpeechAceReq req = objectMapper.readValue("""
                {
                  "user_id": "ex_callback_001",
                  "question_number": 4,
                  "retry_count": 0,
                  "speechace_result": {"score": 90}
                }
                """, ExamRequestDTO.SpeechAceReq.class);
        when(speechAceResultRepository.existsByExamIdAndQuestionNumberAndRetryCountIn(
                eq(EXAM_ID), eq(4), any()))
                .thenReturn(false, true);

        examService.saveSpeechAceResult(req);
        examService.saveSpeechAceResult(req);

        verify(speechAceResultRepository, times(1)).insert(any(web.tosunsaeng.domain.exams.domain.entity.SpeechAceResult.class));
    }

    @Test
    void duplicateAzureCallbackStoresOneResult() {
        Map<String, Object> payload = Map.of(
                "metadata", Map.of(
                        "user_id", EXAM_ID,
                        "question_number", 4,
                        "retry_count", 0
                )
        );
        when(azureResultRepository.existsByExamIdAndQuestionNumberAndRetryCountIn(
                eq(EXAM_ID), eq(4), any()))
                .thenReturn(false, true);

        examService.processAzureCallback(payload);
        examService.processAzureCallback(payload);

        verify(azureResultRepository, times(1)).insert(any(web.tosunsaeng.domain.exams.domain.entity.AzureResult.class));
    }

    @Test
    void duplicateSummaryCallbackStoresOneResult() throws Exception {
        ExamRequestDTO.AiResultReq req = objectMapper.readValue("""
                {
                  "user_id": "ex_callback_001",
                  "mock_exam_id": "mock_exam_003",
                  "suggested_total_score": 170,
                  "question_number": 0
                }
                """, ExamRequestDTO.AiResultReq.class);
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(examSession()));
        when(examSummaryRepository.existsById("summary:" + EXAM_ID + ":v1"))
                .thenReturn(false, true);

        examService.updateExamResult(req);
        examService.updateExamResult(req);

        verify(examSummaryRepository, times(1)).insert(any(ExamSummary.class));
        verify(examSessionManager, times(2)).completeIfIncomplete(EXAM_ID);
        verify(gradingService, times(2)).completeSummary(EXAM_ID);
        verify(gradingService, times(2)).calculateAndCacheOverallStatus(EXAM_ID);
    }

    @Test
    void abandonedQuestionCallbackIsIdempotentNoOp() throws Exception {
        ExamRequestDTO.AiResultReq req = objectMapper.readValue("""
                {
                  "user_id": "ex_callback_001",
                  "question_number": 4,
                  "retry_count": 2,
                  "score": 8.0
                }
                """, ExamRequestDTO.AiResultReq.class);
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(abandonedSession()));

        examService.updateExamResult(req);
        examService.updateExamResult(req);

        verify(examSessionRepository, times(2)).findById(EXAM_ID);
        verifyNoInteractions(
                examResultRepository,
                examSummaryRepository,
                gradingService,
                examSessionManager
        );
    }

    @Test
    void abandonedSummaryCallbackDoesNotStoreOrCompleteExam() throws Exception {
        ExamRequestDTO.AiResultReq req = objectMapper.readValue("""
                {
                  "user_id": "ex_callback_001",
                  "suggested_total_score": 170,
                  "question_number": 0
                }
                """, ExamRequestDTO.AiResultReq.class);
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(abandonedSession()));

        examService.updateExamResult(req);

        verifyNoInteractions(
                examResultRepository,
                examSummaryRepository,
                gradingService,
                examSessionManager
        );
    }

    @Test
    void abandonedSpeechAceAndAzureCallbacksDoNotStoreResults() throws Exception {
        ExamRequestDTO.SpeechAceReq speechAceReq = objectMapper.readValue("""
                {
                  "user_id": "ex_callback_001",
                  "question_number": 4,
                  "retry_count": 0,
                  "speechace_result": {"score": 90}
                }
                """, ExamRequestDTO.SpeechAceReq.class);
        Map<String, Object> azurePayload = Map.of(
                "metadata", Map.of(
                        "user_id", EXAM_ID,
                        "question_number", 4,
                        "retry_count", 0
                )
        );
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(abandonedSession()));

        examService.saveSpeechAceResult(speechAceReq);
        examService.processAzureCallback(azurePayload);

        verifyNoInteractions(speechAceResultRepository, azureResultRepository);
    }

    @Test
    void malformedAzureMetadataLogsOnlySafeClassification(CapturedOutput output) {
        Map<String, Object> payload = Map.of(
                "metadata", Map.of(
                        "user_id", EXAM_ID,
                        "question_number", "not-an-integer",
                        "sensitive_field", "should-not-be-logged"
                )
        );

        assertThrows(ClassCastException.class, () -> examService.processAzureCallback(payload));

        assertAll(
                () -> assertTrue(output.getOut().contains(
                        "Azure 콜백 메타데이터 검증 실패 "
                                + "event=grading.callback outcome=rejected reason=invalid_metadata "
                                + "callbackType=azure errorType=java.lang.ClassCastException"
                )),
                () -> assertFalse(output.getOut().contains("not-an-integer")),
                () -> assertFalse(output.getOut().contains("should-not-be-logged")),
                () -> assertFalse(output.getOut().contains("sensitive_field"))
        );
        verifyNoInteractions(examSessionRepository, azureResultRepository);
    }

    private ExamSession examSession() {
        return ExamSession.builder()
                .examId(EXAM_ID)
                .userId(USER_ID)
                .createdAt(LocalDateTime.of(2026, 7, 23, 12, 0))
                .active(true)
                .status(ExamSessionStatus.IN_PROGRESS)
                .build();
    }

    private ExamSession abandonedSession() {
        return ExamSession.builder()
                .examId(EXAM_ID)
                .userId(USER_ID)
                .createdAt(LocalDateTime.of(2026, 7, 23, 12, 0))
                .active(false)
                .status(ExamSessionStatus.ABANDONED)
                .build();
    }
}
