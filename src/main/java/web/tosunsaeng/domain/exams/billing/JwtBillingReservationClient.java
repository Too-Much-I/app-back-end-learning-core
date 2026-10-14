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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class JwtBillingReservationClient implements BillingReservationClient {

    static final int MAX_RESPONSE_BYTES = 16 * 1024;
    private static final DateTimeFormatter UTC_MILLIS = new DateTimeFormatterBuilder()
            .appendInstant(3)
            .toFormatter();

    private final BillingSagaProperties properties;
    private final BillingWorkloadTokenIssuer tokenIssuer;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ObjectProvider<Tracer> tracerProvider;
    private final MeterRegistry meterRegistry;

    public JwtBillingReservationClient(
            BillingSagaProperties properties,
            BillingWorkloadTokenIssuer tokenIssuer,
            ObjectMapper objectMapper
    ) {
        this(
                properties,
                tokenIssuer,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(properties.getConnectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                null,
                null
        );
    }

    public JwtBillingReservationClient(
            BillingSagaProperties properties,
            BillingWorkloadTokenIssuer tokenIssuer,
            ObjectMapper objectMapper,
            ObjectProvider<Tracer> tracerProvider,
            MeterRegistry meterRegistry
    ) {
        this(
                properties,
                tokenIssuer,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(properties.getConnectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                tracerProvider,
                meterRegistry
        );
    }

    JwtBillingReservationClient(
            BillingSagaProperties properties,
            BillingWorkloadTokenIssuer tokenIssuer,
            ObjectMapper objectMapper,
            HttpClient httpClient
    ) {
        this(properties, tokenIssuer, objectMapper, httpClient, null, null);
    }

    private JwtBillingReservationClient(
            BillingSagaProperties properties,
            BillingWorkloadTokenIssuer tokenIssuer,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            ObjectProvider<Tracer> tracerProvider,
            MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.tokenIssuer = tokenIssuer;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.tracerProvider = tracerProvider;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public ReservationSnapshot reservePaid(String operationId, PaidReservationProtocol.Request request) {
        PaidReservationProtocol.uuid(operationId, true);
        PaidReservationProtocol.require(request != null);
        ReservationSnapshot snapshot = postPaid("/internal/v1/reservations", operationId,
                request, ReserveResponse.class, "billing_reservation_reserve").toSnapshot();
        PaidReservationProtocol.require(snapshot.reservationStatus() == ReservationStatus.RESERVED);
        PaidReservationProtocol.matches(operationId, request, snapshot);
        return snapshot;
    }

    @Override
    public ReservationSnapshot statusPaid(String userId, String operationId) {
        PaidReservationProtocol.uuid(userId, false);
        PaidReservationProtocol.uuid(operationId, true);
        ReservationSnapshot snapshot = postPaid("/internal/v1/reservations/status", null,
                new StatusRequest(userId, operationId), StatusResponse.class, "billing_reservation_status").toSnapshot();
        PaidReservationProtocol.snapshot(snapshot);
        PaidReservationProtocol.require(operationId.equals(snapshot.operationId()));
        return snapshot;
    }

    @Override
    public ReservationSnapshot reservePaidPhone(String operationId, PaidReservationProtocol.PhoneRequest request) {
        PaidReservationProtocol.uuid(operationId, true);
        PaidReservationProtocol.require(request != null);
        ReservationSnapshot snapshot = postPaid("/internal/v1/reservations", operationId, request,
                ReserveResponse.class, "billing_reservation_reserve").toSnapshot();
        PaidReservationProtocol.require(snapshot.reservationStatus() == ReservationStatus.RESERVED);
        PaidReservationProtocol.matchesPhone(operationId, request, snapshot);
        return snapshot;
    }

    @Override
    public ReservationSnapshot confirmPaid(String operationId, String reservationId, String userId,
            String sessionId, Instant committedAt) {
        PaidReservationProtocol.uuid(operationId, true); PaidReservationProtocol.uuid(reservationId, true);
        PaidReservationProtocol.uuid(userId, false); PaidReservationProtocol.opaque(sessionId);
        PaidReservationProtocol.require(committedAt != null);
        ReservationSnapshot snapshot = postPaid("/internal/v1/reservations/" + reservationId + "/confirm", operationId,
                new ConfirmRequest(userId, sessionId, UTC_MILLIS.format(committedAt)), ConfirmResponse.class,
                "billing_reservation_confirm").toSnapshot();
        PaidReservationProtocol.require(operationId.equals(snapshot.operationId()) && reservationId.equals(snapshot.reservationId())
                && sessionId.equals(snapshot.sessionId()) && snapshot.reservationStatus() == ReservationStatus.CONFIRMED);
        return snapshot;
    }

    @Override
    public ReservationSnapshot cancelPaid(String operationId, String reservationId, String userId) {
        PaidReservationProtocol.uuid(operationId, true); PaidReservationProtocol.uuid(reservationId, true);
        PaidReservationProtocol.uuid(userId, false);
        ReservationSnapshot snapshot = postPaid("/internal/v1/reservations/" + reservationId + "/cancel", operationId,
                new CancelRequest(userId, "SESSION_COMMIT_FAILED"), CancelResponse.class, "billing_reservation_cancel").toSnapshot();
        PaidReservationProtocol.require(operationId.equals(snapshot.operationId()) && reservationId.equals(snapshot.reservationId())
                && snapshot.reservationStatus() == ReservationStatus.CANCELED);
        return snapshot;
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
        return findPhoneContinuation(userId, false);
    }

    @Override
    public Optional<PhoneContinuationSnapshot> findPaidPhoneContinuation(String userId) {
        PaidReservationProtocol.uuid(userId, false);
        return findPhoneContinuation(userId, true);
    }

    private Optional<PhoneContinuationSnapshot> findPhoneContinuation(String userId, boolean paid) {
        RawResponse response = exchange(
                "/internal/v1/reservations/continuations/phone",
                null,
                new PhoneContinuationRequest(userId),
                "billing_phone_continuation", paid ? PaidBillingErrorDecoder.Endpoint.PHONE_DISCOVERY : null
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
        if (paid) throw paidError(PaidBillingErrorDecoder.Endpoint.PHONE_DISCOVERY, response);
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

    private <T> T postPaid(String path, String operationId, Object body, Class<T> responseType, String telemetryOperation) {
        PaidBillingErrorDecoder.Endpoint endpoint = responseType == ReserveResponse.class ? PaidBillingErrorDecoder.Endpoint.RESERVE
                : responseType == StatusResponse.class ? PaidBillingErrorDecoder.Endpoint.STATUS
                : responseType == ConfirmResponse.class ? PaidBillingErrorDecoder.Endpoint.CONFIRM : PaidBillingErrorDecoder.Endpoint.CANCEL;
        RawResponse response = exchange(path, operationId, body, telemetryOperation, endpoint);
        if (response.statusCode() == 200) return decodeSuccess(response.body(), responseType);
        if (response.statusCode() >= 200 && response.statusCode() < 300)
            throw new BillingClientException(BillingClientException.Category.CONTRACT_ERROR, null);
        throw paidError(endpoint, response);
    }

    private BillingClientException paidError(PaidBillingErrorDecoder.Endpoint endpoint, RawResponse response) {
        return new PaidBillingErrorDecoder(objectMapper).decode(endpoint, response.statusCode(), response.body(), response.rawRetryAfter());
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
        return exchange(path, operationId, body, telemetryOperation, null);
    }

    private RawResponse exchange(String path, String operationId, Object body, String telemetryOperation,
            PaidBillingErrorDecoder.Endpoint paidEndpoint) {
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
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                    .timeout(properties.getReadTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody));
            if (operationId != null) {
                requestBuilder.header("Idempotency-Key", operationId);
            }
            if (span != null) {
                requestBuilder.header("traceparent", traceparent(span));
            }
            requestBuilder.header("Authorization", tokenIssuer.authorization(
                    BillingWorkloadTokenIssuer.Purpose.RESERVATIONS));

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
            var retryHeaders = response.headers().allValues("Retry-After");
            String rawRetryAfter = retryHeaders.isEmpty() ? null : retryHeaders.size() == 1 ? retryHeaders.getFirst() : "INVALID";
            RawResponse raw = new RawResponse(response.statusCode(), responseBody, retryAfter(response), rawRetryAfter);
            if (paidEndpoint != null && response.statusCode() >= 300)
                outcome = categoryOutcome(paidError(paidEndpoint, raw).category());
            return raw;
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
            case OWNER_BLOCKED -> "blocked_owner";
            case MEMBER_EVIDENCE_PENDING -> "waiting_member";
            case PRE_RESERVE_REJECTED -> "pre_reserve_rejected";
            case ENTITLEMENT_REVOKED, TRANSITION_CONTEXT_CONFLICT -> "business_rejected";
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
            if (responseType == ReserveResponse.class || responseType == StatusResponse.class) {
                JsonNode tree = objectMapper.readTree(body);
                if (tree == null || !tree.isObject()) {
                    throw new BillingClientException(BillingClientException.Category.CONTRACT_ERROR, null);
                }
                for (String field : java.util.List.of("entitlementSource", "transition", "confirmRejection")) {
                    if (tree.has(field) && tree.get(field).isNull()) {
                        throw new BillingClientException(BillingClientException.Category.CONTRACT_ERROR, null);
                    }
                }
            }
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

    private record RawResponse(int statusCode, byte[] body, Integer retryAfterSeconds, String rawRetryAfter) {
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
            Instant expiresAt,
            PaidReservationProtocol.Source entitlementSource,
            PaidReservationProtocol.Transition transition
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
            if (transition != null && (entitlementSource == null
                    || reservationKind != web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind.INITIAL))
                throw new IllegalArgumentException("Invalid paid transition");
        }

        ReservationSnapshot toSnapshot() {
            return new ReservationSnapshot(
                    operationId, reservationId, reservationKind, reservationStatus,
                    attemptGroupId, null, sessionId, mockExamId,
                    continuationReason, continuationId, expiresAt, null, entitlementSource, transition, null
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
            Instant terminalAt,
            PaidReservationProtocol.Source entitlementSource,
            PaidReservationProtocol.Transition transition,
            PaidReservationProtocol.ConfirmRejection confirmRejection
    ) {
        private StatusResponse {
            if (transition != null && (entitlementSource == null
                    || reservationKind != web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind.INITIAL))
                throw new IllegalArgumentException("Invalid paid transition");
            if (confirmRejection != null && (entitlementSource == null || reservationStatus != ReservationStatus.CANCELED))
                throw new IllegalArgumentException("Invalid confirm rejection");
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
                    continuationReason, continuationId, expiresAt, terminalAt, entitlementSource, transition, confirmRejection
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
