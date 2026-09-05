package web.tosunsaeng.domain.exams.billing;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind;
import web.tosunsaeng.domain.exams.domain.enums.BillingContinuationReason;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SigV4BillingReservationClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void reserveUsesSignedJsonRequestAndStrictResponseContract() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> idempotencyKey = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/reservations", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("{"
                    + "\"operationId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872c\","
                    + "\"reservationId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872d\","
                    + "\"reservationKind\":\"INITIAL\","
                    + "\"reservationStatus\":\"RESERVED\","
                    + "\"attemptGroupId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872e\","
                    + "\"sessionId\":\"ex_contract\","
                    + "\"mockExamId\":\"mock_exam_003\","
                    + "\"expiresAt\":\"2026-08-28T03:05:00Z\""
                    + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        BillingSagaProperties properties = new BillingSagaProperties();
        properties.setCreationSagaEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setRegion("ap-northeast-2");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));
        BillingReservationClient client = new SigV4BillingReservationClient(
                properties,
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key", "test-secret")),
                new BillingSagaConfiguration().billingContractObjectMapper()
        );

        BillingReservationClient.ReservationSnapshot result = client.reserve(
                "018f6f36-2f42-4bf5-8c17-0be35de4872c",
                "00000000-0000-4000-8000-000000000001",
                "ex_contract",
                "mock_exam_003"
        );

        assertEquals(BillingReservationKind.INITIAL, result.reservationKind());
        assertEquals(BillingReservationClient.ReservationStatus.RESERVED, result.reservationStatus());
        assertEquals("018f6f36-2f42-4bf5-8c17-0be35de4872c", idempotencyKey.get());
        assertNotNull(authorization.get());
        assertTrue(authorization.get().contains("Credential=test-key/"));
        assertTrue(authorization.get().contains("/ap-northeast-2/vpc-lattice-svcs/aws4_request"));
        assertTrue(body.get().contains("\"sessionId\":\"ex_contract\""));
    }

    @Test
    void confirmSerializesSessionCommittedAtWithExactlyThreeUtcMilliseconds() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        String reservationId = "018f6f36-2f42-4bf5-8c17-0be35de4872d";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/internal/v1/reservations/" + reservationId + "/confirm",
                exchange -> {
                    body.set(new String(
                            exchange.getRequestBody().readAllBytes(),
                            StandardCharsets.UTF_8
                    ));
                    byte[] response = ("{"
                            + "\"operationId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872c\","
                            + "\"reservationId\":\"" + reservationId + "\","
                            + "\"reservationStatus\":\"CONFIRMED\","
                            + "\"attemptGroupId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872e\","
                            + "\"attemptGroupStatus\":\"OPEN\","
                            + "\"sessionId\":\"ex_contract\","
                            + "\"confirmedAt\":\"2026-08-28T03:00:02Z\""
                            + "}").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                }
        );
        server.start();

        BillingSagaProperties properties = new BillingSagaProperties();
        properties.setCreationSagaEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        BillingReservationClient client = new SigV4BillingReservationClient(
                properties,
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key", "test-secret")),
                new BillingSagaConfiguration().billingContractObjectMapper()
        );

        client.confirm(
                "018f6f36-2f42-4bf5-8c17-0be35de4872c",
                reservationId,
                "00000000-0000-4000-8000-000000000001",
                "ex_contract",
                Instant.parse("2026-08-28T03:00:01Z")
        );

        assertTrue(body.get().contains(
                "\"sessionCommittedAt\":\"2026-08-28T03:00:01.000Z\""
        ));
    }

    @Test
    void reserveRejectsScalarCoercionInSuccessResponse() throws Exception {
        BillingReservationClient client = clientReturning(
                "/internal/v1/reservations",
                "{"
                        + "\"operationId\":123,"
                        + "\"reservationId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872d\","
                        + "\"reservationKind\":\"INITIAL\","
                        + "\"reservationStatus\":\"RESERVED\","
                        + "\"attemptGroupId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872e\","
                        + "\"sessionId\":\"ex_contract\","
                        + "\"mockExamId\":\"mock_exam_003\","
                        + "\"expiresAt\":\"2026-08-28T03:05:00Z\""
                        + "}"
        );

        BillingClientException failure = assertThrows(
                BillingClientException.class,
                () -> client.reserve(
                        "018f6f36-2f42-4bf5-8c17-0be35de4872c",
                        "00000000-0000-4000-8000-000000000001",
                        "ex_contract",
                        "mock_exam_003"
                )
        );

        assertEquals(BillingClientException.Category.CONTRACT_ERROR, failure.category());
    }

    @Test
    void reserveRejectsNumericEnumInSuccessResponse() throws Exception {
        BillingReservationClient client = clientReturning(
                "/internal/v1/reservations",
                "{"
                        + "\"operationId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872c\","
                        + "\"reservationId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872d\","
                        + "\"reservationKind\":0,"
                        + "\"reservationStatus\":\"RESERVED\","
                        + "\"attemptGroupId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872e\","
                        + "\"sessionId\":\"ex_contract\","
                        + "\"mockExamId\":\"mock_exam_003\","
                        + "\"expiresAt\":\"2026-08-28T03:05:00Z\""
                        + "}"
        );

        BillingClientException failure = assertThrows(
                BillingClientException.class,
                () -> client.reserve(
                        "018f6f36-2f42-4bf5-8c17-0be35de4872c",
                        "00000000-0000-4000-8000-000000000001",
                        "ex_contract",
                        "mock_exam_003"
                )
        );

        assertEquals(BillingClientException.Category.CONTRACT_ERROR, failure.category());
    }

    @Test
    void confirmRejectsMissingRequiredSuccessField() throws Exception {
        String reservationId = "018f6f36-2f42-4bf5-8c17-0be35de4872d";
        BillingReservationClient client = clientReturning(
                "/internal/v1/reservations/" + reservationId + "/confirm",
                "{"
                        + "\"operationId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872c\","
                        + "\"reservationId\":\"" + reservationId + "\","
                        + "\"reservationStatus\":\"CONFIRMED\","
                        + "\"attemptGroupId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872e\","
                        + "\"attemptGroupStatus\":\"OPEN\","
                        + "\"sessionId\":\"ex_contract\""
                        + "}"
        );

        BillingClientException failure = assertThrows(
                BillingClientException.class,
                () -> client.confirm(
                        "018f6f36-2f42-4bf5-8c17-0be35de4872c",
                        reservationId,
                        "00000000-0000-4000-8000-000000000001",
                        "ex_contract",
                        Instant.parse("2026-08-28T03:00:01Z")
                )
        );

        assertEquals(BillingClientException.Category.CONTRACT_ERROR, failure.category());
    }

    @Test
    void phoneContinuationAcceptsOnlyEmpty204AndSendsNoIdempotencyKey() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> idempotencyKey = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/reservations/continuations/phone", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        BillingReservationClient client = testClient();

        assertTrue(client.findPhoneContinuation(
                "00000000-0000-4000-8000-000000000001").isEmpty());
        assertEquals(
                "{\"userId\":\"00000000-0000-4000-8000-000000000001\"}",
                body.get()
        );
        assertEquals(null, idempotencyKey.get());
    }

    @Test
    void phoneContinuationStrictlyDecodesBillingSnapshot() throws Exception {
        String continuationId = "018f6f36-2f42-4bf5-8c17-0be35de4872f";
        String attemptGroupId = "018f6f36-2f42-4bf5-8c17-0be35de4872e";
        BillingReservationClient client = clientReturning(
                "/internal/v1/reservations/continuations/phone",
                "{"
                        + "\"continuationReason\":\"PHONE_REJOIN\","
                        + "\"continuationId\":\"" + continuationId + "\","
                        + "\"attemptGroupId\":\"" + attemptGroupId + "\","
                        + "\"mockExamId\":\"mock_exam_003\""
                        + "}"
        );

        BillingReservationClient.PhoneContinuationSnapshot result =
                client.findPhoneContinuation(
                        "00000000-0000-4000-8000-000000000001").orElseThrow();

        assertEquals(BillingContinuationReason.PHONE_REJOIN, result.continuationReason());
        assertEquals(continuationId, result.continuationId());
        assertEquals(attemptGroupId, result.attemptGroupId());
        assertEquals("mock_exam_003", result.mockExamId());
    }

    @Test
    void phoneContinuationRejectsNonCanonicalAttemptGroupId() throws Exception {
        BillingReservationClient client = clientReturning(
                "/internal/v1/reservations/continuations/phone",
                "{"
                        + "\"continuationReason\":\"PHONE_REJOIN\","
                        + "\"continuationId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872f\","
                        + "\"attemptGroupId\":\"group-existing\","
                        + "\"mockExamId\":\"mock_exam_003\""
                        + "}"
        );

        BillingClientException failure = assertThrows(
                BillingClientException.class,
                () -> client.findPhoneContinuation(
                        "00000000-0000-4000-8000-000000000001")
        );

        assertEquals(BillingClientException.Category.CONTRACT_ERROR, failure.category());
    }

    @Test
    void reserveRejectsUppercaseAttemptGroupUuid() throws Exception {
        BillingReservationClient client = clientReturning(
                "/internal/v1/reservations",
                "{"
                        + "\"operationId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872c\","
                        + "\"reservationId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872d\","
                        + "\"reservationKind\":\"INITIAL\","
                        + "\"reservationStatus\":\"RESERVED\","
                        + "\"attemptGroupId\":\"018F6F36-2F42-4BF5-8C17-0BE35DE4872E\","
                        + "\"sessionId\":\"ex_contract\","
                        + "\"mockExamId\":\"mock_exam_003\","
                        + "\"expiresAt\":\"2026-08-28T03:05:00Z\""
                        + "}"
        );

        BillingClientException failure = assertThrows(
                BillingClientException.class,
                () -> client.reserve(
                        "018f6f36-2f42-4bf5-8c17-0be35de4872c",
                        "00000000-0000-4000-8000-000000000001",
                        "ex_contract",
                        "mock_exam_003"
                )
        );

        assertEquals(BillingClientException.Category.CONTRACT_ERROR, failure.category());
    }

    @Test
    void confirmRejectsNonVersionFourAttemptGroupUuid() throws Exception {
        String reservationId = "018f6f36-2f42-4bf5-8c17-0be35de4872d";
        BillingReservationClient client = clientReturning(
                "/internal/v1/reservations/" + reservationId + "/confirm",
                "{"
                        + "\"operationId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872c\","
                        + "\"reservationId\":\"" + reservationId + "\","
                        + "\"reservationStatus\":\"CONFIRMED\","
                        + "\"attemptGroupId\":\"018f6f36-2f42-1bf5-8c17-0be35de4872e\","
                        + "\"attemptGroupStatus\":\"OPEN\","
                        + "\"sessionId\":\"ex_contract\","
                        + "\"confirmedAt\":\"2026-08-28T03:00:02Z\""
                        + "}"
        );

        BillingClientException failure = assertThrows(
                BillingClientException.class,
                () -> client.confirm(
                        "018f6f36-2f42-4bf5-8c17-0be35de4872c",
                        reservationId,
                        "00000000-0000-4000-8000-000000000001",
                        "ex_contract",
                        Instant.parse("2026-08-28T03:00:01Z")
                )
        );

        assertEquals(BillingClientException.Category.CONTRACT_ERROR, failure.category());
    }

    @Test
    void statusRejectsNonUuidAttemptGroupId() throws Exception {
        BillingReservationClient client = clientReturning(
                "/internal/v1/reservations/status",
                "{"
                        + "\"operationId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872c\","
                        + "\"reservationId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872d\","
                        + "\"reservationKind\":\"INITIAL\","
                        + "\"reservationStatus\":\"RESERVED\","
                        + "\"attemptGroupId\":\"not-a-uuid\","
                        + "\"sessionId\":\"ex_contract\","
                        + "\"mockExamId\":\"mock_exam_003\","
                        + "\"expiresAt\":\"2026-08-28T03:05:00Z\""
                        + "}"
        );

        BillingClientException failure = assertThrows(
                BillingClientException.class,
                () -> client.status(
                        "00000000-0000-4000-8000-000000000001",
                        "018f6f36-2f42-4bf5-8c17-0be35de4872c"
                )
        );

        assertEquals(BillingClientException.Category.CONTRACT_ERROR, failure.category());
    }

    @Test
    void phoneReserveUsesExactSixFieldContractAndReadsContinuationEcho() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        String continuationId = "018f6f36-2f42-4bf5-8c17-0be35de4872f";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/reservations", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("{"
                    + "\"operationId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872c\","
                    + "\"reservationId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872d\","
                    + "\"reservationKind\":\"REPLACEMENT\","
                    + "\"reservationStatus\":\"RESERVED\","
                    + "\"attemptGroupId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872e\","
                    + "\"sessionId\":\"ex_contract\","
                    + "\"mockExamId\":\"mock_exam_003\","
                    + "\"continuationReason\":\"PHONE_REJOIN\","
                    + "\"continuationId\":\"" + continuationId + "\","
                    + "\"expiresAt\":\"2026-08-28T03:05:00Z\""
                    + "}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        BillingReservationClient.ReservationSnapshot result = testClient()
                .reservePhoneContinuation(
                        "018f6f36-2f42-4bf5-8c17-0be35de4872c",
                        "00000000-0000-4000-8000-000000000001",
                        "ex_contract",
                        "mock_exam_003",
                        BillingContinuationReason.PHONE_REJOIN,
                        continuationId,
                        "018f6f36-2f42-4bf5-8c17-0be35de4872e"
                );

        assertTrue(body.get().contains("\"continuationReason\":\"PHONE_REJOIN\""));
        assertTrue(body.get().contains("\"continuationId\":\"" + continuationId + "\""));
        assertTrue(body.get().contains(
                "\"expectedAttemptGroupId\":\"018f6f36-2f42-4bf5-8c17-0be35de4872e\""));
        assertFalse(body.get().contains("null"));
        assertEquals(BillingContinuationReason.PHONE_REJOIN, result.continuationReason());
        assertEquals(continuationId, result.continuationId());
    }

    @Test
    void phoneContinuationRejectsUnexpectedSuccessfulStatus() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/reservations/continuations/phone", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        BillingClientException failure = assertThrows(
                BillingClientException.class,
                () -> testClient().findPhoneContinuation(
                        "00000000-0000-4000-8000-000000000001")
        );

        assertEquals(BillingClientException.Category.CONTRACT_ERROR, failure.category());
    }

    @Test
    void clientSpanTraceparentIsIncludedBeforeSigV4Signing() throws Exception {
        AtomicReference<String> traceparent = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/reservations/continuations/phone", exchange -> {
            exchange.getRequestBody().readAllBytes();
            traceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        ObjectProvider<Tracer> tracerProvider = mock(ObjectProvider.class);
        Tracer tracer = mock(Tracer.class);
        Span parent = mock(Span.class);
        Span child = mock(Span.class);
        Span.Builder spanBuilder = mock(Span.Builder.class);
        TraceContext parentContext = mock(TraceContext.class);
        TraceContext childContext = mock(TraceContext.class);
        Tracer.SpanInScope scope = mock(Tracer.SpanInScope.class);
        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(parent);
        when(parent.context()).thenReturn(parentContext);
        when(tracer.spanBuilder()).thenReturn(spanBuilder);
        when(spanBuilder.name("billing_phone_continuation")).thenReturn(spanBuilder);
        when(spanBuilder.kind(Span.Kind.CLIENT)).thenReturn(spanBuilder);
        when(spanBuilder.setParent(parentContext)).thenReturn(spanBuilder);
        when(spanBuilder.start()).thenReturn(child);
        when(child.context()).thenReturn(childContext);
        when(childContext.traceId()).thenReturn("0123456789abcdef0123456789abcdef");
        when(childContext.spanId()).thenReturn("fedcba9876543210");
        when(childContext.sampled()).thenReturn(true);
        when(tracer.withSpan(child)).thenReturn(scope);

        BillingSagaProperties properties = new BillingSagaProperties();
        properties.setCreationSagaEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setRegion("ap-northeast-2");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));
        BillingReservationClient client = new SigV4BillingReservationClient(
                properties,
                StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-key", "test-secret")),
                new BillingSagaConfiguration().billingContractObjectMapper(),
                tracerProvider,
                new SimpleMeterRegistry()
        );

        client.findPhoneContinuation("00000000-0000-4000-8000-000000000001");

        assertEquals(
                "00-0123456789abcdef0123456789abcdef-fedcba9876543210-01",
                traceparent.get()
        );
        assertTrue(authorization.get().contains("traceparent"));
        verify(child).end();
        verify(scope).close();
    }

    private BillingReservationClient testClient() {
        BillingSagaProperties properties = new BillingSagaProperties();
        properties.setCreationSagaEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setRegion("ap-northeast-2");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));
        return new SigV4BillingReservationClient(
                properties,
                StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-key", "test-secret")),
                new BillingSagaConfiguration().billingContractObjectMapper()
        );
    }

    private BillingReservationClient clientReturning(String path, String json) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        BillingSagaProperties properties = new BillingSagaProperties();
        properties.setCreationSagaEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setRegion("ap-northeast-2");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));
        return new SigV4BillingReservationClient(
                properties,
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key", "test-secret")),
                new BillingSagaConfiguration().billingContractObjectMapper()
        );
    }
}
