package web.tosunsaeng.domain.exams.billing;

import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import java.time.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class BillingWorkloadTokenIssuerTest {
    @TestFactory
    Stream<DynamicTest> all64Flags() {
        return IntStream.range(0, 64).mapToObj(m -> DynamicTest.dynamicTest("flags-" + m, () -> {
            boolean j=(m&1)!=0,s=(m&2)!=0,h=(m&4)!=0,r=(m&8)!=0,p=(m&32)!=0;
            boolean valid = (!h || s) && (!r || s) && (j || (!s && !p));
            if (valid) assertDoesNotThrow(() -> BillingWorkloadConfiguration.validateFlags(j,s,h,r,p));
            else assertThrows(IllegalStateException.class, () -> BillingWorkloadConfiguration.validateFlags(j,s,h,r,p));
        }));
    }

    @Test
    void actualSignedTokensAreFreshAndStrictForBothPurposes() throws Exception {
        Instant now = Instant.parse("2026-10-13T00:00:00.999999999Z");
        var issuer = new BillingWorkloadTokenIssuer(WorkloadTestKeys.properties(), Clock.fixed(now, ZoneOffset.UTC));
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (var purpose : BillingWorkloadTokenIssuer.Purpose.values()) {
            for (int i=0;i<2;i++) {
                String value = issuer.authorization(purpose);
                assertTrue(value.startsWith("Bearer "));
                var jwt = SignedJWT.parse(value.substring(7));
                assertTrue(jwt.verify(new RSASSAVerifier(WorkloadTestKeys.KEY.toPublicJWK())));
                assertEquals(java.util.Set.of("alg","typ","kid"),jwt.getHeader().toJSONObject().keySet());
                var c = jwt.getJWTClaimsSet();
                assertEquals("urn:tosunsaeng:workload:learning-core:test",c.getIssuer());
                assertEquals("learning-core-service",c.getSubject());
                assertTrue(ids.add(c.getJWTID()));
                assertEquals(4,java.util.UUID.fromString(c.getJWTID()).version());
                assertEquals(now.getEpochSecond(),c.getIssueTime().toInstant().getEpochSecond());
                assertEquals(c.getIssueTime(),c.getNotBeforeTime());
                assertEquals(60000,c.getExpirationTime().getTime()-c.getIssueTime().getTime());
                String target = switch (purpose) {
                    case RESERVATIONS -> "reservations";
                    case ATTEMPT_GROUP_EVENTS -> "attempt-group-events";
                    case EXAM_DELETION_EVENTS -> "exam-deletion-events";
                    case REANSWER_ELIGIBILITY -> "reanswer-eligibility";
                };
                assertEquals(java.util.List.of("tosunsaeng-billing:"+target+":test"),c.getAudience());
                // Aud must be a JSON string on the wire, not merely a single-item parser list.
                var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(jwt.getPayload().toBytes());
                assertTrue(json.get("aud").isTextual());
            }
        }
    }

    @Test
    void disabledDoesNotReadPrivateKeyAndCannotIssue() {
        var p = new WorkloadJwtProperties() {
            @Override public String getPrivateJwk() { throw new AssertionError("OFF key read"); }
        };
        var issuer = new BillingWorkloadTokenIssuer(p,Clock.systemUTC());
        assertThrows(IllegalStateException.class,()->issuer.authorization(BillingWorkloadTokenIssuer.Purpose.RESERVATIONS));
    }

    @TestFactory
    Stream<DynamicTest> invalidKeyConfigurationsFailWithoutSecretCause() {
        return Stream.of("empty","environment","public","kid","small","duplicate","crt","extra","ops").map(id ->
            DynamicTest.dynamicTest(id,()-> {
                var p=WorkloadTestKeys.properties();
                var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
                var n=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(p.getPrivateJwk());
                switch(id) {
                    case "empty" -> p.setPrivateJwk("");
                    case "environment" -> p.setEnvironment("prod");
                    case "public" -> p.setPrivateJwk(WorkloadTestKeys.KEY.toPublicJWK().toJSONString());
                    case "kid" -> {n.put("kid","");p.setPrivateJwk(n.toString());}
                    case "small" -> {n.put("n","AQAB");p.setPrivateJwk(n.toString());}
                    case "duplicate" -> p.setPrivateJwk(p.getPrivateJwk().replaceFirst("\\{","{\"kid\":\"duplicate\","));
                    case "crt" -> {n.put("qi","AQAB");p.setPrivateJwk(n.toString());}
                    case "extra" -> {n.put("unexpected","not-a-secret");p.setPrivateJwk(n.toString());}
                    case "ops" -> {n.putArray("key_ops").add("verify");p.setPrivateJwk(n.toString());}
                }
                var failure=assertThrows(IllegalStateException.class,()->new BillingWorkloadTokenIssuer(p,Clock.systemUTC()));
                assertNull(failure.getCause());
                assertEquals("Billing workload signing configuration is invalid",failure.getMessage());
            }));
    }
}
