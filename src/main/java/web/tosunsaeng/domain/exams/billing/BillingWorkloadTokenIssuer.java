package web.tosunsaeng.domain.exams.billing;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Clock;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** No network access, no token cache, no authentication fallback. */
public final class BillingWorkloadTokenIssuer {
    public enum Purpose { RESERVATIONS, ATTEMPT_GROUP_EVENTS, EXAM_DELETION_EVENTS }
    private final String environment;
    private final RSAKey key;
    private final Clock clock;

    public BillingWorkloadTokenIssuer(WorkloadJwtProperties properties, Clock clock) {
        this.clock = clock;
        this.environment = properties.getEnvironment();
        if (!properties.isSenderEnabled()) {
            key = null; // Do not even read the private-JWK property while OFF.
            return;
        }
        try {
            require(Set.of("test", "staging", "production").contains(environment));
            String raw = properties.getPrivateJwk();
            require(raw != null && raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65536);
            var mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
            JsonNode node = mapper.readTree(raw);
            require(node != null && node.isObject());
            Set<String> names = new HashSet<>();
            node.fieldNames().forEachRemaining(names::add);
            require(names.equals(Set.of("kty", "alg", "use", "key_ops", "kid", "n", "e", "d", "p", "q", "dp", "dq", "qi")));
            require("RSA".equals(node.path("kty").asText()) && "RS256".equals(node.path("alg").asText())
                    && "sig".equals(node.path("use").asText()));
            require(node.path("kid").isTextual() && !node.path("kid").asText().isBlank()
                    && node.path("kid").asText().length() <= 256);
            require(node.path("key_ops").isArray() && node.path("key_ops").size() == 1
                    && "sign".equals(node.path("key_ops").get(0).asText()));
            for (String name : Set.of("n", "e", "d", "p", "q", "dp", "dq", "qi")) {
                require(node.path(name).isTextual() && node.path(name).asText().matches("[A-Za-z0-9_-]+"));
            }
            key = RSAKey.parse(raw);
            var n = key.getModulus().decodeToBigInteger();
            var e = key.getPublicExponent().decodeToBigInteger();
            var d = key.getPrivateExponent().decodeToBigInteger();
            var p = key.getFirstPrimeFactor().decodeToBigInteger();
            var q = key.getSecondPrimeFactor().decodeToBigInteger();
            require(n.bitLength() >= 2048 && e.testBit(0)
                    && e.compareTo(java.math.BigInteger.valueOf(3)) >= 0 && e.compareTo(n) < 0);
            require(p.isProbablePrime(80) && q.isProbablePrime(80) && !p.equals(q) && p.multiply(q).equals(n));
            var pm = p.subtract(java.math.BigInteger.ONE);
            var qm = q.subtract(java.math.BigInteger.ONE);
            require(e.multiply(d).mod(pm.divide(pm.gcd(qm)).multiply(qm)).equals(java.math.BigInteger.ONE));
            require(d.mod(pm).equals(key.getFirstFactorCRTExponent().decodeToBigInteger())
                    && d.mod(qm).equals(key.getSecondFactorCRTExponent().decodeToBigInteger())
                    && q.modInverse(p).equals(key.getFirstCRTCoefficient().decodeToBigInteger()));
            SignedJWT check = sign(Purpose.RESERVATIONS);
            require(check.verify(new RSASSAVerifier(key.toPublicJWK())));
        } catch (Exception invalid) {
            // Never retain a parsing exception/cause containing secret configuration.
            throw new IllegalStateException("Billing workload signing configuration is invalid");
        }
    }

    public String authorization(Purpose purpose) {
        if (key == null) throw new IllegalStateException("Billing workload sender is disabled");
        try {
            return "Bearer " + sign(purpose).serialize();
        } catch (Exception failure) {
            throw new IllegalStateException("Billing workload token issuance failed");
        }
    }

    private SignedJWT sign(Purpose purpose) throws JOSEException {
        long now = clock.instant().getEpochSecond();
        String target = switch (purpose) {
            case RESERVATIONS -> "reservations";
            case ATTEMPT_GROUP_EVENTS -> "attempt-group-events";
            case EXAM_DELETION_EVENTS -> "exam-deletion-events";
        };
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT).keyID(key.getKeyID()).build(), new JWTClaimsSet.Builder()
                .issuer("urn:tosunsaeng:workload:learning-core:" + environment)
                .subject("learning-core-service").audience("tosunsaeng-billing:" + target + ":" + environment)
                .issueTime(Date.from(java.time.Instant.ofEpochSecond(now)))
                .notBeforeTime(Date.from(java.time.Instant.ofEpochSecond(now)))
                .expirationTime(Date.from(java.time.Instant.ofEpochSecond(Math.addExact(now, 60))))
                .jwtID(UUID.randomUUID().toString()).build());
        jwt.sign(new RSASSASigner(key));
        return jwt;
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Invalid signing configuration");
    }
}
