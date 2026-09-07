package web.tosunsaeng.domain.challenge;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.io.ByteArrayInputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static web.tosunsaeng.domain.challenge.ChallengeModels.*;

class ChallengeTransportTest {
    @Test void actualHttpMultipartStrict202AndNoRedirects() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger code = new AtomicInteger(202), redirected = new AtomicInteger(); AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>(), auth = new AtomicReference<>();
        Attempt a = ChallengeContractTest.attempt(); a.generation = 1; Job j = Job.pending(a, ChallengeContractTest.NOW);
        AtomicReference<String> response = new AtomicReference<>("{\"contract_version\":\"v1\",\"job_id\":\"" + j.id + "\",\"grading_attempt\":1,\"status\":\"accepted\"}");
        server.createContext("/v1/challenges/evaluations", x -> {
            body.set(new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)); key.set(x.getRequestHeaders().getFirst("Idempotency-Key"));
            auth.set(x.getRequestHeaders().getFirst("Authorization")); x.getResponseHeaders().add("Location", "/must-not-follow");
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8); x.sendResponseHeaders(code.get(), bytes.length); x.getResponseBody().write(bytes); x.close();
        });
        server.createContext("/must-not-follow", x -> { redirected.incrementAndGet(); x.sendResponseHeaders(200, -1); x.close(); }); server.start();
        try {
            ChallengeAiClient client = new ChallengeAiClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/challenges/evaluations"), "synthetic-outbound-fixture");
            assertThat(client.send(a, j, new byte[]{1,2,3}, Duration.ofSeconds(2)).accepted()).isTrue();
            assertThat(key.get()).isEqualTo(j.id); assertThat(auth.get()).isEqualTo("Bearer synthetic-outbound-fixture");
            assertThat(body.get()).contains("name=\"audio_file\"").doesNotContain(a.userId, "difficulty", "S3", a.uploadKey);
            response.set(response.get().replace("\"grading_attempt\":1", "\"grading_attempt\":\"1\""));
            assertThat(client.send(a, j, new byte[]{1}, Duration.ofSeconds(2))).isEqualTo(new ChallengeAiClient.Reply(false, false, null));
            code.set(307); assertThat(client.send(a, j, new byte[]{1}, Duration.ofSeconds(2)).retryable()).isFalse(); assertThat(redirected).hasValue(0);
            code.set(503); assertThat(client.send(a, j, new byte[]{1}, Duration.ofSeconds(2)).retryable()).isTrue();
        } finally { server.stop(0); }
    }
    @Test void httpBudgetIncludesSlowBodyAndBoundedResponseMemory() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch release = new CountDownLatch(1); AtomicBoolean oversized = new AtomicBoolean(false);
        ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor(); server.setExecutor(threads);
        server.createContext("/v1/challenges/evaluations", x -> {
            x.getRequestBody().readAllBytes(); x.sendResponseHeaders(202, 0);
            try { if (!oversized.get()) release.await(5, TimeUnit.SECONDS); x.getResponseBody().write(new byte[16385]); }
            catch (Exception ignored) { } finally { x.close(); }
        }); server.start();
        try {
            ChallengeAiClient client = new ChallengeAiClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/challenges/evaluations"), "synthetic-outbound-fixture");
            Attempt a = ChallengeContractTest.attempt(); a.generation = 1; Job j = Job.pending(a, ChallengeContractTest.NOW);
            long start = System.nanoTime(); assertThat(client.send(a, j, new byte[]{1}, Duration.ofMillis(80)).accepted()).isFalse();
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
            release.countDown(); oversized.set(true);
            assertThat(client.send(a, j, new byte[]{1}, Duration.ofSeconds(2)).accepted()).isFalse();
        } finally { release.countDown(); server.stop(0); threads.shutdownNow(); }
    }
    @Test void presignedUrlReissueUsesSameKeyAndClampsDeadline() {
        S3Client s3 = mock(S3Client.class);
        try (S3Presigner presigner = S3Presigner.builder().region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("synthetic-access", "synthetic-secret"))).build()) {
            ChallengeAudioStorage storage = new ChallengeAudioStorage(s3, presigner, "isolated-fixture-bucket");
            Attempt a = ChallengeContractTest.attempt(); Instant now = a.submissionDeadlineAt.minusSeconds(60);
            var first = storage.upload(a, now);
            var second = storage.upload(a, now.plusSeconds(10));
            assertThat(URI.create(first.url()).getPath()).isEqualTo("/" + a.uploadKey);
            assertThat(URI.create(second.url()).getPath()).isEqualTo("/" + a.uploadKey);
            assertThat(first.expiresAt()).isEqualTo(a.submissionDeadlineAt); assertThat(second.expiresAt()).isEqualTo(a.submissionDeadlineAt);
            assertThat(first.url()).contains("X-Amz-Expires=60"); assertThat(second.url()).contains("X-Amz-Expires=50");
        }
        verifyNoInteractions(s3);
    }
    @Test void downloadRechecksMetadataAndUsesBoundedRead() {
        S3Client s3 = mock(S3Client.class); AtomicBoolean aborted = new AtomicBoolean();
        var response = GetObjectResponse.builder().contentType("audio/mp4").contentLength(2L).build();
        var stream = new ResponseInputStream<>(response, AbortableInputStream.create(new ByteArrayInputStream(new byte[2_097_154]), () -> aborted.set(true)));
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(stream);
        var storage = new ChallengeAudioStorage(s3, mock(S3Presigner.class), "isolated-fixture-bucket");
        assertThatThrownBy(() -> storage.read("fixture-key")).satisfies(e -> assertThat(((ChallengeFailure)e).status).isEqualTo(413));
        assertThat(aborted).isTrue();
    }
    @ParameterizedTest @ValueSource(strings = {"http://ai.example.test/v1/challenges/evaluations", "https://ai.example.test/wrong", "https://user@ai.example.test/v1/challenges/evaluations", "https://ai.example.test/v1/challenges/evaluations?token=x"})
    void productionRejectsInvalidEndpoints(String endpoint) {
        ChallengeProperties p = new ChallengeProperties(); p.setEnabled(true); p.setAiEndpoint(URI.create(endpoint));
        p.setOutboundCredential("synthetic-one"); p.setCallbackCredential("synthetic-two");
        assertThatThrownBy(() -> p.validate(true)).isInstanceOf(IllegalStateException.class);
    }
    @Test void disabledConfigNeedsNoExternalValuesButEnabledNeedsSeparateCredentials() {
        ChallengeProperties p = new ChallengeProperties(); p.validate(true); p.setEnabled(true);
        p.setAiEndpoint(URI.create("https://ai.example.test/v1/challenges/evaluations"));
        p.setOutboundCredential("synthetic-one"); p.setCallbackCredential("synthetic-one");
        assertThatThrownBy(() -> p.validate(true)).isInstanceOf(IllegalStateException.class);
        p.setCallbackCredential("synthetic-two"); p.validate(true);
    }
}
