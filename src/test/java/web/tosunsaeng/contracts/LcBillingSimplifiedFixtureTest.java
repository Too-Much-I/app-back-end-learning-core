package web.tosunsaeng.contracts;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent LC offline contract models; does not execute a service, HTTP chain or worker. */
class LcBillingSimplifiedFixtureTest {
    private static final String ROOT = "/contracts/lc-billing-paid-exam-simplified-exact-r1-fixtures-1/";
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Duration WINDOW = Duration.ofDays(120);
    private static final Set<String> FILES = Set.of("README.md", "contract-snapshot.md", "error-cases.json",
            "manifest.json", "member-resume-cases.json", "phone-snapshot-cases.json", "public-error-examples.json",
            "state-scenarios.json", "time-cases.json");

    @Test
    void immutableBytesAndScope() throws Exception {
        assertEquals("35f7d437fb562e1427efe385ffaf1721a60f4780fcaacf495c2a0c7e787c2436", hash(resource("SHA256SUMS")));
        Set<String> seen = new HashSet<>();
        for (String line : new String(resource("SHA256SUMS"), StandardCharsets.UTF_8).lines().toList()) {
            String[] entry = line.split("  ", -1);
            assertEquals(2, entry.length);
            assertTrue(FILES.contains(entry[1]) && seen.add(entry[1]));
            assertEquals(entry[0], hash(resource(entry[1])), entry[1]);
        }
        assertEquals(FILES, seen);
        assertEquals("79bcfe3d79225c21d712791266bea10d4aed9eed3dc0529ddf86f7b29673a482", hash(resource("contract-snapshot.md")));
        JsonNode m = load("manifest.json");
        assertEquals("lc-billing-paid-exam-simplified-exact-r1-fixtures-1", s(m, "revision"));
        assertEquals("lc-billing-paid-exam-simplified-exact-r1-draft-3", s(m, "contractRevision"));
        assertEquals(hash(resource("contract-snapshot.md")), s(m, "contractSha256"));
        for (String flag : List.of("runtimeTested", "httpExecuted", "lcIndependentValidation")) assertFalse(b(m, flag));
        assertEquals(JSON.readTree("[\"deletion-route-auth\",\"historical-owner-deletion\",\"final-record-retention-purge\"]"), m.get("deferred"));
        Set<String> ids = new HashSet<>();
        for (String file : List.of("error-cases.json", "public-error-examples.json", "time-cases.json",
                "member-resume-cases.json", "phone-snapshot-cases.json", "state-scenarios.json"))
            for (JsonNode c : load(file)) assertTrue(ids.add(s(c, "id")));
        JsonNode states = load("state-scenarios.json");
        assertEquals(28, states.size());
        Set<String> expected = new HashSet<>();
        for (var group : Map.of("E", 6, "T", 6, "P", 5, "C", 3).entrySet())
            for (int i = 1; i <= group.getValue(); i++) expected.add(group.getKey() + String.format("%02d", i));
        for (char c = 'a'; c <= 'h'; c++) expected.add("E02-" + c);
        Set<String> actual = new HashSet<>();
        String contract = new String(resource("contract-snapshot.md"), StandardCharsets.UTF_8);
        for (JsonNode c : states) {
            actual.add(s(c, "id"));
            assertEquals("NOT_EXECUTED_REQUIRES_IMPLEMENTATION", s(c, "execution"));
            assertTrue(contract.contains(s(c, "requirement")));
        }
        assertEquals(expected, actual);
    }

