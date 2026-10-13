package web.tosunsaeng.domain.exams.attemptgroup.infrastructure;

import com.sun.net.httpserver.HttpServer;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.exams.billing.WorkloadTestKeys;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class JwtAttemptGroupEventClientTest {
    @Test
    void freshSignedAttemptTokensPreserveCanonicalBytesAndTraceAndDoNotRedirect() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var tokens=new ArrayList<String>(); var bodies=new ArrayList<byte[]>(); var traces=new ArrayList<String>();
        var redirected=new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/internal/v1/attempt-group-events",e->{
            tokens.add(e.getRequestHeaders().getFirst("Authorization"));
            traces.add(e.getRequestHeaders().getFirst("traceparent"));
            bodies.add(e.getRequestBody().readAllBytes());
            e.getResponseHeaders().set("Retry-After","5");
            e.getResponseHeaders().set("Location","/redirect");
            e.sendResponseHeaders(tokens.size()==1?503:307,-1);e.close();
        });
        server.createContext("/redirect",e->{redirected.incrementAndGet();e.sendResponseHeaders(204,-1);e.close();});
        server.start();
        try {
            var p=properties("http://127.0.0.1:"+server.getAddress().getPort());
            var client=new JwtAttemptGroupEventClient(p,WorkloadTestKeys.issuer());
            String body="{\"eventId\":\"opaque-test\",\"value\":1}";
            String trace="00-0123456789abcdef0123456789abcdef-fedcba9876543210-01";
            assertEquals(503,client.send(body,trace).statusCode());
            assertEquals(307,client.send(body,trace).statusCode());
            assertEquals(0,redirected.get());
            assertArrayEquals(bodies.get(0),bodies.get(1));
            assertEquals(java.util.List.of(trace,trace),traces);
            var ids=new java.util.HashSet<String>();
            for(String t:tokens){
                var jwt=SignedJWT.parse(t.substring(7));
                assertTrue(jwt.verify(new RSASSAVerifier(WorkloadTestKeys.KEY.toPublicJWK())));
                assertTrue(ids.add(jwt.getJWTClaimsSet().getJWTID()));
                assertEquals(java.util.List.of("tosunsaeng-billing:attempt-group-events:test"),jwt.getJWTClaimsSet().getAudience());
            }
        } finally {server.stop(0);}
    }

    static AttemptGroupEventProperties properties(String url) {
        return new AttemptGroupEventProperties(false,true,url,null,Duration.ofMinutes(30),Duration.ofSeconds(1),20,
                Duration.ofSeconds(30),Duration.ofMinutes(15),Duration.ofSeconds(2),Duration.ofSeconds(5),
                Duration.ofDays(30),Duration.ofDays(90));
    }
}
