package web.tosunsaeng.domain.answerlearning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import static web.tosunsaeng.domain.answerlearning.LearningModels.*;

/** No provider keys here. SigV4 service=lambda; no redirects or automatic model retry. */
public class LearningLambdaClient {
    private final LearningProperties config;
    private final AwsCredentialsProvider credentials;
    private final ObjectMapper json;
    private final HttpClient http;
    public LearningLambdaClient(LearningProperties config, AwsCredentialsProvider credentials, ObjectMapper json) {
        this(config, credentials, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    LearningLambdaClient(LearningProperties config, AwsCredentialsProvider credentials, ObjectMapper json, HttpClient http) {
        this.config=config; this.credentials=credentials; this.json=json; this.http=http;
    }
    public static Document payload(Submission s) {
        return new Document("submissionId", s.id).append("attempt", s.attempt).append("lessonId", s.lessonId)
                .append("level", s.level).append("contentVersion", s.contentVersion)
                .append("audio", new Document("bucket", s.audio.bucket()).append("key", s.audio.key())
                        .append("etag", s.audio.etag()).append("sha256", s.audio.sha256()))
                .append("checklist", s.checklist);
    }
    public Document submit(Submission s) { return call("POST", "/v1/learning/evaluations", payload(s)); }
    public Document get(Submission s) { return call("GET", "/v1/learning/evaluations/" + s.id + ":" + s.attempt, null); }
    public void purge(String submissionId) {
        LearningService.uuid(submissionId);
        Document proof = call("DELETE", "/v1/learning/evaluations/" + submissionId, null);
        if (!submissionId.equals(proof.getString("submissionId")) || !Boolean.TRUE.equals(proof.getBoolean("purged")))
            throw new LearningFailure(502, "INVALID_GRADING_RESPONSE");
    }
    public Document call(String method, String path, Document body) {
        if (!"DELETE".equals(method) && !config.isGradingEnabled()) throw LearningFailure.unavailable();
        // Cleanup remains available when new grading is switched off. Validate its endpoint independently.
        config.validateLambdaUrl();
        try {
            byte[] bytes = body == null ? new byte[0] : json.writeValueAsBytes(body);
            URI uri = URI.create(config.getLambdaUrl().replaceAll("/$", "") + path);
            var unsigned = SdkHttpFullRequest.builder().uri(uri).method(SdkHttpMethod.valueOf(method))
                    .putHeader("Content-Type", "application/json").putHeader("X-Amz-Content-Sha256", LearningStorage.sha(bytes)).contentStreamProvider(() -> new ByteArrayInputStream(bytes)).build();
            var signed = Aws4Signer.create().sign(unsigned, Aws4SignerParams.builder().awsCredentials(credentials.resolveCredentials())
                    .signingName("lambda").signingRegion(Region.of(config.getRegion())).build());
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds("DELETE".equals(method) ? 5 : 25));
            signed.headers().forEach((key, values) -> {
                if (!key.equalsIgnoreCase("host") && !key.equalsIgnoreCase("content-length")) values.forEach(v -> request.header(key, v));
            });
            var response = http.send(request.method(method, HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] responseBytes;
            try (var stream = response.body()) { responseBytes = stream.readNBytes(131073); }
            if (responseBytes.length > 131072) throw new LearningFailure(502, "INVALID_GRADING_RESPONSE");
            if (response.statusCode() == 404) throw new LearningFailure(404, "GRADING_JOB_NOT_FOUND");
            if (response.statusCode() == 409 || response.statusCode() == 422) throw new LearningFailure(502, "GRADING_REQUEST_REJECTED");
            if (response.statusCode() != 200 && response.statusCode() != 202) throw LearningFailure.unavailable();
            Document envelope = Document.parse(new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8));
            if (!Boolean.TRUE.equals(envelope.getBoolean("isSuccess")) || !(envelope.get("result") instanceof Document result))
                throw new LearningFailure(502, "INVALID_GRADING_RESPONSE");
            return result;
        } catch (LearningFailure e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw LearningFailure.unavailable(); }
        catch (Exception e) { throw LearningFailure.unavailable(); }
    }
}
