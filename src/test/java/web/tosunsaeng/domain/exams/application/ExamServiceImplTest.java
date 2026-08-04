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
import web.tosunsaeng.global.auth.CurrentUserProvider;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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
        when(examSessionManager.findOrCreate(LEGACY_USER_ID))
                .thenReturn(new ExamSessionManager.Assignment(assignedSession, mockExam, true));

        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGetObjectRequest);
        when(presignedGetObjectRequest.url())
                .thenReturn(URI.create("https://example.com/questions/mock_exam_003/q_1.wav").toURL());
    }

    @Test
    void createExamSessionSavesCurrentUserMappingAfterResponseDataIsPrepared() {
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
        creationOrder.verify(examSessionManager).findOrCreate(LEGACY_USER_ID);
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
    void reusableSessionKeepsExamIdAndRefreshesSelectedPaperPresignedUrls() throws Exception {
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
        ExamSession existing = ExamSession.builder()
                .examId("ex_existing_002")
                .userId(LEGACY_USER_ID)
                .mockExamId("mock_exam_002")
                .cycleNumber(1)
                .active(true)
                .build();
        when(examSessionManager.findOrCreate(LEGACY_USER_ID))
                .thenReturn(new ExamSessionManager.Assignment(existing, selectedPaper, false));
        when(redisTemplate.hasKey("exam:status:ex_existing_002")).thenReturn(true);

        when(presignedGetObjectRequest.url()).thenReturn(
                URI.create("https://example.com/first.wav").toURL(),
                URI.create("https://example.com/second.wav").toURL()
        );

        ExamResponseDTO.CreateSessionResult first = examService.createExamSession();
        ExamResponseDTO.CreateSessionResult second = examService.createExamSession();

        ArgumentCaptor<GetObjectPresignRequest> requestCaptor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner, times(2)).presignGetObject(requestCaptor.capture());
        assertEquals("ex_existing_002", first.getExamId());
        assertEquals(first.getExamId(), second.getExamId());
        assertEquals("https://example.com/first.wav", first.getQuestions().getFirst().getAudioUrl());
        assertEquals("https://example.com/second.wav", second.getQuestions().getFirst().getAudioUrl());
        assertEquals(List.of(
                        "questions/mock_exam_002/q_1.wav",
                        "questions/mock_exam_002/q_1.wav"
                ), requestCaptor.getAllValues().stream()
                        .map(request -> request.getObjectRequest().key())
                        .toList());
        verify(examSessionManager, times(2)).findOrCreate(LEGACY_USER_ID);
        verifyNoInteractions(examSessionRepository, mockExamCatalogService);
    }

    @Test
    void reusableSessionRecoversMissingRedisStatusFromMongoState() {
        when(examSessionManager.findOrCreate(LEGACY_USER_ID))
                .thenReturn(new ExamSessionManager.Assignment(
                        assignedSession,
                        MockExam.builder()
                                .mockExamId("mock_exam_003")
                                .questions(List.of(Question.builder()
                                        .partNumber(1)
                                        .questionNumber(1)
                                        .build()))
                                .build(),
                        false
                ));
        when(redisTemplate.hasKey("exam:status:" + assignedSession.getExamId())).thenReturn(false);
        when(gradingService.calculateAndCacheOverallStatus(assignedSession.getExamId()))
                .thenReturn(ExamStatus.PROCESSING);

        examService.createExamSession();

        verify(gradingService).calculateAndCacheOverallStatus(assignedSession.getExamId());
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
        when(examSessionManager.findOrCreate(LEGACY_USER_ID))
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
