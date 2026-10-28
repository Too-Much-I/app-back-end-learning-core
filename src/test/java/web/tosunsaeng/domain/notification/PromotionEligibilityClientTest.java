package web.tosunsaeng.domain.notification;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.exams.billing.BillingWorkloadTokenIssuer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PromotionEligibilityClientTest {
    static final String USER="30000000-0000-4000-8000-000000000001";
    static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"),ZoneOffset.UTC);
    static final String VALID="{\"userId\":\""+USER+"\",\"eligible\":true,\"checkedAt\":\"2026-10-28T03:00:00Z\"}";
    @Test void requiresBothFreshStrictResponses() throws Exception {
        for(String response:new String[]{VALID,VALID.replace("true","false"),VALID.replace("03:00:00","02:59:00"),
                VALID.replace("true","\"true\""),VALID.replace("\"eligible\":true","\"eligible\":true,\"eligible\":true"),VALID+"{}","x".repeat(16385)}) {
            HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            AtomicInteger calls=new AtomicInteger();
            server.createContext("/",exchange->{calls.incrementAndGet();byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
            server.start();
            var issuer=mock(BillingWorkloadTokenIssuer.class);when(issuer.authorization(any())).thenReturn("Bearer fixture");
            URI uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/");
            try(var client=new PromotionEligibilityClient(uri,uri,issuer,CLOCK)) {
                assertThat(client.eligible(USER)).isEqualTo(response.equals(VALID));
                assertThat(calls.get()).isEqualTo(response.equals(VALID)?2:1);
            }finally{server.stop(0);}
        }
    }
    @Test void errorsAndRedirectsNeverRetryOrReachSecondService() throws Exception {
        for(int status:new int[]{302,401,403,429,503}){
            HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger calls=new AtomicInteger();
            server.createContext("/",exchange->{calls.incrementAndGet();exchange.getResponseHeaders().add("Location","/redirected");
                exchange.sendResponseHeaders(status,-1);exchange.close();});server.start();
            var issuer=mock(BillingWorkloadTokenIssuer.class);when(issuer.authorization(any())).thenReturn("Bearer fixture");
            URI uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/");
            try(var client=new PromotionEligibilityClient(uri,uri,issuer,CLOCK)){
                assertThat(client.eligible(USER)).isFalse();assertThat(calls.get()).isEqualTo(1);
            }finally{server.stop(0);}
        }
    }
    @Test void responseBodyStallIsBoundedAndNotRetried() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        CountDownLatch release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
        server.createContext("/",exchange->{calls.incrementAndGet();exchange.sendResponseHeaders(200,100);
            exchange.getResponseBody().write('{');exchange.getResponseBody().flush();
            try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{exchange.close();}});
        server.start();var issuer=mock(BillingWorkloadTokenIssuer.class);when(issuer.authorization(any())).thenReturn("Bearer fixture");
        URI uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/");
        try(var client=new PromotionEligibilityClient(uri,uri,issuer,CLOCK)) {
            long start=System.nanoTime();assertThat(client.eligible(USER)).isFalse();
            assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(6));
            assertThat(calls.get()).isEqualTo(1);
        }finally{release.countDown();server.stop(0);}
    }
}
