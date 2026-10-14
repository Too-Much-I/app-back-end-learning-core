package web.tosunsaeng.contracts;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/** LC-owned offline oracle. No production components, HTTP requests, DBs or Billing verifier code. */
class LcBillingPaidFixtureTest {
    private static final String ROOT = "/contracts/lc-billing-paid-exam-r2-fixtures-2/";
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final String UUID4 = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
    private static final List<String> EVENT = List.of("eventId", "eventType", "schemaVersion", "producer",
            "consumerScopeId", "occurredAt", "userId", "attemptGroupId", "reason", "confirmedSessionId");
    private static final Set<String> FILES = Set.of("README.md", "canonical-cases.json", "clock-cases.json",
            "http-cases.json", "invalid-event-cases.json", "manifest.json", "public-current.pem",
            "public-jwks.json", "public-next.pem", "state-scenarios.json", "status-examples.json",
            "token-cases.json", "wire-examples.json");

    @Test
    void originalManifestAndAllBytes() throws Exception {
        assertEquals("bb4458f3e7b6ed801795fbd652fc41e2d5e6c6c2d752b9ef693d6f43e4893659", hash(resource("SHA256SUMS")));
        Set<String> seen = new HashSet<>();
        for (String line : new String(resource("SHA256SUMS"), StandardCharsets.UTF_8).lines().toList()) {
            String[] p = line.split("  ", -1);
            assertEquals(2, p.length);
            assertTrue(FILES.contains(p[1]) && seen.add(p[1]));
            assertEquals(p[0], hash(resource(p[1])), p[1]);
        }
        assertEquals(FILES, seen);
        JsonNode m = load("manifest.json");
        assertEquals("lc-billing-paid-exam-r2-fixtures-2", text(m, "revision"));
        assertEquals("lc-billing-paid-exam-contract-r2-draft-2", text(m, "contractRevision"));
        assertEquals("3210c7eef1fb2f9fdde101c2384c86a5bbde1f9be346da0651c971e357444c52", text(m, "contractSha256"));
        assertEquals(JSON.readTree("[\"S15\",\"V07\"]"), m.get("deferred"));
        assertFalse(m.get("runtimeTested").booleanValue());
        // Keep the producer's original manifest; LC results belong in a separate receipt.
        assertFalse(m.get("lcIndependentValidation").booleanValue());
        JsonNode scenarios = load("state-scenarios.json");
        assertEquals(26, scenarios.size());
        for (JsonNode c : scenarios) assertEquals("NOT_EXECUTED_REQUIRES_IMPLEMENTATION", text(c, "execution"));
    }

    @TestFactory
    Stream<DynamicTest> canonicalBytes() throws Exception {
        return cases("canonical-cases.json", 18, c -> {
            byte[] raw = Base64.getDecoder().decode(text(c, "inputBase64"));
            JsonNode value = strict(raw);
            String result;
            if ("event".equals(text(c, "kind"))) {
                assertEquals(204, eventStatus(raw));
                var ordered = JSON.createObjectNode();
                EVENT.forEach(k -> ordered.set(k, value.get(k)));
                result = canonical(ordered);
            } else result = canonical(value);
            byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
            assertTrue(Arrays.equals(Base64.getDecoder().decode(text(c, "expectedCanonicalBase64")), bytes), "canonical bytes");
            assertTrue(value.equals(strict(bytes)), "round trip");
            assertEquals(text(c, "expectedSha256"), hash(bytes));
        });
    }

    @TestFactory
    Stream<DynamicTest> invalidEvents() throws Exception {
        return cases("invalid-event-cases.json", 13, c -> {
            int result = eventStatus(Base64.getDecoder().decode(text(c, "inputBase64")));
            assertEquals(c.get("expectedStatus").intValue(), result);
            assertEquals(text(c, "expectedCode"), result == 422 ? "UNSUPPORTED_CONTRACT" : "INVALID_REQUEST");
        });
    }

    @TestFactory
    Stream<DynamicTest> exactNanosecondClock() throws Exception {
        return cases("clock-cases.json", 5, c -> {
            Instant now = Instant.parse(text(c, "now"));
            BigInteger nanos = BigInteger.valueOf(now.getEpochSecond()).multiply(BigInteger.valueOf(1_000_000_000))
                    .add(BigInteger.valueOf(now.getNano()));
            assertEquals(new BigInteger(text(c, "expectedEpochNanos")), nanos);
        });
    }