    @TestFactory Stream<DynamicTest> errorVectors() throws Exception {
        return cases("error-cases.json", 22, c -> assertEquals(s(c, "expected"), error(c.get("input"))));
    }
    private static String error(JsonNode input) {
        try {
            JsonNode body = strict(Base64.getDecoder().decode(s(input, "rawBodyBase64")));
            require(names(body).equals(Set.of("code", "message", "retryable", "correlationId")));
            String code = s(body, "code"); s(body, "message"); s(body, "correlationId");
            boolean retry = b(body, "retryable");
            String endpoint = s(input, "endpoint");
            require(Set.of("reserve", "status").contains(endpoint));
            int http = input.get("http").intValue();
            return switch (code) {
                case "MEMBER_EVIDENCE_PENDING" -> http == 503 && retry ? "WAITING_MEMBER" : "CONTRACT_ERROR";
                case "BILLING_TEMPORARILY_UNAVAILABLE" -> http == 503 && retry ? "TEMPORARY_FAILURE" : "CONTRACT_ERROR";
                case "OPERATION_OWNER_MISMATCH" -> http == 409 && !retry ? "OWNER_BLOCKED" : "CONTRACT_ERROR";
                case "RESERVE_REJECTED_BEFORE_CREATION" -> http == 409 && !retry && endpoint.equals("status") ? "PRE_RESERVE_TERMINAL" : "CONTRACT_ERROR";
                case "OPERATION_NOT_FOUND" -> http == 404 && !retry && endpoint.equals("status") ? "OPERATION_NOT_FOUND" : "CONTRACT_ERROR";
                default -> "CONTRACT_ERROR";
            };
        } catch (Exception rejected) { return "CONTRACT_ERROR"; }
    }

    @TestFactory Stream<DynamicTest> publicExamples() throws Exception {
        return cases("public-error-examples.json", 4, c -> {
            record PublicError(int status, String code, String message, String retry) {}
            PublicError e = switch (s(c, "kind")) {
                case "member" -> new PublicError(503, "BILLING_TEMPORARILY_UNAVAILABLE", "시험 사용권을 일시적으로 확인할 수 없습니다.", "1");
                case "delete-during-creation" -> new PublicError(409, "EXAM_CREATION_PROCESSING", "시험 준비 중입니다. 잠시 후 다시 시도해 주세요.", "1");
                case "creation-processing" -> new PublicError(409, "EXAM_CREATION_PROCESSING", "시험 생성이 처리 중입니다. 같은 요청으로 다시 시도해 주세요.", "1");
                case "deleted-replay" -> new PublicError(404, "EXAM_4004", "해당 모의고사 세션을 찾을 수 없습니다.", null);
                default -> throw new IllegalArgumentException("Unknown example");
            };
            JsonNode body = strict(Base64.getDecoder().decode(s(c, "rawBodyBase64")));
            assertEquals(Set.of("isSuccess", "code", "message"), names(body));
            assertFalse(b(body, "isSuccess"));
            assertEquals(e.code(), s(body, "code")); assertEquals(e.message(), s(body, "message"));
            assertEquals(e.status(), c.get("http").intValue());
            assertEquals(e.retry(), c.get("retryAfter").isNull() ? null : s(c, "retryAfter"));
        });
    }

