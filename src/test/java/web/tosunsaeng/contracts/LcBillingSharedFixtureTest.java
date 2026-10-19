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
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/** A-stage independent contract oracle only; not a runtime verifier or HTTP-chain substitute. */
class LcBillingSharedFixtureTest {
    private static final String ROOT = "/contracts/lc-billing-workload-auth-r1/";
    private static final String REVISION = "lc-billing-workload-auth-r1-fixtures-1";
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Base64.Decoder B64 = Base64.getUrlDecoder();
    private static final Map<String, String> ISSUERS = Map.of(
            "lc", "urn:tosunsaeng:workload:learning-core:test",
            "identity", "urn:tosunsaeng:workload:identity:test");

    @Test
    void originalBytesMatchSharedManifest() throws Exception {
        Set<String> names = new HashSet<>();
        for (String line : new String(resource("SHA256SUMS"), StandardCharsets.UTF_8).lines().toList()) {
            String[] fields = line.split("\\s+");
            assertEquals(2, fields.length);
            assertTrue(Set.of("public-jwks.json", "token-cases.json", "http-cases.json").contains(fields[1]));
            assertTrue(names.add(fields[1]));
            assertEquals(fields[0], HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(resource(fields[1]))), fields[1]);
        }
        assertEquals(3, names.size());
    }

    @TestFactory
    Stream<DynamicTest> originalSignedCases() throws Exception {
        JsonNode fixture = JSON.readTree(resource("token-cases.json"));
        JsonNode sets = JSON.readTree(resource("public-jwks.json"));
        assertEquals(REVISION, fixture.path("revision").asText());
        assertEquals(72, fixture.path("cases").size());
        Set<String> ids = new HashSet<>();
        return StreamSupport.stream(fixture.path("cases").spliterator(), false).map(c -> {
            String id = c.path("id").asText();
            assertFalse(id.isBlank());
            assertTrue(ids.add(id));
            return DynamicTest.dynamicTest(id, () -> {
                String verifier = c.path("verifier").asText();
                assertTrue(ISSUERS.containsKey(verifier));
                String keySet = c.path("keySet").asText();
                assertTrue(Set.of("current", "overlap", "next").contains(keySet));
                JsonNode jwks = sets.get("current".equals(keySet) ? verifier : keySet);
                // Setup failures are not mistaken for expected token rejection.
                var keys = publicKeys(jwks);
                Principal principal = authenticate(c.path("token").asText(), ISSUERS.get(verifier),
                        Instant.parse(c.path("now").asText()), keys);
                assertEquals(c.path("valid").asBoolean(), principal != null, id);
                if (principal != null) {
                    assertEquals(c.path("subject").asText(), principal.subject(), id);
                    assertEquals(c.path("audience").asText(), principal.audience(), id);
                }
            });
        });
    }

    private record Principal(String subject, String audience) {}

    private static Map<String, java.security.PublicKey> publicKeys(JsonNode set) throws Exception {
        require(set != null && set.isObject() && set.size() == 1 && set.has("keys"));
        require(set.get("keys").isArray() && set.get("keys").size() >= 1 && set.get("keys").size() <= 20);
        Map<String, java.security.PublicKey> keys = new HashMap<>();
        for (JsonNode k : set.get("keys")) {
            require(k.isObject() && k.size() == 7);
            Set<String> fields = new HashSet<>();
            k.fieldNames().forEachRemaining(fields::add);
            require(fields.equals(Set.of("kty", "alg", "use", "key_ops", "kid", "n", "e")));
            require("RSA".equals(string(k, "kty")) && "RS256".equals(string(k, "alg"))
                    && "sig".equals(string(k, "use")));
            require(k.path("key_ops").isArray() && k.path("key_ops").size() == 1
                    && "verify".equals(k.path("key_ops").get(0).asText()));
            String kid = string(k, "kid");
            require(kid.length() <= 256 && !keys.containsKey(kid));
            BigInteger n = new BigInteger(1, B64.decode(string(k, "n")));
            BigInteger e = new BigInteger(1, B64.decode(string(k, "e")));
            require(n.bitLength() >= 2048 && n.testBit(0) && e.compareTo(BigInteger.valueOf(3)) >= 0
                    && e.testBit(0) && e.compareTo(n) < 0);
            keys.put(kid, KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e)));
        }
        return keys;
    }

    private static Principal authenticate(String token, String issuer, Instant now,
                                          Map<String, java.security.PublicKey> keys) {
        try {
            require(token.length() <= 16 * 1024);
            require(token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"));
            String[] parts = token.split("\\.");
            JsonNode header = strictJson(B64.decode(parts[0]));
            require(header.isObject() && header.size() == 3 && "RS256".equals(string(header, "alg"))
                    && "JWT".equals(string(header, "typ")));
            var key = keys.get(string(header, "kid"));
            require(key != null);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            require(signature.verify(B64.decode(parts[2])));
            JsonNode claims = strictJson(B64.decode(parts[1]));
            require(claims.isObject() && issuer.equals(string(claims, "iss")));
            String sub = string(claims, "sub");
            String aud = string(claims, "aud");
            require(string(claims, "jti").matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
            long iat = numericDate(claims, "iat");
            long exp = numericDate(claims, "exp");
            require(exp > iat && exp - iat <= 60);
            require(!Instant.ofEpochSecond(iat).isAfter(now.plusSeconds(30)));
            require(now.isBefore(Instant.ofEpochSecond(exp).plusSeconds(30)));
            if (claims.has("nbf")) {
                long nbf = numericDate(claims, "nbf");
                require(nbf >= iat && nbf < exp);
                require(!Instant.ofEpochSecond(nbf).isAfter(now.plusSeconds(30)));
            }
            // Subject/audience authorization belongs to B's HTTP chain, not this authentication oracle.
            return new Principal(sub, aud);
        } catch (Exception rejected) {
            // Never attach parser/signature exceptions: they may contain fixture token fragments.
            return null;
        }
    }

    private static JsonNode strictJson(byte[] bytes) throws Exception {
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        return JSON.readTree(text);
    }

    private static long numericDate(JsonNode node, String name) {
        JsonNode value = node.get(name);
        require(value != null && value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0);
        return value.longValue();
    }

    private static String string(JsonNode node, String name) {
        JsonNode value = node.get(name);
        require(value != null && value.isTextual() && !value.textValue().isBlank());
        return value.textValue();
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Rejected contract input");
    }

    private static byte[] resource(String name) throws Exception {
        try (var input = LcBillingSharedFixtureTest.class.getResourceAsStream(ROOT + name)) {
            if (input == null) throw new IllegalStateException("Missing fixture resource");
            return input.readAllBytes();
        }
    }
}
