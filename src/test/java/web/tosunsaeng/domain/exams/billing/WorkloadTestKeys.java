package web.tosunsaeng.domain.exams.billing;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.time.Clock;

/** Ephemeral test keys only; never printed or written. */
public final class WorkloadTestKeys {
    public static final RSAKey KEY = generate();
    private static RSAKey generate() {
        try { return new RSAKeyGenerator(2048).keyID("lc-test").generate(); }
        catch (Exception e) { throw new IllegalStateException("Test key generation failed"); }
    }
    public static WorkloadJwtProperties properties() {
        var p = new WorkloadJwtProperties();
        p.setSenderEnabled(true);
        p.setEnvironment("test");
        var json = KEY.toJSONObject();
        json.put("alg", "RS256"); json.put("use", "sig"); json.put("key_ops", java.util.List.of("sign"));
        try { p.setPrivateJwk(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(json)); }
        catch (Exception e) { throw new IllegalStateException("Test configuration failed"); }
        return p;
    }
    public static BillingWorkloadTokenIssuer issuer() {
        return new BillingWorkloadTokenIssuer(properties(), Clock.systemUTC());
    }
}
