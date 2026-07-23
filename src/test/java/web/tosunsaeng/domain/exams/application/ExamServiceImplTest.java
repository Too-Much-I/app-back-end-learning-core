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
import web.tosunsaeng.domain.exams.domain.repository.MockExamRepository;
import web.tosunsaeng.domain.exams.domain.repository.SpeechAceResultRepository;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.global.auth.CurrentUserProvider;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
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
    private ExamSessionRepository examSessionRepository;

    @Mock
    private MockExamRepository mockExamRepository;

    @Mock
    private SpeechAceResultRepository speechAceResultRepository;

    @Mock
    private AzureResultRepository azureResultRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private ExamServiceImpl examService;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(examService, "bucketName", "test-learning-core-bucket");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
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
        when(mockExamRepository.findByMockExamId("mock_exam_003")).thenReturn(java.util.Optional.of(mockExam));

        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGetObjectRequest);
        when(presignedGetObjectRequest.url())
                .thenReturn(URI.create("https://example.com/questions/mock_exam_003/q_1.wav").toURL());
    }

    @Test
    void createExamSessionSavesCurrentUserMappingAfterResponseDataIsPrepared() {
        ExamResponseDTO.CreateSessionResult result = examService.createExamSession();

        ArgumentCaptor<ExamSession> sessionCaptor = ArgumentCaptor.forClass(ExamSession.class);
        String expectedRedisKey = "exam:status:" + result.getExamId();

        InOrder creationOrder = inOrder(
                valueOperations,
                mockExamRepository,
                s3Presigner,
                currentUserProvider,
                examSessionRepository
        );
        creationOrder.verify(valueOperations)
                .set(expectedRedisKey, ExamStatus.PENDING.name(), 1, TimeUnit.HOURS);
        creationOrder.verify(mockExamRepository).findByMockExamId("mock_exam_003");
        creationOrder.verify(s3Presigner).presignGetObject(any(GetObjectPresignRequest.class));
        creationOrder.verify(currentUserProvider).getCurrentUserId();
        creationOrder.verify(examSessionRepository).save(sessionCaptor.capture());

        ExamSession savedSession = sessionCaptor.getValue();
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

        verifyNoInteractions(restTemplate, examResultRepository, speechAceResultRepository, azureResultRepository);
    }
}