    @TestFactory
    Stream<DynamicTest> rawSignedTokens() throws Exception {
        Map<String, Map<String, PublicKey>> sets = keySets(); // Setup failures must fail, not become expected 401.
        return cases("token-cases.json", 31, c -> {
            Map<String, PublicKey> keys = sets.get(text(c, "keySet"));
            assertNotNull(keys);
            assertEquals(c.get("expectedAuthStatus").intValue(),
                    authenticate(text(c, "token"), Instant.parse(text(c, "now")), keys));
        });
    }

    @TestFactory
    Stream<DynamicTest> offlineHttpDecisionTableNotServerExecution() throws Exception {
        Map<String, PublicKey> keys = keySets().get("current");
        return cases("http-cases.json", 12, c -> {
            String path = c.path("path").asText();
            String namespace = "/internal/v1/entitlements/access";
            if (c.has("expectedDispatch")) {
                assertFalse(path.equals(namespace) || path.startsWith(namespace + "/"));
                assertEquals("NOT_OWNED_BY_REVOKE_CHAIN", text(c, "expectedDispatch"));
                return;
            }
            if ("unbound".equals(text(c, "port"))) {
                assertEquals("CONNECTION_FAILURE", text(c, "expectedTransport"));
                return;
            }
            int status;
            if (!c.path("enabled").asBoolean() || !"workload".equals(text(c, "port"))) status = 404;
            else if (c.hasNonNull("query")) status = 401;
            else {
                String header = c.path("authorization").asText("");
                status = header.matches("Bearer [A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
                        ? authenticate(header.substring(7), Instant.parse("2026-10-14T00:00:00Z"), keys) : 401;
                if (status == 204 && (!"POST".equals(c.path("method").asText())
                        || !path.equals(namespace + "/events"))) status = 403;
            }
            assertEquals(c.path("expectedStatus").intValue(), status);
        });
    }

    @TestFactory
    Stream<DynamicTest> wireExamplesStructuralOnly() throws Exception {
        return cases("wire-examples.json", 6, c -> {
            JsonNode q = c.get("request"), a = c.get("response");
            fields(q, Set.of("userId", "sessionId", "mockExamId"), Set.of("restartContext"));
            assertTrue(text(q, "userId").matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"));
            snapshot(a);
            assertEquals("RESERVED", text(a, "reservationStatus"));
            assertEquals(text(q, "sessionId"), text(a, "sessionId"));
            assertEquals(text(q, "mockExamId"), text(a, "mockExamId"));
            if (!q.has("restartContext")) {
                assertEquals("INITIAL", text(a, "reservationKind"));
                assertFalse(a.has("transition"));
                return;
            }
            JsonNode ctx = q.get("restartContext");
            fields(ctx, Set.of("previousAttemptGroupId", "previousSessionId", "startReason"), Set.of());
            assertTrue(text(ctx, "previousAttemptGroupId").matches(UUID4));
            assertNotEquals(text(ctx, "previousSessionId"), text(q, "sessionId"));
            String reason = text(ctx, "startReason");
            assertTrue(Set.of("RESTART_INCOMPLETE", "NEW_AFTER_SUBMISSION", "NEW_AFTER_COMPLETION").contains(reason));
            if (!reason.equals("RESTART_INCOMPLETE")) assertEquals("INITIAL", text(a, "reservationKind"));
            if (text(a, "reservationKind").equals("INITIAL")) {
                JsonNode transition = a.get("transition");
                fields(transition, Set.of("previousAttemptGroupId", "previousSessionId"), Set.of());
                assertEquals(ctx.get("previousAttemptGroupId"), transition.get("previousAttemptGroupId"));
                assertEquals(ctx.get("previousSessionId"), transition.get("previousSessionId"));
                assertNotEquals(text(ctx, "previousAttemptGroupId"), text(a, "attemptGroupId"));
            } else {
                assertEquals(text(ctx, "previousAttemptGroupId"), text(a, "attemptGroupId"));
                assertFalse(a.has("transition"));
            }
        });
    }

    @TestFactory
    Stream<DynamicTest> statusExamplesStructuralOnly() throws Exception {
        return cases("status-examples.json", 3, c -> {
            JsonNode q = c.get("request"), a = c.get("response");
            fields(q, Set.of("userId", "operationId"), Set.of());
            snapshot(a);
            assertEquals(text(q, "operationId"), text(a, "operationId"));
            assertTrue(Set.of("CANCELED", "CONFIRMED").contains(text(a, "reservationStatus")));
            Instant.parse(text(a, "terminalAt"));
            if (a.has("confirmRejection")) {
                assertEquals("CANCELED", text(a, "reservationStatus"));
                fields(a.get("confirmRejection"), Set.of("code"), Set.of());
                assertEquals("ENTITLEMENT_REVOKED", text(a.get("confirmRejection"), "code"));
            }
        });
    }

    private static void snapshot(JsonNode a) {
        fields(a, Set.of("operationId", "reservationId", "reservationKind", "reservationStatus", "attemptGroupId",
                "sessionId", "mockExamId", "expiresAt", "entitlementSource"),
                Set.of("transition", "terminalAt", "confirmRejection", "attemptGroupStatus"));
        for (String k : List.of("operationId", "reservationId", "attemptGroupId")) require(text(a, k).matches(UUID4));
        for (String k : List.of("sessionId", "mockExamId")) require(text(a, k).length() <= 128);
        require(Set.of("INITIAL", "REPLACEMENT").contains(text(a, "reservationKind")));
        require(Set.of("EXAM_PASS", "SUBSCRIPTION").contains(text(a, "entitlementSource")));
        Instant.parse(text(a, "expiresAt"));
    }

    private static int eventStatus(byte[] bytes) {
        if (bytes.length > 16384) return 413;
        JsonNode n;
        try {
            n = strict(bytes);
            fields(n, new HashSet<>(EVENT), Set.of());
            for (String k : List.of("eventId", "attemptGroupId")) require(text(n, k).matches(UUID4));
            require(text(n, "userId").matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"));
            require(n.get("schemaVersion").isIntegralNumber());
            for (String k : List.of("eventType", "producer", "consumerScopeId", "reason")) text(n, k);
            String at = text(n, "occurredAt");
            require(at.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z"));
            require(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withResolverStyle(java.time.format.ResolverStyle.STRICT).parse(at) != null);
            JsonNode session = n.get("confirmedSessionId");
            require(session.isNull() || session.isTextual() && session.textValue().length() >= 1
                    && session.textValue().codePointCount(0, session.textValue().length()) <= 128);
        } catch (Exception rejected) { return 400; }
        return n.get("schemaVersion").bigIntegerValue().equals(BigInteger.ONE)
                && text(n, "eventType").equals("AttemptGroupAccessRevoked") && text(n, "producer").equals("billing")
                && text(n, "consumerScopeId").equals("fixture-test")
                && Set.of("PURCHASE_REFUNDED", "PURCHASE_REVOKED").contains(text(n, "reason")) ? 204 : 422;
    }

    private static String canonical(JsonNode n) {
        if (n.isNull()) return "null";
        if (n.isIntegralNumber()) return n.bigIntegerValue().toString();
        if (n.isTextual()) {
            StringBuilder s = new StringBuilder("\"");
            n.textValue().codePoints().forEach(cp -> {
                require(cp < 0xd800 || cp > 0xdfff);
                if (cp < 32) s.append(String.format(Locale.ROOT, "\\u%04x", cp));
                else { if (cp == 34 || cp == 92) s.append('\\'); s.appendCodePoint(cp); }
            });
            return s.append('"').toString();
        }
        require(n.isObject());
        StringJoiner object = new StringJoiner(",", "{", "}");
        n.fields().forEachRemaining(e -> object.add(canonical(JSON.getNodeFactory().textNode(e.getKey())) + ":" + canonical(e.getValue())));
        return object.toString();
    }

    private static Map<String, Map<String, PublicKey>> keySets() throws Exception {
        Map<String, Map<String, PublicKey>> sets = new HashMap<>();
        JsonNode root = load("public-jwks.json");
        for (String name : List.of("current", "overlap", "next")) {
            Map<String, PublicKey> keys = new HashMap<>();
            for (JsonNode k : root.get(name).get("keys")) {
                fields(k, Set.of("kty", "n", "e", "kid", "alg", "use", "key_ops"), Set.of());
                require(text(k, "kty").equals("RSA") && text(k, "alg").equals("RS256") && text(k, "use").equals("sig"));
                require(k.get("key_ops").equals(JSON.readTree("[\"verify\"]")));
                BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(text(k, "n")));
                BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(text(k, "e")));
                require(n.bitLength() >= 2048 && n.testBit(0) && e.testBit(0) && e.compareTo(BigInteger.valueOf(3)) >= 0 && e.compareTo(n) < 0);
                PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
                require(keys.put(text(k, "kid"), key) == null);
            }
            require(!keys.isEmpty());
            sets.put(name, keys);
        }
        for (String name : List.of("current", "next")) {
            String pem = new String(resource("public-" + name + ".pem"), StandardCharsets.US_ASCII)
                    .replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
            require(Arrays.equals(Base64.getDecoder().decode(pem), sets.get(name).values().iterator().next().getEncoded()));
        }
        return sets;
    }

    private static int authenticate(String token, Instant now, Map<String, PublicKey> keys) {
        String sub, aud;
        try {
            require(token.length() <= 16384 && token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"));
            String[] p = token.split("\\.");
            JsonNode h = strict(Base64.getUrlDecoder().decode(p[0]));
            fields(h, Set.of("alg", "typ", "kid"), Set.of());
            require(text(h, "alg").equals("RS256") && text(h, "typ").equals("JWT"));
            PublicKey key = keys.get(text(h, "kid"));
            require(key != null);
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update((p[0] + "." + p[1]).getBytes(StandardCharsets.US_ASCII));
            require(verifier.verify(Base64.getUrlDecoder().decode(p[2])));
            JsonNode c = strict(Base64.getUrlDecoder().decode(p[1]));
            require(text(c, "iss").equals("urn:tosunsaeng:workload:billing:test"));
            sub = text(c, "sub"); aud = text(c, "aud");
            require(text(c, "jti").matches(UUID4));
            long iat = numericDate(c, "iat"), exp = numericDate(c, "exp");
            require(exp > iat && exp - iat <= 60);
            require(!Instant.ofEpochSecond(iat).isAfter(now.plusSeconds(30)));
            require(now.isBefore(Instant.ofEpochSecond(exp).plusSeconds(30)));
            if (c.has("nbf")) {
                long nbf = numericDate(c, "nbf");
                require(nbf >= iat && nbf < exp && !Instant.ofEpochSecond(nbf).isAfter(now.plusSeconds(30)));
            }
        } catch (Exception rejected) { return 401; } // Never report raw parser/signature exceptions.
        return sub.equals("billing-service") && aud.equals("tosunsaeng-learning-core:entitlement-access-events:test") ? 204 : 403;
    }

    private static long numericDate(JsonNode n, String k) {
        JsonNode v = n.get(k);
        require(v != null && v.isIntegralNumber() && v.canConvertToLong() && v.longValue() >= 0);
        return v.longValue();
    }

    private static JsonNode strict(byte[] bytes) throws Exception {
        try {
            String s = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            require(!s.startsWith("\ufeff"));
            JsonNode n = JSON.readTree(s);
            require(n != null);
            scalar(n);
            return n;
        } catch (Exception rejected) { throw new IllegalArgumentException("Rejected fixture JSON"); }
    }

    private static void scalar(JsonNode n) {
        if (n.isTextual()) n.textValue().codePoints().forEach(cp -> require(cp < 0xd800 || cp > 0xdfff));
        if (n.isObject()) n.fieldNames().forEachRemaining(k -> k.codePoints().forEach(cp -> require(cp < 0xd800 || cp > 0xdfff)));
        if (n.isContainerNode()) n.forEach(LcBillingPaidFixtureTest::scalar);
    }

    private static void fields(JsonNode n, Set<String> required, Set<String> optional) {
        require(n != null && n.isObject());
        Set<String> names = new HashSet<>(); n.fieldNames().forEachRemaining(names::add);
        require(names.containsAll(required)); names.removeAll(required); names.removeAll(optional); require(names.isEmpty());
        for (String k : optional) require(!n.has(k) || !n.get(k).isNull());
    }
    private static String text(JsonNode n, String k) {
        JsonNode v = n.get(k); require(v != null && v.isTextual() && !v.textValue().isEmpty()); return v.textValue();
    }
    private static void require(boolean ok) { if (!ok) throw new IllegalArgumentException("Rejected contract input"); }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static JsonNode load(String name) throws Exception { return strict(resource(name)); }
    private static byte[] resource(String name) throws Exception {
        try (var in = LcBillingPaidFixtureTest.class.getResourceAsStream(ROOT + name)) {
            if (in == null) throw new IllegalStateException("Missing fixture resource"); return in.readAllBytes();
        }
    }
    private interface Check { void run(JsonNode c) throws Exception; }
    private static Stream<DynamicTest> cases(String file, int count, Check check) throws Exception {
        JsonNode array = load(file); assertTrue(array.isArray()); assertEquals(count, array.size());
        Set<String> ids = new HashSet<>();
        return StreamSupport.stream(array.spliterator(), false).map(c -> {
            String id = text(c, "id"); assertTrue(ids.add(id));
            return DynamicTest.dynamicTest(id, () -> check.run(c));
        });
    }
}
