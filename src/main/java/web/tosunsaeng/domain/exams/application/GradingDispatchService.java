package web.tosunsaeng.domain.exams.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GradingDispatchService {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String AI_SERVER_URL = "http://ai-server:8000/evaluations";

    private final S3Presigner s3Presigner;
    private final RestTemplate restTemplate;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    public void dispatchQuestion(QuestionDispatchClaim claim) {
        byte[] audioBytes = restTemplate.getForObject(
                URI.create(generatePresignedGetUrl(claim.fileKey(), Duration.ofMinutes(5))),
                byte[].class
        );
        if (audioBytes == null) {
            throw new IllegalStateException("S3 audio object returned an empty response");
        }

        ByteArrayResource audioResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return "q_%d_r%d.webm".formatted(claim.questionNumber(), claim.retryCount());
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("user_id", claim.examId());
        body.add("mock_exam_id", GradingKeys.MOCK_EXAM_ID);
        body.add("part_number", partNumber(claim.questionNumber()));
        body.add("question_number", claim.questionNumber());
        body.add("retry_count", claim.retryCount());
        body.add("audio_file", audioResource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set(IDEMPOTENCY_KEY_HEADER, claim.jobId());

        restTemplate.postForEntity(
                AI_SERVER_URL,
                new HttpEntity<>(body, headers),
                String.class
        );
    }

    public void dispatchSummary(SummaryDispatchClaim claim) {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", claim.examId());
        body.put("mock_exam_id", GradingKeys.MOCK_EXAM_ID);
        body.put("question_number", 0);
        body.put("part_number", 0);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(IDEMPOTENCY_KEY_HEADER, claim.jobId());

        restTemplate.postForEntity(
                AI_SERVER_URL,
                new HttpEntity<>(body, headers),
                String.class
        );
    }

    private String generatePresignedGetUrl(String fileKey, Duration duration) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(duration)
                .getObjectRequest(getObjectRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    static int partNumber(Integer questionNumber) {
        if (questionNumber == null) return 1;
        if (questionNumber == 0) return 0;
        if (questionNumber <= 2) return 1;
        if (questionNumber <= 4) return 2;
        if (questionNumber <= 7) return 3;
        if (questionNumber <= 10) return 4;
        return 5;
    }
}
