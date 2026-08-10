package web.tosunsaeng.domain.exams.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import web.tosunsaeng.global.config.GradingProperties;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GradingDispatchService {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String CLIENT_SOURCE_FIELD = "client_source";
    static final String APP_CLIENT_SOURCE = "app";
    private static final String EVALUATIONS_PATH = "evaluations";

    private final S3Presigner s3Presigner;
    private final RestTemplate restTemplate;
    private final GradingProperties gradingProperties;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    public void dispatchQuestion(QuestionDispatchClaim claim) {
        byte[] audioBytes = downloadAudio(claim);

        ByteArrayResource audioResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return "q_%d_r%d.webm".formatted(claim.questionNumber(), claim.retryCount());
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("user_id", claim.examId());
        body.add("mock_exam_id", GradingKeys.effectiveMockExamId(claim.mockExamId()));
        body.add("part_number", partNumber(claim.questionNumber()));
        body.add("question_number", claim.questionNumber());
        body.add("retry_count", claim.retryCount());
        body.add(CLIENT_SOURCE_FIELD, APP_CLIENT_SOURCE);
        body.add("audio_file", audioResource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set(IDEMPOTENCY_KEY_HEADER, claim.jobId());

        postEvaluation(new HttpEntity<>(body, headers), claim.jobId(), "question");
    }

    public void dispatchSummary(SummaryDispatchClaim claim) {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", claim.examId());
        body.put("mock_exam_id", GradingKeys.effectiveMockExamId(claim.mockExamId()));
        body.put("question_number", 0);
        body.put("part_number", 0);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(IDEMPOTENCY_KEY_HEADER, claim.jobId());

        postEvaluation(new HttpEntity<>(body, headers), claim.jobId(), "summary");
    }

    private byte[] downloadAudio(QuestionDispatchClaim claim) {
        long startedAt = System.nanoTime();
        try {
            byte[] audioBytes = restTemplate.getForObject(
                    URI.create(generatePresignedGetUrl(claim.fileKey(), Duration.ofMinutes(5))),
                    byte[].class
            );
            if (audioBytes == null) {
                throw new IllegalStateException("S3 audio object returned an empty response");
            }
            log.debug(
                    "S3 음성 파일 다운로드 완료 event=grading.dispatch.stage "
                            + "outcome=success stage=s3_download "
                            + "dispatchType=question jobId={} durationMs={}",
                    claim.jobId(), elapsedMillis(startedAt)
            );
            return audioBytes;
        } catch (RuntimeException downloadFailure) {
            throw GradingDispatchException.at(
                    GradingDispatchException.Stage.S3_DOWNLOAD,
                    startedAt,
                    downloadFailure
            );
        }
    }

    private void postEvaluation(HttpEntity<?> request, String jobId, String dispatchType) {
        long startedAt = System.nanoTime();
        try {
            restTemplate.postForEntity(
                    aiEvaluationUri(gradingProperties.aiServerUrl()),
                    request,
                    String.class
            );
            log.debug(
                    "AI 채점 요청 전송 완료 event=grading.dispatch.stage "
                            + "outcome=success stage=ai_post "
                            + "dispatchType={} jobId={} durationMs={}",
                    dispatchType, jobId, elapsedMillis(startedAt)
            );
        } catch (RuntimeException postFailure) {
            throw GradingDispatchException.at(
                    GradingDispatchException.Stage.AI_POST,
                    startedAt,
                    postFailure
            );
        }
    }

    static URI aiEvaluationUri(URI aiServerUrl) {
        String baseUrl = aiServerUrl.toString();
        URI directoryUri = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        return directoryUri.resolve(EVALUATIONS_PATH);
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

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
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
