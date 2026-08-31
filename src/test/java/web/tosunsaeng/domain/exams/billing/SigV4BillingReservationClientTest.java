package web.tosunsaeng.domain.exams.billing;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
