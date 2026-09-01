package web.tosunsaeng.domain.exams.attemptgroup.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(
        prefix = "app.attempt-group-events",
        name = "publisher-enabled",
        havingValue = "true"
)
public class SigV4AttemptGroupEventClient implements AttemptGroupEventClient {
    static final int MAX_RESPONSE_BYTES = 16 * 1024;
    private static final String SIGNING_NAME = "vpc-lattice-svcs";
    private final AttemptGroupEventProperties properties;
    private final AwsCredentialsProvider credentialsProvider;
    private final AwsV4HttpSigner signer;
    private final HttpClient httpClient;

    public SigV4AttemptGroupEventClient(
            AttemptGroupEventProperties properties,
            AwsCredentialsProvider credentialsProvider
    ) {
        this(properties, credentialsProvider, AwsV4HttpSigner.create(),
                HttpClient.newBuilder()
                        .connectTimeout(properties.connectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    SigV4AttemptGroupEventClient(
            AttemptGroupEventProperties properties,
            AwsCredentialsProvider credentialsProvider,
            AwsV4HttpSigner signer,
            HttpClient httpClient
    ) {
        this.properties = properties;
        this.credentialsProvider = credentialsProvider;
        this.signer = signer;
        this.httpClient = httpClient;
    }

    @Override
    public Response send(String canonicalPayload, String traceparent) {
        URI uri = endpoint();
        byte[] requestBody = canonicalPayload.getBytes(StandardCharsets.UTF_8);
        try {
            SdkHttpRequest unsigned = SdkHttpRequest.builder()
                    .uri(uri)
                    .method(SdkHttpMethod.POST)
                    .putHeader("Content-Type", "application/json")
                    .putHeader("traceparent", traceparent)
                    .build();
            ContentStreamProvider payload = ContentStreamProvider.fromByteArray(requestBody);

            // traceparent is final before signing. The signed request is immutable afterwards.
            SignedRequest signed = signer.sign(request -> request
                    .identity(credentialsProvider.resolveCredentials())
                    .request(unsigned)
                    .payload(payload)
                    .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, SIGNING_NAME)
                    .putProperty(AwsV4HttpSigner.REGION_NAME, properties.awsRegion()));

            HttpRequest.Builder http = HttpRequest.newBuilder(uri)
                    .timeout(properties.readTimeout())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody));
            signed.request().headers().forEach((name, values) -> {
                if (!isRestrictedHeader(name)) {
                    values.forEach(value -> http.header(name, value));
                }
            });
            HttpResponse<InputStream> response = httpClient.send(
                    http.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (body.readNBytes(MAX_RESPONSE_BYTES + 1).length > MAX_RESPONSE_BYTES) {
                    return new Response(422, null);
                }
            }
            return new Response(response.statusCode(), retryAfter(response));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new TransportException(interrupted);
        } catch (IOException | RuntimeException failure) {
            if (failure instanceof TransportException transportException) {
                throw transportException;
            }
            throw new TransportException(failure);
        }
    }

    private URI endpoint() {
        String base = properties.billingBaseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + "/internal/v1/attempt-group-events");
    }

    private static Integer retryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After")
                .flatMap(value -> {
                    try {
                        return java.util.Optional.of(Integer.parseInt(value));
                    } catch (NumberFormatException ignored) {
                        return java.util.Optional.empty();
                    }
                })
                .orElse(null);
    }

    private static boolean isRestrictedHeader(String name) {
        return "host".equalsIgnoreCase(name) || "content-length".equalsIgnoreCase(name);
    }
}
