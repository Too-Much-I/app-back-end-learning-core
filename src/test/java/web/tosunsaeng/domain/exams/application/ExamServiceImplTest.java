package web.tosunsaeng.domain.exams.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
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
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;
import web.tosunsaeng.domain.exams.domain.repository.AzureResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
import web.tosunsaeng.domain.exams.domain.repository.SpeechAceResultRepository;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.domain.exams.billing.BillingSagaProperties;
import web.tosunsaeng.global.auth.CurrentUserProvider;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ExamServiceImplTest {

    private static final String LEGACY_USER_ID = "00000000-0000-0000-0000-000000000001";

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

    @Mock
    private BillingExamCreationSaga billingExamCreationSaga;

    @Mock
    private BillingSagaProperties billingSagaProperties;

    @InjectMocks
    private ExamServiceImpl examService;

    private ExamSession assignedSession;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(examService, "bucketName", "test-learning-core-bucket");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(currentUserProvider.getCurrentUserId()).thenReturn(LEGACY_USER_ID);

        Question question = Question.builder()
                .partNumber(1)
                .questionNumber(1)
                .question("Test question")
                .build();
        MockExam mockExam = MockExam.builder()
                .mockExamId("mock_exam_003")
                .title("Test mock exam")
                .questions(List.of(question))
                .build();
        assignedSession = ExamSession.builder()
                .examId("ex_1234567890_0729_0600")
                .userId(LEGACY_USER_ID)
                .createdAt(LocalDateTime.of(2026, 7, 29, 6, 0))
                .mockExamId("mock_exam_003")
                .cycleNumber(1)
                .active(true)
                .build();
        lenient().when(examSessionManager.startNew(LEGACY_USER_ID))
                .thenReturn(new ExamSessionManager.Assignment(assignedSession, mockExam, true));

        lenient().when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGetObjectRequest);
        lenient().when(presignedGetObjectRequest.url())
                .thenReturn(URI.create("https://example.com/questions/mock_exam_003/q_1.wav").toURL());
    }

    @Test
    void createExamSessionSavesCurrentUserMappingAfterResponseDataIsPrepared(CapturedOutput output) {
        ExamResponseDTO.CreateSessionResult result = examService.createExamSession();

        String expectedRedisKey = "exam:status:" + result.getExamId();

        InOrder creationOrder = inOrder(
                valueOperations,
                examSessionManager,
                s3Presigner,
                currentUserProvider,
                examSessionRepository
        );
        creationOrder.verify(currentUserProvider).getCurrentUserId();
        creationOrder.verify(examSessionManager).startNew(LEGACY_USER_ID);
        creationOrder.verify(valueOperations)
                .set(expectedRedisKey, ExamStatus.PENDING.name(), 1, TimeUnit.HOURS);
        creationOrder.verify(s3Presigner).presignGetObject(any(GetObjectPresignRequest.class));

        ExamSession savedSession = assignedSession;
        assertEquals(LEGACY_USER_ID, savedSession.getUserId());
        assertEquals(result.getExamId(), savedSession.getExamId());
        assertNotNull(savedSession.getCreatedAt());
        assertTrue(result.getExamId().matches("^ex_[0-9a-f]{10}_[0-9]{4}_[0-9]{4}$"));

        JsonNode responseJson = new ObjectMapper().valueToTree(result);
        assertEquals(3, responseJson.size());
        assertTrue(responseJson.has("examId"));
        assertTrue(responseJson.has("title"));
        assertTrue(responseJson.has("questions"));
        assertFalse(responseJson.has("userId"));
        assertFalse(responseJson.has("user_id"));
        assertTrue(output.getOut().contains(
                "시험 세션 준비 완료 event=exam.session.ready outcome=success examId=" + result.getExamId()
                        + " mockExamId=mock_exam_003 questionCount=1 durationMs="
        ));
        assertFalse(output.getOut().contains(LEGACY_USER_ID));
        assertFalse(output.getOut().contains("https://example.com"));

        verifyNoInteractions(
                restTemplate,
                examResultRepository,
                examSessionRepository,
                mockExamCatalogService,
                speechAceResultRepository,
                azureResultRepository
        );
    }

    @Test
    void createExamSessionUsesBillingSagaOnlyWhenFeatureFlagIsEnabled() {
        String operationId = "018f6f36-2f42-4bf5-8c17-0be35de4872c";
        when(billingSagaProperties.isCreationSagaEnabled()).thenReturn(true);
        when(billingExamCreationSaga.start(LEGACY_USER_ID, operationId))
                .thenReturn(new ExamSessionManager.Assignment(
                        assignedSession,
                        MockExam.builder()
                                .mockExamId("mock_exam_003")
                                .title("Test mock exam")
                                .questions(List.of(Question.builder()
                                        .partNumber(1)
                                        .questionNumber(1)
                                        .question("Test question")
                                        .build()))
                                .build(),
                        false
                ));

        ExamResponseDTO.CreateSessionResult result = examService.createExamSession(operationId);

        assertEquals(assignedSession.getExamId(), result.getExamId());
        verify(billingExamCreationSaga).start(LEGACY_USER_ID, operationId);
        verify(examSessionManager, never()).startNew(LEGACY_USER_ID);
    }

    @Test
    void createExamSessionPartFourReturnsStoredOpaqueTableContextWithoutTableImage() throws Exception {
        String storedTableImageUrl = "https://cdn.example.com/mock-exam/001/part4/q8.png";
        Map<String, Object> storedTableContext = Map.of(
                "person_name", "Maya Bennett",
                "work_experience", List.of(Map.of(
                        "section_name", "Work Experience",
                        "details", List.of("Kitchen Assistant", "Safety Trainer")
                ))
        );
        Question partFourQuestion = Question.builder()
                .partNumber(4)
                .questionNumber(8)
                .question("Part 4 question")
                .tableImageUrl(storedTableImageUrl)
                .tableContext(storedTableContext)
                .build();
        MockExam mockExam = MockExam.builder()
                .mockExamId("mock_exam_001")
                .title("Part 4 mock exam")
                .questions(List.of(partFourQuestion))
                .build();
        ExamSession session = ExamSession.builder()
                .examId("ex_part4_session")
                .userId(LEGACY_USER_ID)
                .mockExamId("mock_exam_001")
                .active(true)
                .build();
        when(examSessionManager.startNew(LEGACY_USER_ID))
                .thenReturn(new ExamSessionManager.Assignment(session, mockExam, true));
        when(presignedGetObjectRequest.url())
                .thenReturn(URI.create("https://example.com/questions/mock_exam_001/q_8.wav").toURL());

        ExamResponseDTO.CreateSessionResult result = examService.createExamSession();

        ExamResponseDTO.QuestionDTO responseQuestion = result.getQuestions().getFirst();
        JsonNode responseJson = new ObjectMapper().valueToTree(responseQuestion);
        assertEquals(4, responseQuestion.getPart());
        assertEquals(8, responseQuestion.getQuestionNumber());
        assertEquals("Part 4 question", responseQuestion.getText());
        assertEquals(
                "https://example.com/questions/mock_exam_001/q_8.wav",
                responseQuestion.getAudioUrl()
        );
        assertEquals(storedTableContext, responseQuestion.getTableContext());
        assertEquals(
                new ObjectMapper().valueToTree(storedTableContext),
                responseJson.get("tableContext")
        );
        assertFalse(responseJson.has("tableImageUrl"));

        ArgumentCaptor<GetObjectPresignRequest> requestCaptor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(requestCaptor.capture());
        assertEquals(
                "questions/mock_exam_001/q_8.wav",
                requestCaptor.getValue().getObjectRequest().key()
        );
    }

    @Test
    void createExamSessionPartFourWithoutTableContextUsesCatalogConfigurationError() {
        Question partFourQuestion = Question.builder()
                .partNumber(4)
                .questionNumber(8)
                .tableImageUrl("https://cdn.example.com/legacy-table-image.png")
                .build();
        MockExam mockExam = MockExam.builder()
                .mockExamId("mock_exam_001")
                .title("Part 4 mock exam")
                .questions(List.of(partFourQuestion))
                .build();
        ExamSession session = ExamSession.builder()
                .examId("ex_part4_missing_context")
                .userId(LEGACY_USER_ID)
                .mockExamId("mock_exam_001")
                .active(true)
                .build();
        when(examSessionManager.startNew(LEGACY_USER_ID))
                .thenReturn(new ExamSessionManager.Assignment(session, mockExam, true));

        ExamsException exception = assertThrows(
                ExamsException.class,
                () -> examService.createExamSession()
        );

        assertEquals(ErrorStatus._EXAM_CATALOG_CONFIGURATION_ERROR, exception.getCode());
        verifyNoInteractions(s3Presigner);
    }

    @Test
    void consecutiveStartsReturnDifferentExamIdsAndInitializeEachRedisStatus() throws Exception {
        Question selectedQuestion = Question.builder()
                .partNumber(1)
                .questionNumber(1)
                .question("Selected question")
                .build();
        MockExam selectedPaper = MockExam.builder()
                .mockExamId("mock_exam_002")
                .title("Selected paper")
                .questions(List.of(selectedQuestion))
                .build();
        ExamSession firstSession = ExamSession.builder()
                .examId("ex_new_first_002")
                .userId(LEGACY_USER_ID)
                .mockExamId("mock_exam_002")
                .cycleNumber(1)
                .active(true)
                .build();
        ExamSession secondSession = ExamSession.builder()
                .examId("ex_new_second_002")
                .userId(LEGACY_USER_ID)
                .mockExamId("mock_exam_002")
                .cycleNumber(1)
                .active(true)
                .build();
        when(examSessionManager.startNew(LEGACY_USER_ID)).thenReturn(
                new ExamSessionManager.Assignment(firstSession, selectedPaper, true),
                new ExamSessionManager.Assignment(secondSession, selectedPaper, true)
        );

        when(presignedGetObjectRequest.url()).thenReturn(
                URI.create("https://example.com/first.wav").toURL(),
                URI.create("https://example.com/second.wav").toURL()
        );

        ExamResponseDTO.CreateSessionResult first = examService.createExamSession();
        ExamResponseDTO.CreateSessionResult second = examService.createExamSession();

        ArgumentCaptor<GetObjectPresignRequest> requestCaptor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner, times(2)).presignGetObject(requestCaptor.capture());
        assertEquals("ex_new_first_002", first.getExamId());
        assertEquals("ex_new_second_002", second.getExamId());
        assertFalse(first.getExamId().equals(second.getExamId()));
        assertEquals("https://example.com/first.wav", first.getQuestions().getFirst().getAudioUrl());
        assertEquals("https://example.com/second.wav", second.getQuestions().getFirst().getAudioUrl());
        assertEquals(List.of(
                        "questions/mock_exam_002/q_1.wav",
                        "questions/mock_exam_002/q_1.wav"
                ), requestCaptor.getAllValues().stream()
                        .map(request -> request.getObjectRequest().key())
                        .toList());
        verify(examSessionManager, times(2)).startNew(LEGACY_USER_ID);
        verify(valueOperations).set(
                "exam:status:ex_new_first_002", ExamStatus.PENDING.name(), 1, TimeUnit.HOURS);
        verify(valueOperations).set(
                "exam:status:ex_new_second_002", ExamStatus.PENDING.name(), 1, TimeUnit.HOURS);
        verifyNoInteractions(examSessionRepository, mockExamCatalogService);
    }

    @Test
    void newSessionDoesNotReuseOrRecoverPreviousRedisState() {

        examService.createExamSession();

        verify(redisTemplate, never()).hasKey(any());
        verify(gradingService, never()).calculateAndCacheOverallStatus(any());
        verify(valueOperations).set(
                "exam:status:" + assignedSession.getExamId(),
                ExamStatus.PENDING.name(),
                1,
                TimeUnit.HOURS
        );
    }

    @Test
    void selectedPaperBuildsQuestionAndPartThreeGuideS3Paths() {
        MockExam selectedPaper = MockExam.builder()
                .mockExamId("mock_exam_002")
                .questions(List.of(Question.builder()
                        .partNumber(3)
                        .questionNumber(5)
                        .build()))
                .build();
        ExamSession selectedSession = ExamSession.builder()
                .examId("ex_selected_002")
                .userId(LEGACY_USER_ID)
                .mockExamId("mock_exam_002")
                .active(true)
                .build();
        when(examSessionManager.startNew(LEGACY_USER_ID))
                .thenReturn(new ExamSessionManager.Assignment(selectedSession, selectedPaper, true));

        examService.createExamSession();

        ArgumentCaptor<GetObjectPresignRequest> requestCaptor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner, times(2)).presignGetObject(requestCaptor.capture());
        assertEquals(List.of(
                        "questions/mock_exam_002/q_5.wav",
                        "questions/mock_exam_002/part3_intro.wav"
                ), requestCaptor.getAllValues().stream()
                        .map(request -> request.getObjectRequest().key())
                        .toList());
    }
}
