package web.tosunsaeng.domain.exams.access;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.apache.catalina.startup.Tomcat;
import jakarta.servlet.http.*;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.security.KeyStore;
import javax.net.ssl.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class AccessReceiverTlsTest {
    @TempDir Path directory;
    @Test void realTlsListenerIsolatedFromPublicPortAndAppRoutes() throws Exception {
        Path file=directory.resolve("fixture.p12");String password="isolated-test-only";
        var keytool=Path.of(System.getProperty("java.home"),"bin","keytool").toString();
        var process=new ProcessBuilder(keytool,"-genkeypair","-alias","fixture","-keyalg","RSA","-keysize","2048",
                "-validity","2","-dname","CN=localhost","-ext","SAN=dns:localhost,ip:127.0.0.1",
                "-storetype","PKCS12","-keystore",file.toString(),"-storepass",password,"-noprompt").redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();assertThat(process.waitFor()).isZero();
        int internal;try(var socket=new ServerSocket(0)){internal=socket.getLocalPort();}
        var p=new AccessEventProperties();p.setReceiverEnabled(true);p.setPort(internal);p.setConsumerScopeId("fixture-test");
        p.setTlsKeyStore(file.toString());p.setTlsKeyStorePassword(password);p.setTlsKeyAlias("fixture");
        var tls=AccessEventTls.connector(p,0);var server=new Tomcat();server.setBaseDir(directory.resolve("tomcat").toString());server.setPort(0);
        var publicConnector=server.getConnector();server.getService().addConnector(tls);
        var calls=new AtomicInteger();var filter=new AccessEventFilter(true,internal,"fixture-test",
                AccessReceiverContractTest.verifier("current",AccessReceiverContractTest.NOW),e->calls.incrementAndGet());
        var isolation=new AccessEventRuntimeConfiguration().billingAccessPortIsolation(p).getFilter();
        var ctx=server.addContext("",directory.toString());
        Tomcat.addServlet(ctx,"fixture",new HttpServlet(){
            @Override protected void service(HttpServletRequest req,HttpServletResponse res) throws java.io.IOException,jakarta.servlet.ServletException {
                isolation.doFilter(req,res,(a,b)->filter.doFilter(a,b,(x,y)->((HttpServletResponse)y).setStatus(200)));
            }
        });ctx.addServletMappingDecoded("/*","fixture");
        KeyStore store=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(file)){store.load(in,password.toCharArray());}
        KeyStore trust=KeyStore.getInstance(KeyStore.getDefaultType());trust.load(null,null);trust.setCertificateEntry("fixture",store.getCertificate("fixture"));
        var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(trust);
        var ssl=SSLContext.getInstance("TLS");ssl.init(null,tm.getTrustManagers(),null);
        var client=HttpClient.newBuilder().sslContext(ssl).connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
        try {
            server.start();String origin="https://localhost:"+internal;
            String authorization=AccessReceiverContractTest.load("http-cases.json").get(0).get("authorization").asText();
            var request=HttpRequest.newBuilder(URI.create(origin+AccessEventFilter.ROUTE)).timeout(Duration.ofSeconds(5))
                    .header("Authorization",authorization).POST(HttpRequest.BodyPublishers.ofByteArray(AccessReceiverContractTest.event())).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofByteArray());assertThat(response.statusCode()).isEqualTo(204);
            assertThat(response.sslSession()).isPresent();assertThat(response.body()).isEmpty();assertThat(calls.get()).isEqualTo(1);
            assertThat(client.send(HttpRequest.newBuilder(URI.create(origin+"/api/v1/exams")).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
            String publicOrigin="http://localhost:"+publicConnector.getLocalPort();
            assertThat(client.send(HttpRequest.newBuilder(URI.create(publicOrigin+AccessEventFilter.ROUTE)).header("X-Forwarded-Port",String.valueOf(internal))
                    .GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
            assertThat(client.send(HttpRequest.newBuilder(URI.create(publicOrigin+"/api/v1/exams")).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
            assertThat(calls.get()).isEqualTo(1);
        }finally{server.stop();server.destroy();}
    }
    @Test void invalidTlsConfigIsSanitizedAndPortCollisionRejected(){
        var p=new AccessEventProperties();p.setReceiverEnabled(true);p.setPort(8443);p.setConsumerScopeId("fixture-test");
        p.setTlsKeyStore("secret-file-value");p.setTlsKeyStorePassword("secret-password-value");p.setTlsKeyAlias("fixture");
        assertThatThrownBy(()->AccessEventTls.connector(p,8080)).hasMessage("Billing access TLS configuration is invalid").hasNoCause();
        assertThatThrownBy(()->AccessEventTls.connector(p,8443)).hasMessage("Billing access listener configuration is invalid");
        p.setReceiverEnabled(false);assertThatThrownBy(()->AccessEventTls.connector(p,8080)).hasMessage("Billing access receiver is disabled");
    }
}
