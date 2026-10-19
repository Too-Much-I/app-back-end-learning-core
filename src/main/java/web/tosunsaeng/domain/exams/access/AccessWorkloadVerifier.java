package web.tosunsaeng.domain.exams.access;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import static web.tosunsaeng.domain.exams.access.AccessEventJson.*;

/** Immutable, locally deployed Billing keys. No remote lookup or other service trust fallback. */
public final class AccessWorkloadVerifier {
    private final Map<String,PublicKey> keys;
    private final String environment;
    private final Clock clock;
    public AccessWorkloadVerifier(String environment,String jwks,Clock clock) {
        this.environment=environment; this.clock=clock;
        try {
            require(environment!=null && Set.of("test","staging","production").contains(environment));
            require(jwks!=null && jwks.getBytes(StandardCharsets.UTF_8).length<=65536);
            var root=parse(jwks.getBytes(StandardCharsets.UTF_8)); fields(root,Set.of("keys"));
            var array=root.get("keys"); require(array.isArray() && !array.isEmpty() && array.size()<=20);
            Map<String,PublicKey> parsed=new HashMap<>();
            for(var k:array) {
                fields(k,Set.of("kty","alg","use","key_ops","kid","n","e"));
                require(text(k,"kty").equals("RSA") && text(k,"alg").equals("RS256") && text(k,"use").equals("sig"));
                require(k.get("key_ops").isArray() && k.get("key_ops").size()==1 && k.get("key_ops").get(0).isTextual()
                        && k.get("key_ops").get(0).textValue().equals("verify"));
                String kid=text(k,"kid"); require(!kid.isBlank() && kid.length()<=256);
                require(text(k,"n").matches("[A-Za-z0-9_-]+") && text(k,"e").matches("[A-Za-z0-9_-]+"));
                var n=new BigInteger(1,Base64.getUrlDecoder().decode(text(k,"n")));
                var e=new BigInteger(1,Base64.getUrlDecoder().decode(text(k,"e")));
                require(n.bitLength()>=2048 && n.testBit(0) && e.testBit(0) && e.compareTo(BigInteger.valueOf(3))>=0 && e.compareTo(n)<0);
                require(parsed.put(kid,KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n,e)))==null);
            }
            keys=Map.copyOf(parsed);
        } catch(Exception invalid) { throw new IllegalStateException("Billing access verification configuration is invalid"); }
    }
    public int authenticate(String token) {
        String subject,audience;
        try {
            require(token!=null && token.length()<=16384 && token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"));
            var parts=token.split("\\."); var header=parse(Base64.getUrlDecoder().decode(parts[0]));
            fields(header,Set.of("alg","typ","kid"));
            require(text(header,"alg").equals("RS256") && text(header,"typ").equals("JWT"));
            var key=keys.get(text(header,"kid")); require(key!=null);
            var signature=Signature.getInstance("SHA256withRSA"); signature.initVerify(key);
            signature.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
            require(signature.verify(Base64.getUrlDecoder().decode(parts[2])));
            var claims=parse(Base64.getUrlDecoder().decode(parts[1]));
            require(text(claims,"iss").equals("urn:tosunsaeng:workload:billing:"+environment));
            subject=text(claims,"sub"); audience=text(claims,"aud");
            require(text(claims,"jti").matches(AccessRevocationEvent.UUID4));
            long issued=date(claims,"iat"), expires=date(claims,"exp");
            require(expires>issued && expires-issued<=60);
            Instant now=clock.instant();
            require(!Instant.ofEpochSecond(issued).isAfter(now.plusSeconds(30)) && now.isBefore(Instant.ofEpochSecond(expires).plusSeconds(30)));
            if(claims.has("nbf")) {
                long nbf=date(claims,"nbf"); require(nbf>=issued && nbf<expires && !Instant.ofEpochSecond(nbf).isAfter(now.plusSeconds(30)));
            }
        } catch(Exception invalid) { return 401; }
        return subject.equals("billing-service") && audience.equals("tosunsaeng-learning-core:entitlement-access-events:"+environment)?204:403;
    }
    private static long date(JsonNode n,String k) {
        var v=n.get(k); require(v!=null && v.isIntegralNumber() && v.canConvertToLong() && v.longValue()>=0); return v.longValue();
    }
}
