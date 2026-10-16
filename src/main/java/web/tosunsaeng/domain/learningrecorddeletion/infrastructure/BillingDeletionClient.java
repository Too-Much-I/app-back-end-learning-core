package web.tosunsaeng.domain.learningrecorddeletion.infrastructure;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import web.tosunsaeng.domain.exams.billing.BillingWorkloadTokenIssuer;
import web.tosunsaeng.domain.learningrecorddeletion.domain.BillingDeletionDelivery.Failure;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.io.ByteArrayOutputStream;

/** No redirect, bounded response, no raw diagnostics or retained response text. */
public class BillingDeletionClient {
    public record Result(boolean acknowledged, Failure failure, Instant retryAfter) {
        public static Result retry() { return new Result(false, null, null); }
    }
    private final HttpClient client;
    private final URI endpoint;
    private final BillingWorkloadTokenIssuer issuer;
    private final Clock clock;
    public BillingDeletionClient(String origin, BillingWorkloadTokenIssuer issuer, Clock clock) {
        URI uri;
        try { uri = URI.create(origin); } catch (RuntimeException invalid) { throw new IllegalStateException("Invalid deletion origin"); }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !(uri.getRawPath().isEmpty() || uri.getRawPath().equals("/")))
            throw new IllegalStateException("Deletion requires an exact HTTPS origin");
        endpoint = uri.resolve("/internal/v1/exam-deletions/events"); this.issuer = issuer; this.clock = clock;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public Result send(String payload) {
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json").header("Accept-Encoding", "identity")
                    .header("Authorization", issuer.authorization(BillingWorkloadTokenIssuer.Purpose.EXAM_DELETION_EVENTS))
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
            pending = client.sendAsync(request, ignored -> new LimitedBody());
            var response = pending.get(5, TimeUnit.SECONDS);
            return classify(response.statusCode(), response.headers().map(), response.body(), clock.instant());
        } catch (Exception failure) {
            if (pending != null) pending.cancel(true);
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            if (pending == null && failure instanceof IllegalStateException)
                return new Result(false, Failure.AUTHENTICATION_FAILED, null);
            for (Throwable c = failure; c != null; c = c.getCause())
                if (c instanceof TooLarge) return new Result(false, Failure.CONTRACT_ERROR, null);
            return Result.retry();
        }
    }
    public static Result classify(int status, Map<String, List<String>> headers, byte[] body, Instant now) {
        var enc = values(headers, "content-encoding");
        if (body.length > 16384 || (!enc.isEmpty() && (enc.size() != 1 || !enc.getFirst().equalsIgnoreCase("identity"))))
            return new Result(false, Failure.CONTRACT_ERROR, null);
        if (status == 204) {
            // HttpClient may suppress a body forbidden by status; reject contradictory framing too.
            var lengths = values(headers, "content-length");
            boolean empty = body.length == 0 && values(headers,"transfer-encoding").isEmpty()
                    && (lengths.isEmpty() || lengths.size() == 1 && lengths.getFirst().equals("0"));
            return new Result(empty, empty ? null : Failure.CONTRACT_ERROR, null);
        }
        if (status == 401 || status == 403) return new Result(false, Failure.AUTHENTICATION_FAILED, null);
        if (status == 503) {
            JsonNode root = null;
            try { root = mapper().readTree(body); } catch (Exception invalid) {
                // Duplicate/trailing JSON is a contract failure, not a proxy outage.
                try {
                    if (new ObjectMapper().readTree(body) != null) return new Result(false, Failure.CONTRACT_ERROR, null);
                } catch (Exception notJson) { /* Proxy/non-JSON failure is temporary. */ }
            }
            if (root != null) {
                Set<String> fields = new HashSet<>(); root.fieldNames().forEachRemaining(fields::add);
                if (!root.isObject() || !fields.equals(Set.of("code", "message", "retryable", "correlationId"))
                        || !root.path("code").isTextual() || !"BILLING_TEMPORARILY_UNAVAILABLE".equals(root.path("code").asText())
                        || !"Billing is temporarily unavailable.".equals(root.path("message").asText())
                        || !root.path("retryable").isBoolean() || !root.path("retryable").booleanValue()
                        || !root.path("correlationId").isTextual() || !root.path("correlationId").textValue().matches("[A-Za-z0-9._-]{1,128}"))
                    return new Result(false, Failure.CONTRACT_ERROR, null);
            }
        }
        if (status == 408 || status == 429 || status >= 500 && status <= 599)
            return new Result(false, null, status == 429 || status == 503 ? retryAfter(headers, now) : null);
        Failure failure = Failure.CONTRACT_ERROR;
        if (status == 409) {
            try {
                String code = mapper().readTree(body).path("code").asText();
                if (code.equals("EVENT_ID_CONFLICT")) failure = Failure.EVENT_ID_CONFLICT;
                if (code.equals("DELETION_TARGET_UNRESOLVED")) failure = Failure.TARGET_UNRESOLVED;
            } catch (Exception ignored) { }
        }
        return new Result(false, failure, null);
    }
    private static ObjectMapper mapper() {
        return JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    }
    private static List<String> values(Map<String, List<String>> headers, String key) {
        return headers.entrySet().stream().filter(e -> key.equalsIgnoreCase(e.getKey())).flatMap(e -> e.getValue().stream()).toList();
    }
    public static Instant retryAfter(Map<String, List<String>> headers, Instant now) {
        List<String> values = values(headers, "retry-after");
        if (values.size() != 1) return null;
        String raw = values.getFirst();
        try {
            if (raw.matches("[0-9]+")) return now.plusSeconds(Long.parseLong(raw));
            return ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (RuntimeException invalid) { return null; }
    }
    private static final class TooLarge extends RuntimeException { }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription s) { subscription = s; s.request(1); }
        public void onNext(List<ByteBuffer> chunks) {
            for (ByteBuffer b : chunks) {
                if (bytes.size() + b.remaining() > 16384) { subscription.cancel(); result.completeExceptionally(new TooLarge()); return; }
                byte[] part = new byte[b.remaining()]; b.get(part); bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        public void onError(Throwable t) { result.completeExceptionally(t); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
