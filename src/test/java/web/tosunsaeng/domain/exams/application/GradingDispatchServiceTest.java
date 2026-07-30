package web.tosunsaeng.domain.exams.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradingDispatchServiceTest {

    private static final String EXAM_ID = "ex_dispatch_001";
    private static final String AI_SERVER_URL = "http://ai-server:8000/evaluations";

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private PresignedGetObjectRequest presignedGetObjectRequest;

    private GradingDispatchService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new GradingDispatchService(s3Presigner, restTemplate);
        ReflectionTestUtils.setField(service, "bucketName", "test-learning-core-bucket");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void questionDispatchKeepsMultipartContractAndUsesStableIdempotencyKey() throws Exception {
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGetObjectRequest);
        when(presignedGetObjectRequest.url())
                .thenReturn(URI.create("https://example.com/test-audio.wav").toURL());
        when(restTemplate.getForObject(any(URI.class), eq(byte[].class)))
                .thenReturn(new byte[]{1, 2, 3});
        QuestionDispatchClaim claim = new QuestionDispatchClaim(
                "question:" + EXAM_ID + ":4:2",
                1,
                Instant.parse("2026-07-28T00:00:00Z"),
                EXAM_ID,
                4,
                2,
                "temp/" + EXAM_ID + "/q_4_r2.wav",
                "mock_exam_002"
        );

        service.dispatchQuestion(claim);

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(AI_SERVER_URL), requestCaptor.capture(), eq(String.class));
        MultiValueMap<String, Object> body = (MultiValueMap<String, Object>)
                assertInstanceOf(MultiValueMap.class, requestCaptor.getValue().getBody());
        assertAll(
                () -> assertEquals(EXAM_ID, body.getFirst("user_id")),
                () -> assertNotEquals("00000000-0000-0000-0000-000000000001", body.getFirst("user_id")),
                () -> assertEquals("mock_exam_002", body.getFirst("mock_exam_id")),
                () -> assertEquals(2, body.getFirst("part_number")),
                () -> assertEquals(4, body.getFirst("question_number")),
                () -> assertEquals(2, body.getFirst("retry_count")),
                () -> assertInstanceOf(ByteArrayResource.class, body.getFirst("audio_file")),
                () -> assertEquals(
                        "question:" + EXAM_ID + ":4:2",
                        requestCaptor.getValue().getHeaders().getFirst("Idempotency-Key")
                )
        );
    }

    @Test
    @SuppressWarnings("rawtypes")
    void summaryDispatchKeepsExistingBodyAndUsesStableIdempotencyKey() {
        service.dispatchSummary(new SummaryDispatchClaim(
                "summary:" + EXAM_ID + ":v1",
                1,
                Instant.parse("2026-07-28T00:00:00Z"),
                EXAM_ID,
                "mock_exam_002"
        ));

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(AI_SERVER_URL), requestCaptor.capture(), eq(String.class));
        Map<?, ?> body = assertInstanceOf(Map.class, requestCaptor.getValue().getBody());
        assertAll(
                () -> assertEquals(EXAM_ID, body.get("user_id")),
                () -> assertEquals("mock_exam_002", body.get("mock_exam_id")),
                () -> assertEquals(0, body.get("question_number")),
                () -> assertEquals(0, body.get("part_number")),
                () -> assertNull(body.get("retry_count")),
                () -> assertEquals(
                        "summary:" + EXAM_ID + ":v1",
                        requestCaptor.getValue().getHeaders().getFirst("Idempotency-Key")
                )
        );
    }
}
