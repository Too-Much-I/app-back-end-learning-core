package web.tosunsaeng.domain.exams.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;
import web.tosunsaeng.domain.exams.domain.enums.BillingContinuationReason;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SigV4BillingReservationClient implements BillingReservationClient {

    static final int MAX_RESPONSE_BYTES = 16 * 1024;
    private static final String SIGNING_NAME = "vpc-lattice-svcs";
    private static final DateTimeFormatter UTC_MILLIS = new DateTimeFormatterBuilder()
            .appendInstant(3)
            .toFormatter();

    private final BillingSagaProperties properties;
    private final AwsCredentialsProvider credentialsProvider;
    private final ObjectMapper objectMapper;
    private final AwsV4HttpSigner signer;
    private final HttpClient httpClient;
    private final ObjectProvider<Tracer> tracerProvider;
    private final MeterRegistry meterRegistry;

    public SigV4BillingReservationClient(
            BillingSagaProperties properties,
            AwsCredentialsProvider credentialsProvider,
            ObjectMapper objectMapper
    ) {
        this(
                properties,
                credentialsProvider,
                objectMapper,
                AwsV4HttpSigner.create(),
                HttpClient.newBuilder()
                        .connectTimeout(properties.getConnectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                null,
                null
        );
    }

    public SigV4BillingReservationClient(
            BillingSagaProperties properties,
            AwsCredentialsProvider credentialsProvider,
            ObjectMapper objectMapper,
            ObjectProvider<Tracer> tracerProvider,
            MeterRegistry meterRegistry
    ) {
        this(
                properties,
                credentialsProvider,
                objectMapper,
                AwsV4HttpSigner.create(),
                HttpClient.newBuilder()
                        .connectTimeout(properties.getConnectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                tracerProvider,
                meterRegistry
        );
    }

    SigV4BillingReservationClient(
            BillingSagaProperties properties,
            AwsCredentialsProvider credentialsProvider,
            ObjectMapper objectMapper,
            AwsV4HttpSigner signer,
            HttpClient httpClient
    ) {
        this(properties, credentialsProvider, objectMapper, signer, httpClient, null, null);
    }

    private SigV4BillingReservationClient(
            BillingSagaProperties properties,
            AwsCredentialsProvider credentialsProvider,
            ObjectMapper objectMapper,
            AwsV4HttpSigner signer,
            HttpClient httpClient,
            ObjectProvider<Tracer> tracerProvider,
            MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.credentialsProvider = credentialsProvider;
        this.objectMapper = objectMapper;
        this.signer = signer;
        this.httpClient = httpClient;
        this.tracerProvider = tracerProvider;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public ReservationSnapshot reserve(
            String operationId,
            String userId,
            String sessionId,
            String mockExamId
    ) {
        ReserveResponse response = post(
                "/internal/v1/reservations",
                operationId,
                new ReserveRequest(userId, sessionId, mockExamId),
                ReserveResponse.class,
                "billing_reservation_reserve"
        );
        return response.toSnapshot();
    }

    @Override
    public ReservationSnapshot reservePhoneContinuation(
            String operationId,
            String userId,
            String sessionId,
            String mockExamId,
            BillingContinuationReason continuationReason,
            String continuationId,
            String expectedAttemptGroupId
    ) {
        ReserveResponse response = post(
                "/internal/v1/reservations",
                operationId,
                new PhoneReserveRequest(
                        userId, sessionId, mockExamId, continuationReason,
                        continuationId, expectedAttemptGroupId
                ),
                ReserveResponse.class,
                "billing_reservation_reserve"
        );
        return response.toSnapshot();
    }

    @Override
    public Optional<PhoneContinuationSnapshot> findPhoneContinuation(String userId) {
        RawResponse response = exchange(
                "/internal/v1/reservations/continuations/phone",
                null,
                new PhoneContinuationRequest(userId),
                "billing_phone_continuation"
        );
        if (response.statusCode() == 204) {
            if (response.body().length != 0) {
                throw new BillingClientException(
                        BillingClientException.Category.CONTRACT_ERROR,
                        response.retryAfterSeconds()
                );
            }
            return Optional.empty();
        }
        if (response.statusCode() == 200) {
            return Optional.of(decodeSuccess(
                    response.body(), PhoneContinuationResponse.class
            ).toSnapshot());
        }
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            throw new BillingClientException(
                    BillingClientException.Category.CONTRACT_ERROR,
                    response.retryAfterSeconds()
            );
        }
        throw mapError(
                response.statusCode(), response.body(), response.retryAfterSeconds()
        );
    }

    @Override
    public ReservationSnapshot confirm(
            String operationId,
            String reservationId,
            String userId,
            String sessionId,
            Instant sessionCommittedAt
    ) {
        ConfirmResponse response = post(
                "/internal/v1/reservations/" + reservationId + "/confirm",
                operationId,
                new ConfirmRequest(
                        userId,
                        sessionId,
                        UTC_MILLIS.format(sessionCommittedAt)
                ),
                ConfirmResponse.class,
                "billing_reservation_confirm"
        );
        return response.toSnapshot();
    }

    @Override
    public ReservationSnapshot cancel(
            String operationId,
            String reservationId,
            String userId
    ) {
        CancelResponse response = post(
                "/internal/v1/reservations/" + reservationId + "/cancel",
                operationId,
                new CancelRequest(userId, "SESSION_COMMIT_FAILED"),
                CancelResponse.class,
                "billing_reservation_cancel"
        );
        return response.toSnapshot();
    }

    @Override
    public ReservationSnapshot status(String userId, String operationId) {
        StatusResponse response = post(
                "/internal/v1/reservations/status",
                null,
                new StatusRequest(userId, operationId),
                StatusResponse.class,
                "billing_reservation_status"
        );
        return response.toSnapshot();
    }

    private <T> T post(
            String path,
            String operationId,
            Object body,
            Class<T> responseType,
            String telemetryOperation
    ) {
        RawResponse response = exchange(path, operationId, body, telemetryOperation);
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return decodeSuccess(response.body(), responseType);
        }
        throw mapError(response.statusCode(), response.body(), response.retryAfterSeconds());
    }

    private RawResponse exchange(
            String path,
            String operationId,
            Object body,
            String telemetryOperation
    ) {
        URI uri = endpoint(path);
        byte[] requestBody;
        try {
            requestBody = objectMapper.writeValueAsBytes(body);
        } catch (IOException serializationFailure) {
            throw new BillingClientException(
                    BillingClientException.Category.CONTRACT_ERROR,
                    null,
                    serializationFailure
            );
        }

        long startedAt = System.nanoTime();
        Tracer tracer = tracerProvider == null ? null : tracerProvider.getIfAvailable();
        Span span = startClientSpan(tracer, telemetryOperation);
        String traceId = span == null
                ? "00000000000000000000000000000000"
                : span.context().traceId();
        String outcome = "temporary_failure";
        try (Tracer.SpanInScope ignored = span == null || tracer == null
                ? null : tracer.withSpan(span)) {
            SdkHttpRequest.Builder unsignedBuilder = SdkHttpRequest.builder()
                    .uri(uri)
                    .method(SdkHttpMethod.POST)
                    .putHeader("Content-Type", "application/json");
            if (operationId != null) {
                unsignedBuilder.putHeader("Idempotency-Key", operationId);
            }
            if (span != null) {
                unsignedBuilder.putHeader("traceparent", traceparent(span));
            }
            ContentStreamProvider payload = ContentStreamProvider.fromByteArray(requestBody);
            // URI, body and trace headers are final before signing. Do not mutate afterwards.
            SignedRequest signed = signer.sign(signRequest -> signRequest
                    .identity(credentialsProvider.resolveCredentials())
                    .request(unsignedBuilder.build())
                    .payload(payload)
                    .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, SIGNING_NAME)
                    .putProperty(AwsV4HttpSigner.REGION_NAME, properties.getRegion()));

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                    .timeout(properties.getReadTimeout())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody));
            signed.request().headers().forEach((name, values) -> {
                if (!isRestrictedHeader(name)) {
                    values.forEach(value -> requestBuilder.header(name, value));
                }
            });

            HttpResponse<InputStream> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );
            byte[] responseBody;
            try (InputStream input = response.body()) {
                responseBody = input.readNBytes(MAX_RESPONSE_BYTES + 1);
            }
            if (responseBody.length > MAX_RESPONSE_BYTES) {
                throw new BillingClientException(
                        BillingClientException.Category.CONTRACT_ERROR,
                        retryAfter(response)
                );
            }
            outcome = (response.statusCode() == 200 || response.statusCode() == 204)
                    ? "success" : httpOutcome(response.statusCode());
            return new RawResponse(response.statusCode(), responseBody, retryAfter(response));
        } catch (BillingClientException known) {
            outcome = categoryOutcome(known.category());
            throw known;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            outcome = "temporary_failure";
            throw new BillingClientException(
                    BillingClientException.Category.TEMPORARILY_UNAVAILABLE,
                    null,
                    interrupted
            );
        } catch (IOException | RuntimeException transportFailure) {
            outcome = "temporary_failure";
            throw new BillingClientException(
                    BillingClientException.Category.TEMPORARILY_UNAVAILABLE,
                    null,
                    transportFailure
            );
        } finally {
            if (span != null) {
                span.end();
            }
            observe(telemetryOperation, outcome, traceId, System.nanoTime() - startedAt);
        }
    }

    private URI endpoint(String path) {
        URI base = BillingSagaConfigurationValidator.parseBaseUri(properties.getBaseUrl());
        String origin = base.toString();
        while (origin.endsWith("/")) {
            origin = origin.substring(0, origin.length() - 1);
        }
        return URI.create(origin + path);
    }

    private Span startClientSpan(Tracer tracer, String operation) {
        if (tracer == null) {
            return null;
        }
        Span current = tracer.currentSpan();
        Span.Builder builder = tracer.spanBuilder().name(operation).kind(Span.Kind.CLIENT);
        return (current == null
                ? builder.setNoParent()
                : builder.setParent(current.context()))
                .start();
    }

    private static String traceparent(Span span) {
        String flags = Boolean.TRUE.equals(span.context().sampled()) ? "01" : "00";
        return "00-%s-%s-%s".formatted(
                span.context().traceId(), span.context().spanId(), flags
        );
    }

    private void observe(String operation, String outcome, String traceId, long durationNanos) {
        long safeNanos = Math.max(0L, durationNanos);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(safeNanos);
        log.info(
                "service=learning-core operation={} outcome={} traceId={} durationMs={}",
                operation, outcome, traceId, durationMs
        );
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                "learning.core.billing.client.calls",
                "service", "learning-core",
                "operation", operation,
                "outcome", outcome
        ).increment();
        Timer.builder("learning.core.billing.client.duration")
                .tags(
                        "service", "learning-core",
                        "operation", operation,
                        "outcome", outcome
                )
                .register(meterRegistry)
                .record(safeNanos, TimeUnit.NANOSECONDS);
    }

    private static String httpOutcome(int status) {
        if (status == 401 || status == 403) {
            return "auth_failure";
        }
        if (status == 429) {
            return "rate_limited";
        }
        if (status == 408 || status == 425 || status >= 500) {
            return "temporary_failure";
        }
        return "contract_error";
    }

    private static String categoryOutcome(BillingClientException.Category category) {
        return switch (category) {
            case AUTH_FAILURE -> "auth_failure";
            case RATE_LIMITED -> "rate_limited";
            case TEMPORARILY_UNAVAILABLE, PROCESSING, OPERATION_NOT_FOUND ->
                    "temporary_failure";
            default -> "contract_error";
        };
    }

    private <T> T decodeSuccess(byte[] body, Class<T> responseType) {
        if (body.length == 0) {
            throw new BillingClientException(BillingClientException.Category.CONTRACT_ERROR, null);
        }
        try {
            T decoded = objectMapper.readValue(body, responseType);
            if (decoded == null) {
                throw new BillingClientException(
                        BillingClientException.Category.CONTRACT_ERROR,
                        null
                );
            }
            return decoded;
        } catch (IOException invalidResponse) {
            throw new BillingClientException(
                    BillingClientException.Category.CONTRACT_ERROR,
                    null,
                    invalidResponse
            );
        }
    }

    private BillingClientException mapError(int status, byte[] body, Integer retryAfter) {
        if (status == 401 || status == 403) {
            return new BillingClientException(
                    BillingClientException.Category.AUTH_FAILURE, retryAfter
            );
        }
        String code = errorCode(body);
        BillingClientException.Category category = switch (code) {
            case "INVALID_REQUEST", "INVALID_IDEMPOTENCY_KEY" ->
                    BillingClientException.Category.INVALID_REQUEST;
            case "ENTITLEMENT_INSUFFICIENT" ->
                    BillingClientException.Category.ENTITLEMENT_INSUFFICIENT;
            case "COMMAND_PROCESSING" -> BillingClientException.Category.PROCESSING;
            case "IDEMPOTENCY_KEY_CONFLICT" ->
                    BillingClientException.Category.IDEMPOTENCY_CONFLICT;
            case "RESERVATION_STATE_CONFLICT" ->
                    BillingClientException.Category.RESERVATION_CONFLICT;
            case "OPERATION_NOT_FOUND" ->
                    BillingClientException.Category.OPERATION_NOT_FOUND;
            case "BILLING_TEMPORARILY_UNAVAILABLE" ->
                    BillingClientException.Category.TEMPORARILY_UNAVAILABLE;
            default -> fallbackCategory(status);
        };
        return new BillingClientException(category, retryAfter);
    }

    private String errorCode(byte[] body) {
        if (body.length == 0) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode code = root.get("code");
            return code != null && code.isTextual() ? code.textValue() : "";
        } catch (IOException ignored) {
            return "";
        }
    }

    private static BillingClientException.Category fallbackCategory(int status) {
        if (status == 401 || status == 403) {
            return BillingClientException.Category.AUTH_FAILURE;
        }
        if (status == 429) {
            return BillingClientException.Category.RATE_LIMITED;
        }
        if (status == 408 || status == 425 || status >= 500) {
            return BillingClientException.Category.TEMPORARILY_UNAVAILABLE;
        }
        return BillingClientException.Category.CONTRACT_ERROR;
    }

    private static Integer retryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After")
                .flatMap(value -> {
                    try {
                        int parsed = Integer.parseInt(value);
                        return parsed >= 1 && parsed <= 300
                                ? java.util.Optional.of(parsed)
                                : java.util.Optional.empty();
                    } catch (NumberFormatException ignored) {
                        return java.util.Optional.empty();
                    }
                })
                .orElse(null);
    }

    private static boolean isRestrictedHeader(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.equals("host") || normalized.equals("content-length");
    }

    private record ReserveRequest(String userId, String sessionId, String mockExamId) {
    }

    private record PhoneReserveRequest(
            String userId,
            String sessionId,
            String mockExamId,
            BillingContinuationReason continuationReason,
            String continuationId,
            String expectedAttemptGroupId
    ) {
    }

    private record PhoneContinuationRequest(String userId) {
    }

    private record ConfirmRequest(
            String userId,
            String sessionId,
            String sessionCommittedAt
    ) {
    }

    private record CancelRequest(String userId, String reason) {
    }

    private record StatusRequest(String userId, String operationId) {
    }

    private record RawResponse(int statusCode, byte[] body, Integer retryAfterSeconds) {
    }

    private record PhoneContinuationResponse(
            BillingContinuationReason continuationReason,
            String continuationId,
            String attemptGroupId,
            String mockExamId
    ) {
        private PhoneContinuationResponse {
            if (continuationReason != BillingContinuationReason.PHONE_REJOIN) {
                throw new IllegalArgumentException("Billing continuation reason is invalid");
            }
            requireCanonicalUuidV4(continuationId, "continuationId");
            requireCanonicalUuidV4(attemptGroupId, "attemptGroupId");
            requireOpaqueText(mockExamId, "mockExamId");
        }

        PhoneContinuationSnapshot toSnapshot() {
            return new PhoneContinuationSnapshot(
                    continuationReason, continuationId, attemptGroupId, mockExamId
            );
        }
    }

    private record ReserveResponse(
            String operationId,
            String reservationId,
            web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind reservationKind,
            ReservationStatus reservationStatus,
            String attemptGroupId,
            String sessionId,
            String mockExamId,
            BillingContinuationReason continuationReason,
            String continuationId,
            Instant expiresAt
    ) {
        private ReserveResponse {
            requireText(operationId, "operationId");
            requireText(reservationId, "reservationId");
            requireValue(reservationKind, "reservationKind");
            requireValue(reservationStatus, "reservationStatus");
            requireCanonicalUuidV4(attemptGroupId, "attemptGroupId");
            requireText(sessionId, "sessionId");
            requireText(mockExamId, "mockExamId");
            requireContinuationPair(continuationReason, continuationId);
            requireValue(expiresAt, "expiresAt");
        }

        ReservationSnapshot toSnapshot() {
            return new ReservationSnapshot(
                    operationId, reservationId, reservationKind, reservationStatus,
                    attemptGroupId, null, sessionId, mockExamId,
                    continuationReason, continuationId, expiresAt, null
            );
        }
    }

    private record ConfirmResponse(
            String operationId,
            String reservationId,
            ReservationStatus reservationStatus,
            String attemptGroupId,
            AttemptGroupStatus attemptGroupStatus,
            String sessionId,
            Instant confirmedAt
    ) {
        private ConfirmResponse {
            requireText(operationId, "operationId");
            requireText(reservationId, "reservationId");
            requireValue(reservationStatus, "reservationStatus");
            requireCanonicalUuidV4(attemptGroupId, "attemptGroupId");
            requireValue(attemptGroupStatus, "attemptGroupStatus");
            requireText(sessionId, "sessionId");
            requireValue(confirmedAt, "confirmedAt");
        }

        ReservationSnapshot toSnapshot() {
            return new ReservationSnapshot(
                    operationId, reservationId, null, reservationStatus,
                    attemptGroupId, attemptGroupStatus, sessionId, null, null, confirmedAt
            );
        }
    }

    private record CancelResponse(
            String operationId,
            String reservationId,
            ReservationStatus reservationStatus,
            Instant canceledAt
    ) {
        private CancelResponse {
            requireText(operationId, "operationId");
            requireText(reservationId, "reservationId");
            requireValue(reservationStatus, "reservationStatus");
            requireValue(canceledAt, "canceledAt");
        }

        ReservationSnapshot toSnapshot() {
            return new ReservationSnapshot(
                    operationId, reservationId, null, reservationStatus,
                    null, null, null, null, null, canceledAt
            );
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record StatusResponse(
            String operationId,
            String reservationId,
            web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind reservationKind,
            ReservationStatus reservationStatus,
            String attemptGroupId,
            AttemptGroupStatus attemptGroupStatus,
            String sessionId,
            String mockExamId,
            BillingContinuationReason continuationReason,
            String continuationId,
            Instant expiresAt,
            Instant terminalAt
    ) {
        private StatusResponse {
            requireText(operationId, "operationId");
            requireText(reservationId, "reservationId");
            requireValue(reservationKind, "reservationKind");
            requireValue(reservationStatus, "reservationStatus");
            requireCanonicalUuidV4(attemptGroupId, "attemptGroupId");
            requireText(sessionId, "sessionId");
            requireText(mockExamId, "mockExamId");
            requireContinuationPair(continuationReason, continuationId);
            if (reservationStatus == ReservationStatus.RESERVED) {
                requireValue(expiresAt, "expiresAt");
            } else {
                requireValue(terminalAt, "terminalAt");
            }
            if (reservationStatus == ReservationStatus.CONFIRMED) {
                requireValue(attemptGroupStatus, "attemptGroupStatus");
            }
        }

        ReservationSnapshot toSnapshot() {
            return new ReservationSnapshot(
                    operationId, reservationId, reservationKind, reservationStatus,
                    attemptGroupId, attemptGroupStatus, sessionId, mockExamId,
                    continuationReason, continuationId, expiresAt, terminalAt
            );
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Billing response field is missing: " + field);
        }
    }

    private static void requireValue(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Billing response field is missing: " + field);
        }
    }

    private static void requireContinuationPair(
            BillingContinuationReason reason,
            String continuationId
    ) {
        if (reason == null && continuationId == null) {
            return;
        }
        if (reason != BillingContinuationReason.PHONE_REJOIN) {
            throw new IllegalArgumentException("Billing continuation reason is invalid");
        }
        requireCanonicalUuidV4(continuationId, "continuationId");
    }

    private static void requireCanonicalUuidV4(String value, String field) {
        requireOpaqueText(value, field);
        try {
            java.util.UUID uuid = java.util.UUID.fromString(value);
            if (uuid.version() != 4 || !uuid.toString().equals(value)) {
                throw new IllegalArgumentException("Billing UUID field is invalid: " + field);
            }
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Billing UUID field is invalid: " + field, invalid);
        }
    }

    private static void requireOpaqueText(String value, String field) {
        requireText(value, field);
        if (!value.equals(value.trim()) || value.length() > 128 || value.chars().anyMatch(
                character -> Character.isISOControl(character))) {
            throw new IllegalArgumentException("Billing text field is invalid: " + field);
        }
    }
}
