package web.tosunsaeng.domain.exams.access;

import jakarta.servlet.http.*;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Real loopback Tomcat HTTP. TLS/proxy deployment is intentionally not claimed by this test. */
class AccessReceiverHttpTest {
    @TempDir Path directory;
    @Test void realHttpAuthBodyAndPortBoundaries() throws Exception {
        var server=new Tomcat(); server.setBaseDir(directory.toString()); server.setPort(0);
        server.getConnector();
        var context=server.addContext("",directory.toString()); var calls=new AtomicInteger();
        var filter=new java.util.concurrent.atomic.AtomicReference<AccessEventFilter>();
        Tomcat.addServlet(context,"receiver",new HttpServlet(){
            @Override protected void service(HttpServletRequest req,HttpServletResponse res) throws java.io.IOException,jakarta.servlet.ServletException {
                filter.get().doFilter(req,res,(a,b)->((HttpServletResponse)b).setStatus(418));
            }
        }); context.addServletMappingDecoded("/*","receiver");
        try {
            server.start(); int port=server.getConnector().getLocalPort();
            filter.set(new AccessEventFilter(true,port,"fixture-test",AccessReceiverContractTest.verifier("current",AccessReceiverContractTest.NOW),e->calls.incrementAndGet()));
            var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
            var fixture=AccessReceiverContractTest.load("http-cases.json").get(0);
            String bearer=fixture.get("authorization").asText();
            byte[] body=AccessReceiverContractTest.event();
            assertResponse(http,port,AccessEventFilter.ROUTE,"POST",bearer,body,204);
            assertThat(calls.get()).isEqualTo(1);
            assertResponse(http,port,AccessEventFilter.ROUTE+"?x=1","POST",bearer,body,401);
            assertResponse(http,port,AccessEventFilter.ROUTE+"/","POST",bearer,body,403);
            assertResponse(http,port,AccessEventFilter.ROUTE,"GET",bearer,body,403);
            assertResponse(http,port,AccessEventFilter.ROUTE,"POST","Bearer invalid",new byte[16385],401);
            assertResponse(http,port,AccessEventFilter.ROUTE,"POST",bearer,new byte[16385],413);
            assertResponse(http,port,AccessEventFilter.ROUTE,"POST",bearer,"{}".getBytes(),400);
            assertResponse(http,port,"/internal/v1/events/user-merged","POST",bearer,body,418);
            assertThat(calls.get()).isEqualTo(1);
            // Raw request preserves an empty query component; no HttpClient normalization.
            try(var socket=new Socket("127.0.0.1",port)) {
                socket.setSoTimeout(3000);
                String request="POST "+AccessEventFilter.ROUTE+"? HTTP/1.1\r\nHost: localhost\r\nAuthorization: "+bearer+"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                socket.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                String response=new String(socket.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.US_ASCII);
                assertThat(response).startsWith("HTTP/1.1 401");
            }
        } finally { server.stop(); server.destroy(); }
    }
    private void assertResponse(HttpClient client,int port,String path,String method,String auth,byte[] bytes,int expected) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(5))
                .header("Authorization",auth).header("X-Forwarded-Port","8443").method(method,HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        var response=client.send(request,HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(expected);
        if(expected==401 || expected==403 || expected==204) {
            assertThat(response.body()).isEmpty(); assertThat(response.headers().firstValue("cache-control")).contains("no-store");
        }
    }
}
