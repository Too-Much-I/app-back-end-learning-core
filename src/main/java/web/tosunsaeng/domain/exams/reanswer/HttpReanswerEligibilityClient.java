package web.tosunsaeng.domain.exams.reanswer;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import web.tosunsaeng.domain.exams.billing.BillingWorkloadTokenIssuer;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** One POST, no application retries/redirects, bounded response and end-to-end body deadline. */
public final class HttpReanswerEligibilityClient implements ReanswerEligibilityClient {
    private final URI endpoint;
    private final BillingWorkloadTokenIssuer issuer;
    private final HttpClient http;
    private final BillingWorkloadTokenIssuer.Purpose purpose;
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public HttpReanswerEligibilityClient(URI origin, BillingWorkloadTokenIssuer issuer) {
        this(origin, issuer, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }
    HttpReanswerEligibilityClient(URI origin, BillingWorkloadTokenIssuer issuer, HttpClient http) {
        this(origin, issuer, http, BillingWorkloadTokenIssuer.Purpose.REANSWER_ELIGIBILITY);
    }
    public static HttpReanswerEligibilityClient partLearning(URI origin, BillingWorkloadTokenIssuer issuer) {
        return new HttpReanswerEligibilityClient(origin, issuer, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build(),
                BillingWorkloadTokenIssuer.Purpose.PART_LEARNING_ELIGIBILITY);
    }
    HttpReanswerEligibilityClient(URI origin, BillingWorkloadTokenIssuer issuer, HttpClient http,
            BillingWorkloadTokenIssuer.Purpose purpose) {
        if (purpose != BillingWorkloadTokenIssuer.Purpose.REANSWER_ELIGIBILITY
                && purpose != BillingWorkloadTokenIssuer.Purpose.PART_LEARNING_ELIGIBILITY)
            throw new IllegalArgumentException("Unsupported eligibility purpose");
        this.purpose = purpose;
        this.endpoint = origin.resolve(paidOnly() ? "/internal/v1/entitlements/part-learning-eligibility"
                : "/internal/v1/entitlements/reanswer-eligibility");
        this.issuer = issuer;
        this.http = http;
    }
    private boolean paidOnly() { return purpose == BillingWorkloadTokenIssuer.Purpose.PART_LEARNING_ELIGIBILITY; }

    @Override public ReanswerApproval check(String userId) {
        try { if (!UUID.fromString(userId).toString().equals(userId)) throw ReanswerFailures.contract(); }
        catch (RuntimeException invalid) { throw ReanswerFailures.contract(); }
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .header("Authorization", issuer.authorization(purpose))
                .POST(HttpRequest.BodyPublishers.ofString("{\"userId\":\"" + userId + "\"}")).build();
        var future = http.sendAsync(request, ignored -> new LimitedBody());
        HttpResponse<byte[]> response;
        try { response = future.get(5, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) {
            future.cancel(true); Thread.currentThread().interrupt(); throw ReanswerFailures.unavailable();
        } catch (TimeoutException timeout) {
            future.cancel(true); throw ReanswerFailures.unavailable();
        } catch (ExecutionException failed) {
            Throwable cause=failed.getCause();
            while (cause instanceof CompletionException && cause.getCause()!=null) cause=cause.getCause();
            if (cause instanceof IllegalArgumentException || cause instanceof javax.net.ssl.SSLHandshakeException
                    || cause instanceof javax.net.ssl.SSLPeerUnverifiedException) throw ReanswerFailures.contract("transport_contract",0);
            throw ReanswerFailures.unavailable();
        }
        return decode(response.statusCode(), response.headers(), response.body(), userId);
    }

    ReanswerApproval decode(int status, HttpHeaders headers, byte[] bytes, String userId) {
        try {
            if (bytes.length > 16384 || headers.allValues("Cache-Control").stream()
                    .flatMap(value -> Arrays.stream(value.split(",")))
                    .noneMatch(value -> value.trim().equalsIgnoreCase("no-store"))) throw ReanswerFailures.contract();
            if (Set.of(401,403,404).contains(status)) throw ReanswerFailures.contract("ingress_auth_route",status);
            String contentType = headers.firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            if (!contentType.matches("application/json(?:\\s*;\\s*charset=utf-8)?")) throw ReanswerFailures.contract();
            String raw = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonNode node = mapper.readTree(raw);
            if (status == 400 || status == 503) {
                fields(node, Set.of("code","message","retryable","correlationId"));
                String correlation = text(node,"correlationId");
                UUID id = UUID.fromString(correlation);
                if (!id.toString().equals(correlation) || id.version()!=4 || id.variant()!=2
                        || !node.get("retryable").isBoolean()) throw ReanswerFailures.contract();
                boolean temporary = status == 503;
                if (!text(node,"code").equals(temporary ? "BILLING_TEMPORARILY_UNAVAILABLE" : "INVALID_REQUEST")
                        || !text(node,"message").equals(temporary ? "Billing is temporarily unavailable." : "The request is invalid.")
                        || node.get("retryable").booleanValue()!=temporary) throw ReanswerFailures.contract();
                if (temporary && headers.firstValue("Retry-After").orElse("").equals("1")) throw ReanswerFailures.unavailable();
                throw ReanswerFailures.contract("internal_error",status);
            }
            if (status!=200) throw ReanswerFailures.contract("unexpected_status",status);
            fields(node, Set.of("userId","decision","reason","checkedAt","validUntil"));
            if (!text(node,"userId").equals(userId)) throw ReanswerFailures.contract();
            String checked = timestamp(node,"checkedAt");
            String decision = text(node,"decision"), reason = text(node,"reason");
            if (decision.equals("ALLOWED") && reason.equals(paidOnly() ? "ACTIVE_PAID_ENTITLEMENT" : "ENTITLEMENT_AVAILABLE")) {
                var proof = new ReanswerApproval(ReanswerApproval.TYPE,ReanswerApproval.REVISION,userId,checked,timestamp(node,"validUntil"));
                if (!proof.valid()) throw ReanswerFailures.contract();
                return proof;
            }
            if (!node.get("validUntil").isNull()) throw ReanswerFailures.contract();
            if (decision.equals("DENIED") && reason.equals(paidOnly() ? "PAID_ENTITLEMENT_REQUIRED" : "ENTITLEMENT_INSUFFICIENT"))
                throw new ExamsException(ErrorStatus._ENTITLEMENT_INSUFFICIENT);
            if (decision.equals("DENIED") && reason.equals("ACCOUNT_WITHDRAWN"))
                throw new ExamsException(ErrorStatus._ACCOUNT_WITHDRAWN);
            if (decision.equals("PENDING") && reason.equals("ENTITLEMENT_STATE_UNCONFIRMED")) throw ReanswerFailures.unavailable();
            throw ReanswerFailures.contract();
        } catch (ExamsException known) { throw known; }
        catch (ReanswerFailures.ContractFailure known) { throw known; }
        catch (Exception invalid) { throw ReanswerFailures.contract(); }
    }
    private static void fields(JsonNode node, Set<String> expected) {
        if (node==null || !node.isObject()) throw ReanswerFailures.contract();
        Set<String> actual = new HashSet<>(); node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw ReanswerFailures.contract();
    }
    private static String text(JsonNode node,String key) {
        if (!node.path(key).isTextual()) throw ReanswerFailures.contract();
        return node.get(key).textValue();
    }
    private static String timestamp(JsonNode node,String key) {
        String value=text(node,key);
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?Z")) throw ReanswerFailures.contract();
        Instant.parse(value); return value;
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription; private int size;
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription subscription) { this.subscription=subscription; delegate.onSubscribe(subscription); }
        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer:buffers) {
                if (buffer.remaining()>16384-size) { subscription.cancel(); delegate.onError(new IllegalArgumentException("Response limit")); return; }
                size+=buffer.remaining();
            }
            delegate.onNext(buffers);
        }
        public void onError(Throwable failure) { delegate.onError(failure); }
        public void onComplete() { delegate.onComplete(); }
    }
}