    @TestFactory Stream<DynamicTest> timeVectors() throws Exception {
        return cases("time-cases.json", 14, c -> {
            Instant now = instant(s(c, "now"));
            if (c.has("firstAcceptedAt")) {
                Instant end = instant(s(c, "firstAcceptedAt")).plus(WINDOW);
                assertEquals(instant(s(c, "expectedGuaranteeUntil")), end);
                assertEquals(b(c, "expectedWithin"), now.isBefore(end));
            } else {
                boolean eligible = !now.isBefore(instant(s(c, "terminalAt")).plus(WINDOW)) && !b(c, "unresolved") && !b(c, "hold");
                assertEquals(b(c, "expectedPurgeEligible"), eligible);
            }
        });
    }
    private static Instant instant(String value) {
        require(value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z"));
        return Instant.parse(value);
    }

    @TestFactory Stream<DynamicTest> resumeModels() throws Exception {
        return cases("member-resume-cases.json", 29, c -> assertEquals(s(c, "expected"), resume(c.get("input"))));
    }
    private static String resume(JsonNode n) {
        String quarantine = s(n, "quarantine");
        if (!b(n, "authAllowed") || quarantine.equals("BLOCKED_AUTH")) return "AUTH_BLOCKED";
        if (!b(n, "ownerAllowed") || quarantine.equals("BLOCKED_OWNER")) return "OWNER_BLOCKED";
        if (!b(n, "leaseValid") || !b(n, "versionMatches") || !b(n, "ownsGuard")) return "RELOAD_REQUIRED";
        if (!s(n, "phase").equals("PREPARED")) return "EXISTING_STATE_RECOVERY";
        for (String key : List.of("newSessionExists", "commitMarker", "reservationKnown", "cancelSent", "cancelUnknown"))
            if (b(n, key)) return "REVIEW_REQUIRED";
        if (!b(n, "withinWindow")) return "REVIEW_REQUIRED";
        if (!s(n, "remote").equals("MEMBER_PENDING")) return "STATUS_RECOVERY_REQUIRED";
        boolean evidence = b(n, "memberWaitPersisted") && !b(n, "mixedCause");
        boolean isolation = quarantine.equals("NONE") || quarantine.equals("NEEDS_REVIEW")
                && n.path("reason").asText().equals("retry_exhausted") && b(n, "exhaustionLinked");
        boolean intent = Set.of("WAITING_MEMBER", "CLEANUP_PRECOMMIT").contains(s(n, "intent"));
        return evidence && isolation && intent ? "HTTP_RESUME_ALLOWED" : "REVIEW_REQUIRED";
    }

    @TestFactory Stream<DynamicTest> phoneSelectedFieldsOnly() throws Exception {
        return cases("phone-snapshot-cases.json", 11, c -> assertEquals(b(c, "expectedValid"), phone(c)));
    }
    private static boolean phone(JsonNode c) {
        try {
            JsonNode q = c.get("request"), r = c.get("reserveSnapshot"), status = c.get("statusSnapshot");
            Set<String> fields = Set.of("reservationKind", "entitlementSource", "attemptGroupId", "sessionId", "mockExamId", "continuationReason", "continuationId");
            require(names(r).equals(fields) && names(status).equals(fields) && r.equals(status));
            for (String key : fields) s(r, key);
            require(s(q, "continuationReason").equals("PHONE_REJOIN"));
            for (String key : List.of("sessionId", "mockExamId", "continuationReason", "continuationId")) require(s(q, key).equals(s(r, key)));
            boolean sameGroup = s(q, "expectedAttemptGroupId").equals(s(r, "attemptGroupId"));
            return switch (s(r, "reservationKind")) {
                case "INITIAL" -> !sameGroup && s(r, "entitlementSource").equals("SUBSCRIPTION");
                case "REPLACEMENT" -> sameGroup && s(r, "entitlementSource").equals("EXAM_PASS");
                default -> false;
            };
        } catch (Exception rejected) { return false; }
    }

    private static JsonNode strict(byte[] bytes) throws Exception {
        try {
            String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            require(!value.startsWith("\ufeff"));
            JsonNode n = JSON.readTree(value); require(n != null); scalar(n); return n;
        } catch (Exception rejected) { throw new IllegalArgumentException("Invalid fixture JSON"); }
    }
    private static void scalar(JsonNode n) {
        if (n.isTextual()) n.textValue().codePoints().forEach(cp -> require(cp < 0xd800 || cp > 0xdfff));
        if (n.isObject()) n.fieldNames().forEachRemaining(k -> k.codePoints().forEach(cp -> require(cp < 0xd800 || cp > 0xdfff)));
        if (n.isContainerNode()) n.forEach(LcBillingSimplifiedFixtureTest::scalar);
    }
    private static Set<String> names(JsonNode n) { require(n != null && n.isObject()); Set<String> result = new HashSet<>(); n.fieldNames().forEachRemaining(result::add); return result; }
    private static boolean b(JsonNode n, String key) { require(n.has(key) && n.get(key).isBoolean()); return n.get(key).booleanValue(); }
    private static String s(JsonNode n, String key) { require(n.has(key) && n.get(key).isTextual()); return n.get(key).textValue(); }
    private static void require(boolean ok) { if (!ok) throw new IllegalArgumentException("Invalid contract input"); }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static byte[] resource(String file) throws Exception { try (var in = LcBillingSimplifiedFixtureTest.class.getResourceAsStream(ROOT + file)) { if (in == null) throw new IllegalStateException("Missing fixture"); return in.readAllBytes(); } }
    private static JsonNode load(String file) throws Exception { return strict(resource(file)); }
    private interface Check { void run(JsonNode c) throws Exception; }
    private static Stream<DynamicTest> cases(String file, int count, Check check) throws Exception {
        JsonNode list = load(file); assertTrue(list.isArray()); assertEquals(count, list.size());
        return StreamSupport.stream(list.spliterator(), false).map(c -> DynamicTest.dynamicTest(s(c, "id"), () -> check.run(c)));
    }
}
