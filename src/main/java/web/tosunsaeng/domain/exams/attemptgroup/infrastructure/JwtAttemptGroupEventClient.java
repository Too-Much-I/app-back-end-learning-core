package web.tosunsaeng.domain.exams.attemptgroup.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.exams.billing.BillingWorkloadTokenIssuer;

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
public class JwtAttemptGroupEventClient implements AttemptGroupEventClient {
    static final int MAX_RESPONSE_BYTES = 16 * 1024;
    private final AttemptGroupEventProperties properties;
    private final BillingWorkloadTokenIssuer tokenIssuer;
    private final HttpClient httpClient;

    @org.springframework.beans.factory.annotation.Autowired
    public JwtAttemptGroupEventClient(
            AttemptGroupEventProperties properties,
            BillingWorkloadTokenIssuer tokenIssuer
    ) {
        this(properties, tokenIssuer,
                HttpClient.newBuilder()
                        .connectTimeout(properties.connectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    JwtAttemptGroupEventClient(
            AttemptGroupEventProperties properties,
            BillingWorkloadTokenIssuer tokenIssuer,
            HttpClient httpClient
    ) {
        this.properties = properties;
        this.tokenIssuer = tokenIssuer;
        this.httpClient = httpClient;
    }

    @Override
    public Response send(String canonicalPayload, String traceparent) {
        URI uri = endpoint();
        byte[] requestBody = canonicalPayload.getBytes(StandardCharsets.UTF_8);
        try {
            HttpRequest.Builder http = HttpRequest.newBuilder(uri)
                    .timeout(properties.readTimeout())
                    .header("Content-Type", "application/json")
                    .header("traceparent", traceparent)
                    .header("Authorization", tokenIssuer.authorization(BillingWorkloadTokenIssuer.Purpose.ATTEMPT_GROUP_EVENTS))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody));
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

}
