package web.tosunsaeng.domain.challenge;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static web.tosunsaeng.domain.challenge.ChallengeModels.*;

public class ChallengeAiClient {
    public record Reply(boolean accepted, boolean retryable, Instant retryAfter) {}
    private final HttpClient http;
    private final URI endpoint;
    private final String credential;
    public ChallengeAiClient(URI endpoint, String credential) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build(), endpoint, credential);
    }
    ChallengeAiClient(HttpClient http, URI endpoint, String credential) { this.http = http; this.endpoint = endpoint; this.credential = credential; }
    public Reply send(Attempt a, Job j, byte[] audio, Duration budget) {
        long milliseconds = Math.min(15_000, budget.toMillis());
        if (milliseconds <= 0) return new Reply(false, true, null);
        String boundary = "challenge-" + UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofMillis(milliseconds))
                .header("Authorization", "Bearer " + credential).header("X-Challenge-Contract-Version", "v1")
                .header("Idempotency-Key", j.id).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(a, j, audio, boundary))).build();
        CompletableFuture<HttpResponse<byte[]>> pending = http.sendAsync(request, ignored -> new LimitedBody());
        try {
            HttpResponse<byte[]> response = pending.get(milliseconds, TimeUnit.MILLISECONDS);
            int status = response.statusCode();
            if (status == 202) {
                JsonNode n = ChallengeCallback.JSON.readTree(response.body());
                boolean valid = n != null && n.isObject() && n.path("contract_version").isTextual() && "v1".equals(n.path("contract_version").textValue())
                        && n.path("job_id").isTextual() && j.id.equals(n.path("job_id").textValue())
                        && n.path("grading_attempt").isIntegralNumber() && n.path("grading_attempt").canConvertToInt()
                        && j.generation == n.path("grading_attempt").intValue()
                        && n.path("status").isTextual() && "accepted".equals(n.path("status").textValue());
                return new Reply(valid, false, null);
            }
            return new Reply(false, status == 408 || status == 425 || status == 429 || status >= 500,
                    status == 429 ? retryAfter(response.headers().firstValue("Retry-After").orElse(null), Instant.now()) : null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); return new Reply(false, true, null);
        } catch (TimeoutException e) { return new Reply(false, true, null); }
        catch (ExecutionException e) { return new Reply(false, !(e.getCause() instanceof ResponseLimitExceeded), null); }
        catch (Exception invalidResponse) { return new Reply(false, false, null); }
        finally { pending.cancel(true); }
    }
    static byte[] multipart(Attempt a, Job j, byte[] audio, String boundary) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("contract_version", "v1"); fields.put("attempt_id", a.id); fields.put("job_id", j.id);
        fields.put("grading_attempt", Integer.toString(j.generation)); fields.put("question_id", a.question.questionId());
        fields.put("question_number", Integer.toString(a.questionNumber)); fields.put("prompt_ko", a.question.korean());
        fields.put("reference_answer", a.question.referenceAnswer());
        fields.forEach((name, value) -> out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8)));
        out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"audio_file\"; filename=\"audio.m4a\""
                + "\r\nContent-Type: audio/mp4\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.writeBytes(audio); out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8)); return out.toByteArray();
    }
    static Instant retryAfter(String value, Instant now) {
        if (value == null) return null;
        try { return now.plusSeconds(Math.max(0, Long.parseLong(value))); }
        catch (RuntimeException ignored) {
            try { return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); }
            catch (RuntimeException malformed) { return null; }
        }
    }
    /** Bounds memory and includes response-body download in the request's total deadline. */
    static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (bytes.size() + buffer.remaining() > 16_384) { subscription.cancel(); result.completeExceptionally(new ResponseLimitExceeded()); return; }
                byte[] part = new byte[buffer.remaining()]; buffer.get(part); bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(new IllegalStateException("AI transport failure")); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
    private static class ResponseLimitExceeded extends RuntimeException {}
}
