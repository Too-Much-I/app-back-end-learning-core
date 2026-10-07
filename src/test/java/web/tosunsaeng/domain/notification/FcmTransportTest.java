package web.tosunsaeng.domain.notification;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class FcmTransportTest {
    @Test void droppedResponseHasExactlyOneHttpAttemptAndNoRedirectRetry() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/drop", exchange -> { calls.incrementAndGet(); exchange.getRequestBody().readAllBytes(); exchange.close(); });
        server.createContext("/redirect", exchange -> { calls.incrementAndGet(); exchange.getResponseHeaders().add("Location", "/drop"); exchange.sendResponseHeaders(307, -1); exchange.close(); });
        server.start();
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC);
        try {
            for (String path : new String[]{"/drop", "/redirect"}) {
                calls.set(0);
                try (var client = new FcmPushGateway(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path), clock, "fixture-access-token")) {
                    var result = client.send("fixture-push-token", "fixture-id", clock.instant().plusSeconds(900));
                    assertThat(result.status()).isIn(PushGateway.Status.UNKNOWN, PushGateway.Status.FAILED);
                    assertThat(calls.get()).isEqualTo(1);
                }
            }
        } finally { server.stop(0); }
    }
}
