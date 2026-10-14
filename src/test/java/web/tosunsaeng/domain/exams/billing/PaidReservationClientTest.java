package web.tosunsaeng.domain.exams.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DynamicTest;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class PaidReservationClientTest {
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = new BillingSagaConfiguration().billingContractObjectMapper();
    private HttpServer server;
    private JwtBillingReservationClient client;
    private String response;
    private byte[] rawResponse;
    private int httpStatus = 200;
    private String retryHeader;
    private String lastBody;
    private String lastKey;
    private final List<String> tokens = new ArrayList<>();
    private static final String OP = "00000005-1111-4111-8111-111111111111";
    private static final String USER = "00000002-1111-4111-8111-111111111111";
    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/reservations", exchange -> {
            lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            tokens.add(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = rawResponse == null ? response.getBytes(StandardCharsets.UTF_8) : rawResponse;
            if (retryHeader != null) exchange.getResponseHeaders().add("Retry-After", retryHeader);
            exchange.sendResponseHeaders(httpStatus, httpStatus == 204 ? -1 : bytes.length);
            if (httpStatus != 204) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        var properties = new BillingSagaProperties(); properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        client = new JwtBillingReservationClient(properties, WorkloadTestKeys.issuer(), mapper);
    }
    @AfterEach void stop() { server.stop(0); }
    JsonNode simplified(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/contracts/lc-billing-paid-exam-simplified-exact-r1-fixtures-2/" + name)) {
            return mapper.readTree(input);
        }
    }
    @TestFactory Stream<DynamicTest> strictErrorsThroughActualHttp() throws Exception {
        var tests = new ArrayList<DynamicTest>();
        for (JsonNode row : simplified("error-cases.json")) tests.add(DynamicTest.dynamicTest(row.get("id").asText(), () -> {
            JsonNode input = row.get("input");
            httpStatus = input.get("http").asInt();
            retryHeader = input.path("retryAfter").isTextual() ? input.get("retryAfter").asText() : null;
            rawResponse = Base64.getDecoder().decode(input.get("rawBodyBase64").asText());
            String expected = switch (row.get("expected").asText()) {
                case "WAITING_MEMBER" -> "MEMBER_EVIDENCE_PENDING";
                case "PRE_RESERVE_TERMINAL" -> "PRE_RESERVE_REJECTED"; // Evidence only; no terminal mutation.
                case "TEMPORARY_FAILURE" -> "TEMPORARILY_UNAVAILABLE";
                default -> row.get("expected").asText();
            };
            var error = catchThrowable(() -> {
                switch (input.get("endpoint").asText()) {
                    case "status" -> client.statusPaid(USER, OP);
                    case "confirm" -> client.confirmPaid(OP,"00000006-1111-4111-8111-111111111111",USER,"new",java.time.Instant.EPOCH);
                    case "cancel" -> client.cancelPaid(OP,"00000006-1111-4111-8111-111111111111",USER);
                    case "reserve" -> client.reservePaid(OP, new PaidReservationProtocol.Request(USER,"new","mock",null));
                    default -> throw new AssertionError("Unhandled fixture endpoint");
                }
            });
            assertThat(error).isInstanceOf(BillingClientException.class).hasNoCause();
            assertThat(((BillingClientException)error).category().name()).isEqualTo(expected);
            httpStatus = 200; retryHeader = null; rawResponse = null;
        }));
        return tests.stream();
    }
    @Test void originalOwnerFixtureStillFailsClosed() throws Exception {
        try(var in=getClass().getResourceAsStream("/contracts/lc-billing-paid-exam-simplified-exact-r1-fixtures-1/error-cases.json")) {
            for(JsonNode row:mapper.readTree(in)) if(row.get("id").asText().equals("E06-owner")) {
                httpStatus=409;
                rawResponse=Base64.getDecoder().decode(row.get("input").get("rawBodyBase64").asText());
                assertThatThrownBy(()->client.statusPaid(USER,OP)).isInstanceOf(BillingClientException.class)
                        .extracting(e->((BillingClientException)e).category()).isEqualTo(BillingClientException.Category.CONTRACT_ERROR);
                return;
            }
        }
        throw new AssertionError("Missing original owner fixture");
    }
    @Test void correctOwnerMessageAndDiscoveryPendingNeverBecomeFreeFallback() {
        httpStatus = 409;
        response = "{\"code\":\"OPERATION_OWNER_MISMATCH\",\"message\":\"The operation owner does not match the current owner.\",\"retryable\":false,\"correlationId\":\"opaque\"}";
        assertThatThrownBy(() -> client.statusPaid(USER, OP)).isInstanceOf(BillingClientException.class)
                .extracting(e -> ((BillingClientException)e).category()).isEqualTo(BillingClientException.Category.OWNER_BLOCKED);
        response = "{\"code\":\"COMMAND_PROCESSING\",\"message\":\"Pending\",\"retryable\":true,\"correlationId\":\"opaque\"}";
        retryHeader = "1";
        assertThatThrownBy(() -> client.findPaidPhoneContinuation(USER)).isInstanceOf(BillingClientException.class)
                .extracting(e -> ((BillingClientException)e).category()).isEqualTo(BillingClientException.Category.PROCESSING);
        assertThat(tokens).hasSize(2); // Only the requested calls: no reserve/fallback call.
        httpStatus = 204; response = ""; retryHeader = null;
        assertThat(client.findPaidPhoneContinuation(USER)).isEmpty();
    }
    @TestFactory Stream<DynamicTest> phoneSelectedFieldFixturesWithExplicitSyntheticDtoEnvelope() throws Exception {
        var tests = new ArrayList<DynamicTest>();
        for (JsonNode row : simplified("phone-snapshot-cases.json")) tests.add(DynamicTest.dynamicTest(row.get("id").asText(), () -> {
            ObjectNode request = row.get("request").deepCopy(); request.put("userId", USER);
            var phone = mapper.treeToValue(request, PaidReservationProtocol.PhoneRequest.class);
            var failure = catchThrowable(() -> {
                response = fullPhoneEnvelope(row.get("reserveSnapshot"));
                var reserved = client.reservePaidPhone(OP, phone);
                assertThat(mapper.readTree(lastBody)).isEqualTo(request);
                response = fullPhoneEnvelope(row.get("statusSnapshot"));
                var status = client.statusPaid(USER, OP);
                PaidReservationProtocol.matchesPhone(OP, phone, status);
                new web.tosunsaeng.domain.exams.domain.entity.PaidReservationEvidence(reserved).observe(status);
            });
            if (row.get("expectedValid").asBoolean()) assertThat(failure).isNull();
            else assertThat(failure).isInstanceOf(BillingClientException.class);
        }));
        return tests.stream();
    }
    String fullPhoneEnvelope(JsonNode selected) {
        ObjectNode full = selected.deepCopy();
        full.put("operationId",OP); full.put("reservationId","00000006-1111-4111-8111-111111111111");
        full.put("reservationStatus","RESERVED"); full.put("expiresAt","2026-10-14T01:00:00Z");
        return full.toString();
    }
    @Test void paidDiscoveryStrict200AndAbbreviatedConfirmCancelRemainSeparateFromFullEvidence() throws Exception {
        String group = "00000003-1111-4111-8111-111111111111", reservation = "00000006-1111-4111-8111-111111111111";
        ObjectNode discovery = mapper.createObjectNode().put("continuationReason","PHONE_REJOIN")
                .put("continuationId",OP).put("attemptGroupId",group).put("mockExamId","mock");
        response = discovery.toString();
        assertThat(client.findPaidPhoneContinuation(USER).orElseThrow().attemptGroupId()).isEqualTo(group);
        assertThat(lastKey).isNull();
        discovery.put("extra",true); response = discovery.toString();
        assertThatThrownBy(() -> client.findPaidPhoneContinuation(USER)).isInstanceOf(BillingClientException.class);
        ObjectNode confirm = mapper.createObjectNode().put("operationId",OP).put("reservationId",reservation)
                .put("reservationStatus","CONFIRMED").put("attemptGroupId",group).put("attemptGroupStatus","OPEN")
                .put("sessionId","new").put("confirmedAt","2026-10-14T00:00:00Z");
        response = confirm.toString();
        var snapshot = client.confirmPaid(OP,reservation,USER,"new",java.time.Instant.EPOCH);
        assertThat(snapshot.entitlementSource()).isNull();
        assertThat(lastKey).isEqualTo(OP);
        assertThatThrownBy(() -> new web.tosunsaeng.domain.exams.domain.entity.PaidReservationEvidence(snapshot))
                .isInstanceOf(BillingClientException.class);
        confirm.put("sessionId","other"); response = confirm.toString();
        assertThatThrownBy(() -> client.confirmPaid(OP,reservation,USER,"new",java.time.Instant.EPOCH))
                .isInstanceOf(BillingClientException.class);
        response = mapper.createObjectNode().put("operationId",OP).put("reservationId",reservation)
                .put("reservationStatus","CANCELED").put("canceledAt","2026-10-14T00:00:00Z").toString();
        assertThat(client.cancelPaid(OP,reservation,USER).reservationStatus()).isEqualTo(BillingReservationClient.ReservationStatus.CANCELED);
    }
    JsonNode fixtures(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/contracts/lc-billing-paid-exam-r2-fixtures-2/" + name)) {
            return mapper.readTree(input);
        }
    }
    PaidReservationProtocol.Request request(JsonNode row) throws Exception {
        return mapper.treeToValue(row.get("request"), PaidReservationProtocol.Request.class);
    }
    @TestFactory Stream<DynamicTest> wireFixturesThroughActualHttpClient() throws Exception {
        var tests = new ArrayList<DynamicTest>();
        for (JsonNode row : fixtures("wire-examples.json")) tests.add(DynamicTest.dynamicTest(row.get("id").asText(), () -> {
            response = row.get("response").toString();
            var snapshot = client.reservePaid(OP, request(row));
            assertThat(snapshot.entitlementSource().name()).isEqualTo(row.at("/response/entitlementSource").asText());
            assertThat(mapper.readTree(lastBody)).isEqualTo(row.get("request"));
            assertThat(lastKey).isEqualTo(OP);
        }));
        return tests.stream();
    }
    @TestFactory Stream<DynamicTest> statusFixturesThroughActualHttpClient() throws Exception {
        var tests = new ArrayList<DynamicTest>();
        for (JsonNode row : fixtures("status-examples.json")) tests.add(DynamicTest.dynamicTest(row.get("id").asText(), () -> {
            response = row.get("response").toString();
            var snapshot = client.statusPaid(USER, OP);
            assertThat(snapshot.reservationStatus().name()).isEqualTo(row.at("/response/reservationStatus").asText());
            assertThat(lastKey).isNull(); assertThat(mapper.readTree(lastBody)).isEqualTo(row.get("request"));
            assertThat(snapshot.confirmRejection() != null).isEqualTo(row.get("response").has("confirmRejection"));
        }));
        return tests.stream();
    }
    @Test void strictInvalidResponsesFailClosed() throws Exception {
        JsonNode row = fixtures("wire-examples.json").get(2);
        String valid = row.get("response").toString();
        List<String> invalid = new ArrayList<>(List.of(" ", "null", "[]", valid + " {}", valid.replace("\"SUBSCRIPTION\"", "1"),
                valid.replace("\"SUBSCRIPTION\"", "\"UNKNOWN\""), valid.replace("\"transition\":{", "\"transition\":{\"type\":\"TRIAL_TO_PAID\","),
                valid.replace("\"entitlementSource\":", "\"entitlementSource\":\"EXAM_PASS\",\"entitlementSource\":")));
        for (String field : List.of("entitlementSource", "transition")) {
            ObjectNode missing = row.get("response").deepCopy(); missing.remove(field); invalid.add(missing.toString());
            ObjectNode explicitNull = row.get("response").deepCopy(); explicitNull.putNull(field); invalid.add(explicitNull.toString());
        }
        for (String field : List.of("previousSessionId", "previousAttemptGroupId")) {
            ObjectNode partial = row.get("response").deepCopy(); ((ObjectNode)partial.get("transition")).remove(field); invalid.add(partial.toString());
        }
        for (String bad : invalid) {
            response = bad;
            assertThatThrownBy(() -> client.reservePaid(OP, request(row))).isInstanceOf(BillingClientException.class)
                    .extracting(e -> ((BillingClientException)e).category()).isEqualTo(BillingClientException.Category.CONTRACT_ERROR);
        }
    }
    @Test void wrongOriginAndUnexpectedReplacementAreRejected() throws Exception {
        JsonNode row = fixtures("wire-examples.json").get(3);
        for (String field : List.of("sessionId", "mockExamId", "operationId")) {
            ObjectNode bad = row.get("response").deepCopy(); bad.put(field, "wrong"); response = bad.toString();
            assertThatThrownBy(() -> client.reservePaid(OP, request(row))).isInstanceOf(BillingClientException.class);
        }
        ObjectNode bad = row.get("response").deepCopy(); bad.put("reservationKind", "REPLACEMENT"); bad.remove("transition");
        response = bad.toString();
        assertThatThrownBy(() -> client.reservePaid(OP, request(row))).isInstanceOf(BillingClientException.class);
        bad = row.get("response").deepCopy(); ((ObjectNode)bad.get("transition")).put("previousSessionId", "other");
        response = bad.toString();
        assertThatThrownBy(() -> client.reservePaid(OP, request(row))).isInstanceOf(BillingClientException.class);
    }
    @Test void rejectionOnlyAllowedInCanceledStatusAndNeverReserve() throws Exception {
        ObjectNode valid = (ObjectNode)fixtures("status-examples.json").get(0).get("response");
        for (String status : List.of("RESERVED", "CONFIRMED", "EXPIRED")) {
            ObjectNode bad=valid.deepCopy(); bad.put("reservationStatus", status); response=bad.toString();
            assertThatThrownBy(() -> client.statusPaid(USER, OP)).isInstanceOf(BillingClientException.class);
        }
        ObjectNode bad=valid.deepCopy(); bad.putNull("confirmRejection"); response=bad.toString();
        assertThatThrownBy(() -> client.statusPaid(USER, OP)).isInstanceOf(BillingClientException.class);
        response=valid.toString();
        assertThatThrownBy(() -> client.reservePaid(OP, new PaidReservationProtocol.Request(USER,"session-new","mock-a",null)))
                .isInstanceOf(BillingClientException.class);
    }
    @Test void replayKeepsRequestButRefreshesJwtAndLegacySourceAbsenceStillWorks() throws Exception {
        JsonNode row=fixtures("wire-examples.json").get(0); response=row.get("response").toString();
        client.reservePaid(OP,request(row)); String first=lastBody;
        client.reservePaid(OP,request(row)); assertThat(lastBody).isEqualTo(first);
        assertThat(tokens.get(0)).isNotEqualTo(tokens.get(1));
        ObjectNode old=row.get("response").deepCopy(); old.remove("entitlementSource"); response=old.toString();
        assertThat(client.reserve(OP,USER,"session-new","mock-a").entitlementSource()).isNull();
        assertThatThrownBy(()->client.reservePaid(OP,request(row))).isInstanceOf(BillingClientException.class);
    }
}
