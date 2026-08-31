package web.tosunsaeng.domain.exams.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;

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
                        .build()
        );
    }

    SigV4BillingReservationClient(
            BillingSagaProperties properties,
            AwsCredentialsProvider credentialsProvider,
            ObjectMapper objectMapper,
            AwsV4HttpSigner signer,
            HttpClient httpClient
    ) {
        this.properties = properties;
        this.credentialsProvider = credentialsProvider;
        this.objectMapper = objectMapper;
        this.signer = signer;
        this.httpClient = httpClient;
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
                ReserveResponse.class
        );
        return response.toSnapshot();
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
                ConfirmResponse.class
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
                CancelResponse.class
        );
        return response.toSnapshot();
    }

    @Override
    public ReservationSnapshot status(String userId, String operationId) {
        StatusResponse response = post(
                "/internal/v1/reservations/status",
                null,
                new StatusRequest(userId, operationId),
                StatusResponse.class
        );
        return response.toSnapshot();
    }

    private <T> T post(String path, String operationId, Object body, Class<T> responseType) {
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

        try {
            SdkHttpRequest.Builder unsignedBuilder = SdkHttpRequest.builder()
                    .uri(uri)
                    .method(SdkHttpMethod.POST)
                    .putHeader("Content-Type", "application/json");
            if (operationId != null) {
                unsignedBuilder.putHeader("Idempotency-Key", operationId);
            }
            ContentStreamProvider payload = ContentStreamProvider.fromByteArray(requestBody);
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
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return decodeSuccess(responseBody, responseType);
            }
            throw mapError(response.statusCode(), responseBody, retryAfter(response));
        } catch (BillingClientException known) {
            throw known;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BillingClientException(
                    BillingClientException.Category.TEMPORARILY_UNAVAILABLE,
                    null,
                    interrupted
            );
        } catch (IOException | RuntimeException transportFailure) {
            throw new BillingClientException(
                    BillingClientException.Category.TEMPORARILY_UNAVAILABLE,
                    null,
                    transportFailure
            );
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

    private record ReserveResponse(
            String operationId,
            String reservationId,
            web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind reservationKind,
            ReservationStatus reservationStatus,
            String attemptGroupId,
            String sessionId,
            String mockExamId,
            Instant expiresAt
    ) {
        ReservationSnapshot toSnapshot() {
            return new ReservationSnapshot(
                    operationId, reservationId, reservationKind, reservationStatus,
                    attemptGroupId, null, sessionId, mockExamId, expiresAt, null
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
            Instant expiresAt,
            Instant terminalAt
    ) {
        ReservationSnapshot toSnapshot() {
            return new ReservationSnapshot(
                    operationId, reservationId, reservationKind, reservationStatus,
                    attemptGroupId, attemptGroupStatus, sessionId, mockExamId,
                    expiresAt, terminalAt
            );
        }
    }
}
