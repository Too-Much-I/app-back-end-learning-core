package web.tosunsaeng.domain.exams.reanswer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import web.tosunsaeng.domain.exams.billing.BillingWorkloadTokenIssuer;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HttpReanswerEligibilityClientTest {
    @Test void partLearningUsesPaidOnlyReasonsAndDedicatedPathAndPurpose() {
        var http=mock(HttpClient.class);
        var issuer=mock(BillingWorkloadTokenIssuer.class);
        var purpose=BillingWorkloadTokenIssuer.Purpose.PART_LEARNING_ELIGIBILITY;
        when(issuer.authorization(purpose)).thenReturn("Bearer fixture");
        var caller=new HttpReanswerEligibilityClient(URI.create("https://billing.invalid"),issuer,http,purpose);
        assertTrue(caller.decode(200,headers(),decision("ALLOWED","ACTIVE_PAID_ENTITLEMENT",UNTIL)
                .getBytes(StandardCharsets.UTF_8),USER).valid());
        assertThrows(IllegalStateException.class,() -> caller.decode(200,headers(),
                decision("ALLOWED","ENTITLEMENT_AVAILABLE",UNTIL).getBytes(StandardCharsets.UTF_8),USER));
        assertEquals(ErrorStatus._ENTITLEMENT_INSUFFICIENT,assertThrows(ExamsException.class,
                () -> caller.decode(200,headers(),decision("DENIED","PAID_ENTITLEMENT_REQUIRED",null)
                        .getBytes(StandardCharsets.UTF_8),USER)).getCode());
        var response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200); when(response.headers()).thenReturn(headers());
        when(response.body()).thenReturn(decision("ALLOWED","ACTIVE_PAID_ENTITLEMENT",UNTIL).getBytes(StandardCharsets.UTF_8));
        when(http.<byte[]>sendAsync(any(HttpRequest.class),any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(response));
        caller.check(USER);
        var request=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http,times(1)).sendAsync(request.capture(),any());
        assertEquals("/internal/v1/entitlements/part-learning-eligibility",request.getValue().uri().getPath());
        assertEquals("POST",request.getValue().method());
        assertTrue(request.getValue().headers().firstValue("Idempotency-Key").isEmpty());
        verify(issuer).authorization(purpose);
        verify(issuer,never()).authorization(BillingWorkloadTokenIssuer.Purpose.REANSWER_ELIGIBILITY);
    }
    static final String USER="11111111-1111-4111-8111-111111111111";
    static final String CHECKED="2026-10-21T08:00:00.123456789Z";
    static final String UNTIL="2026-10-21T08:00:05.123456789Z";
    final HttpReanswerEligibilityClient client=new HttpReanswerEligibilityClient(URI.create("https://billing.invalid"),
            mock(BillingWorkloadTokenIssuer.class));
    HttpHeaders headers() { return HttpHeaders.of(Map.of("Content-Type",List.of("application/json"),
            "Cache-Control",List.of("no-store"),"Retry-After",List.of("1")),(k,v)->true); }
    String decision(String decision,String reason,String until) {
        return "{\"userId\":\""+USER+"\",\"decision\":\""+decision+"\",\"reason\":\""+reason
                +"\",\"checkedAt\":\""+CHECKED+"\",\"validUntil\":"+(until==null?"null":"\""+until+"\"")+"}";
    }
    ReanswerApproval decode(int status,String body) { return client.decode(status,headers(),body.getBytes(StandardCharsets.UTF_8),USER); }
    @Test void allowedPreservesNanoseconds() {
        var proof=decode(200,decision("ALLOWED","ENTITLEMENT_AVAILABLE",UNTIL));
        assertTrue(proof.valid()); assertEquals(CHECKED,proof.checkedAt()); assertEquals(UNTIL,proof.validUntil());
    }
    @Test void businessFailuresHaveDistinctPublicCodes() {
        assertEquals(ErrorStatus._ENTITLEMENT_INSUFFICIENT,assertThrows(ExamsException.class,
                ()->decode(200,decision("DENIED","ENTITLEMENT_INSUFFICIENT",null))).getCode());
        assertEquals(ErrorStatus._ACCOUNT_WITHDRAWN,assertThrows(ExamsException.class,
                ()->decode(200,decision("DENIED","ACCOUNT_WITHDRAWN",null))).getCode());
        assertEquals(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE,assertThrows(ExamsException.class,
                ()->decode(200,decision("PENDING","ENTITLEMENT_STATE_UNCONFIRMED",null))).getCode());
    }
    @ParameterizedTest @ValueSource(ints={400,401,403,404,429,500,502,302})
    void integrationFaultsAreNotUserAuthenticationOrTemporaryFailures(int status) {
        assertThrows(IllegalStateException.class,()->decode(status,""));
    }
    @Test void strictShapeAndTimeBounds() {
        String good=decision("ALLOWED","ENTITLEMENT_AVAILABLE",UNTIL);
        for (String bad:List.of(good+"{}",good.replace("\"decision\":","\"decision\":\"ALLOWED\",\"decision\":"),
                good.replace("ALLOWED","UNKNOWN"),good.replace(UNTIL,"2026-10-21T08:00:06Z"),
                good.replace(UNTIL,CHECKED),good.replace(USER,"22222222-2222-4222-8222-222222222222"),
                good.replace("ENTITLEMENT_AVAILABLE","ENTITLEMENT_INSUFFICIENT"),
                good.replace("}",",\"extra\":1}"))) assertThrows(IllegalStateException.class,()->decode(200,bad));
    }
    @Test void strictErrorEnvelope() {
        String body="{\"code\":\"BILLING_TEMPORARILY_UNAVAILABLE\",\"message\":\"Billing is temporarily unavailable.\",\"retryable\":true,\"correlationId\":\""+USER+"\"}";
        assertEquals(1,assertThrows(ExamsException.class,()->decode(503,body)).getRetryAfterSeconds());
        assertThrows(IllegalStateException.class,()->decode(503,body.replace("true","false")));
        assertThrows(IllegalStateException.class,()->decode(503,body.replace("unavailable.","unavailable!")));
    }
    @Test void oversizeAndInvalidUtf8FailClosed() {
        assertThrows(IllegalStateException.class,()->client.decode(200,headers(),new byte[16385],USER));
        assertThrows(IllegalStateException.class,()->client.decode(200,headers(),new byte[]{(byte)0xc3,0x28},USER));
    }
    @Test void noStoreMustBeAnExactDirective() {
        var bad=HttpHeaders.of(Map.of("Content-Type",List.of("application/json"),
                "Cache-Control",List.of("no-store-invalid")),(k,v)->true);
        assertThrows(IllegalStateException.class,()->client.decode(200,bad,
                decision("ALLOWED","ENTITLEMENT_AVAILABLE",UNTIL).getBytes(StandardCharsets.UTF_8),USER));
    }
    @Test void oneRequestUsesDedicatedWorkloadPurposeAndDoesNotRetryNetworkFailure() {
        var http=mock(HttpClient.class);
        var issuer=mock(BillingWorkloadTokenIssuer.class);
        when(issuer.authorization(BillingWorkloadTokenIssuer.Purpose.REANSWER_ELIGIBILITY)).thenReturn("Bearer fixture");
        java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> failed=new java.util.concurrent.CompletableFuture<>();
        failed.completeExceptionally(new java.net.http.HttpTimeoutException("fixture"));
        when(http.<byte[]>sendAsync(any(HttpRequest.class),any())).thenReturn(failed);
        var caller=new HttpReanswerEligibilityClient(URI.create("https://billing.invalid"),issuer,http);
        assertEquals(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE,
                assertThrows(ExamsException.class,()->caller.check(USER)).getCode());
        var request=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http,times(1)).sendAsync(request.capture(),any());
        assertEquals("POST",request.getValue().method());
        assertEquals("/internal/v1/entitlements/reanswer-eligibility",request.getValue().uri().getPath());
        assertEquals(java.time.Duration.ofSeconds(5),request.getValue().timeout().orElseThrow());
        assertEquals("Bearer fixture",request.getValue().headers().firstValue("Authorization").orElseThrow());
        assertTrue(request.getValue().headers().firstValue("Idempotency-Key").isEmpty());
        verify(issuer,times(1)).authorization(BillingWorkloadTokenIssuer.Purpose.REANSWER_ELIGIBILITY);
    }
}
