package web.tosunsaeng.domain.exams.access;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.*;
import static org.assertj.core.api.Assertions.*;

class AccessReceiverContractTest {
    static final String ROOT="/contracts/lc-billing-paid-exam-r2-fixtures-2/";
    static final ObjectMapper JSON=new ObjectMapper();
    static final Instant NOW=Instant.parse("2026-10-14T00:00:00Z");
    static JsonNode load(String f) throws Exception { try(var in=AccessReceiverContractTest.class.getResourceAsStream(ROOT+f)){return JSON.readTree(in);} }
    static byte[] raw(JsonNode n) { return Base64.getDecoder().decode(n.get("inputBase64").asText()); }
    static AccessWorkloadVerifier verifier(String set,Instant now) throws Exception {
        return new AccessWorkloadVerifier("test",load("public-jwks.json").get(set).toString(),Clock.fixed(now,ZoneOffset.UTC));
    }
    static byte[] event() throws Exception {
        for(var c:load("canonical-cases.json")) if(c.path("kind").asText().equals("event")) return raw(c);
        throw new AssertionError("No event fixture");
    }
    @TestFactory Stream<DynamicTest> originalSignedTokensAgainstProductionVerifier() throws Exception {
        return StreamSupport.stream(load("token-cases.json").spliterator(),false).map(c->DynamicTest.dynamicTest(c.get("id").asText(),()->{
            assertThat(verifier(c.get("keySet").asText(),Instant.parse(c.get("now").asText())).authenticate(c.get("token").asText()))
                    .isEqualTo(c.get("expectedAuthStatus").intValue());
        }));
    }
    @TestFactory Stream<DynamicTest> canonicalBytesAgainstProductionCodec() throws Exception {
        return StreamSupport.stream(load("canonical-cases.json").spliterator(),false).map(c->DynamicTest.dynamicTest(c.get("id").asText(),()->{
            if(c.get("kind").asText().equals("event"))
                assertThat(AccessRevocationEvent.decode(raw(c),"fixture-test").digest()).isEqualTo(c.get("expectedSha256").asText());
            else assertThat(AccessEventJson.quote(AccessEventJson.parse(raw(c)).textValue()).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .isEqualTo(Base64.getDecoder().decode(c.get("expectedCanonicalBase64").asText()));
        }));
    }
    @TestFactory Stream<DynamicTest> invalidBodiesAgainstProductionCodec() throws Exception {
        return StreamSupport.stream(load("invalid-event-cases.json").spliterator(),false).map(c->DynamicTest.dynamicTest(c.get("id").asText(),()->{
            assertThatThrownBy(()->AccessRevocationEvent.decode(raw(c),"fixture-test")).isInstanceOfSatisfying(AccessEventFailure.class,
                    e->assertThat(e.status()).isEqualTo(c.get("expectedStatus").intValue()));
        }));
    }
    @TestFactory Stream<DynamicTest> manifestThroughActualServletFilter() throws Exception {
        return StreamSupport.stream(load("http-cases.json").spliterator(),false)
                .filter(c->!c.has("expectedTransport")).map(c->DynamicTest.dynamicTest(c.get("id").asText(),()->{
            var sink=new AtomicInteger(); var downstream=new AtomicInteger();
            var filter=new AccessEventFilter(c.path("enabled").asBoolean(),8443,"fixture-test",verifier("current",NOW),e->sink.incrementAndGet());
            var request=new MockHttpServletRequest(c.path("method").asText("POST"),c.path("path").asText(AccessEventFilter.ROUTE));
            request.setLocalPort(c.path("port").asText().equals("workload")?8443:8080);
            if(c.hasNonNull("authorization")) request.addHeader("Authorization",c.get("authorization").asText());
            if(c.hasNonNull("query")) request.setQueryString(c.get("query").asText());
            request.setContent(event()); var response=new MockHttpServletResponse();
            filter.doFilter(request,response,(a,b)->downstream.incrementAndGet());
            if(c.has("expectedDispatch")) {assertThat(downstream.get()).isEqualTo(1); assertThat(sink.get()).isZero();}
            else {
                assertThat(response.getStatus()).isEqualTo(c.get("expectedStatus").intValue());
                assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
                assertThat(response.getContentAsByteArray()).isEmpty();
                assertThat(sink.get()).isEqualTo(response.getStatus()==204?1:0); assertThat(downstream.get()).isZero();
            }
        }));
    }
    @Test void badKeySetIsSanitizedAndWholeSetRejected() throws Exception {
        var set=load("public-jwks.json").get("current").deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode)set.get("keys")).add(set.get("keys").get(0));
        assertThatThrownBy(()->new AccessWorkloadVerifier("test",set.toString(),Clock.systemUTC()))
                .hasMessage("Billing access verification configuration is invalid").hasNoCause();
        assertThatThrownBy(()->new AccessWorkloadVerifier("production","secret-invalid",Clock.systemUTC()))
                .hasMessageNotContaining("secret-invalid").hasNoCause();
    }
    @Test void duplicateAuthorizationRejectedBeforeBodyOrSink() throws Exception {
        var sink=new AtomicInteger(); var req=new MockHttpServletRequest("POST",AccessEventFilter.ROUTE); req.setLocalPort(8443);
        req.addHeader("Authorization",List.of("Bearer invalid","Bearer invalid"));
        var res=new MockHttpServletResponse();
        new AccessEventFilter(true,8443,"fixture-test",verifier("current",NOW),e->sink.incrementAndGet()).doFilter(req,res,(a,b)->{throw new AssertionError();});
        assertThat(res.getStatus()).isEqualTo(401); assertThat(sink.get()).isZero();
    }
}
