package web.tosunsaeng.domain.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** HTTP v1, a single POST with transport retries/redirects disabled. No Firebase SDK retry layer. */
public class FcmPushGateway implements PushGateway, AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String OAUTH = "https://oauth2.googleapis.com/token";
    private final CloseableHttpClient http;
    private final Clock clock;
    private final String project, email;
    private final String sendEndpoint;
    private final PrivateKey key;
    private String accessToken;
    private Instant tokenExpires = Instant.EPOCH;
    public FcmPushGateway(String project, String credentialFile, Clock clock) {
        this.clock = clock; this.project = project;
        this.sendEndpoint = "https://fcm.googleapis.com/v1/projects/" + project + "/messages:send";
        try {
            if (project == null || !project.matches("[a-z][a-z0-9-]{4,62}") || credentialFile == null) throw new IllegalArgumentException();
            Path path = Path.of(credentialFile);
            if (!path.isAbsolute() || Files.size(path) > 16384) throw new IllegalArgumentException();
            JsonNode credential = JSON.readTree(Files.readAllBytes(path));
            if (!"service_account".equals(credential.path("type").asText()) || !project.equals(credential.path("project_id").asText())) throw new IllegalArgumentException();
            email = credential.path("client_email").asText();
            if (!email.endsWith(".iam.gserviceaccount.com")) throw new IllegalArgumentException();
            String pem = credential.path("private_key").asText().replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        } catch (Exception e) { throw new IllegalStateException("Invalid FCM credential configuration"); }
        http = newHttpClient();
    }
    // Test seam for a loopback HTTP server; no configurable production endpoint or credential bypass.
    FcmPushGateway(java.net.URI loopbackEndpoint, Clock clock, String fakeAccessToken) {
        if (!"127.0.0.1".equals(loopbackEndpoint.getHost()) || !"http".equals(loopbackEndpoint.getScheme())) throw new IllegalArgumentException();
        this.clock = clock; this.project = "fixture"; this.email = null; this.key = null;
        this.sendEndpoint = loopbackEndpoint.toString(); this.accessToken = fakeAccessToken; this.tokenExpires = clock.instant().plusSeconds(3600);
        this.http = newHttpClient();
    }
    private static CloseableHttpClient newHttpClient() {
        return HttpClients.custom().disableAutomaticRetries().disableRedirectHandling().disableCookieManagement()
                .setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(3000).setSocketTimeout(5000).setConnectionRequestTimeout(1000).build()).build();
    }
    @Override public synchronized void prepare() {
        if (accessToken != null && tokenExpires.isAfter(clock.instant().plusSeconds(60))) return;
        try {
            long issued = clock.instant().getEpochSecond();
            String unsigned = b64(JSON.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT"))) + "."
                    + b64(JSON.writeValueAsBytes(Map.of("iss", email, "scope", "https://www.googleapis.com/auth/firebase.messaging",
                    "aud", OAUTH, "iat", issued, "exp", issued + 3600)));
            Signature signature = Signature.getInstance("SHA256withRSA"); signature.initSign(key); signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
            HttpPost post = new HttpPost(OAUTH);
            post.setHeader("Content-Type", "application/x-www-form-urlencoded");
            post.setEntity(new StringEntity("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion="
                    + unsigned + "." + b64(signature.sign()), StandardCharsets.UTF_8));
            try (var response = http.execute(post)) {
                int status = response.getStatusLine().getStatusCode();
                if (status != 200) throw new PreparationFailure(new Result(Status.FAILED, false, status >= 400 && status < 500 && status != 429, 60));
                JsonNode body = JSON.readTree(response.getEntity().getContent().readNBytes(16385));
                String token = body.path("access_token").asText(); long ttl = body.path("expires_in").asLong();
                if (token.isBlank() || token.length() > 8192 || ttl < 120 || ttl > 3600) throw new IllegalStateException();
                accessToken = token; tokenExpires = clock.instant().plusSeconds(ttl);
            }
        } catch (PreparationFailure failure) { throw failure; }
        catch (Exception e) { throw new PreparationFailure(new Result(Status.FAILED, false, false, 60)); }
    }
    @Override public Result send(String token, String notificationId, Instant expiresAt) {
        long ttl = Math.min(900, Duration.between(clock.instant(), expiresAt).getSeconds());
        if (ttl <= 0) return Result.skipped();
        try {
            if (accessToken == null || !tokenExpires.isAfter(clock.instant())) return new Result(Status.FAILED, false, true, 0);
            HttpPost post = new HttpPost(sendEndpoint);
            post.setHeader("Authorization", "Bearer " + accessToken); post.setHeader("Content-Type", "application/json; charset=UTF-8");
            post.setEntity(new StringEntity(JSON.writeValueAsString(payload(token, notificationId, expiresAt, ttl)), StandardCharsets.UTF_8));
            try (var response = http.execute(post)) {
                int status = response.getStatusLine().getStatusCode();
                byte[] body = response.getEntity() == null ? new byte[0] : response.getEntity().getContent().readNBytes(16385);
                var retry = response.getFirstHeader("Retry-After");
                return classify(status, body, retry == null ? null : retry.getValue(), clock.instant());
            }
        } catch (Exception e) { return Result.unknown(); }
    }
    static Map<String, Object> payload(String token, String id, Instant expiry, long ttl) {
        return Map.of("message", Map.of("token", token,
                "notification", Map.of("title", "오늘의 모의고사", "body", "오늘 모의고사로 영어 연습을 이어가 볼까요?"),
                "data", Map.of("notificationId", id, "type", ReminderPolicy.TYPE, "route", "MOCK_EXAM_HOME"),
                "android", Map.of("ttl", ttl + "s", "collapse_key", ReminderPolicy.TYPE),
                "apns", Map.of("headers", Map.of("apns-expiration", Long.toString(expiry.getEpochSecond()), "apns-collapse-id", ReminderPolicy.TYPE))));
    }
    static Result classify(int status, byte[] body, String retryAfter, Instant now) {
        if (status >= 200 && status < 300) return new Result(Status.ACCEPTED, false, false, 0);
        boolean invalid = false;
        try {
            if (body.length <= 16384) for (JsonNode detail : JSON.readTree(body).path("error").path("details"))
                if ("type.googleapis.com/google.firebase.fcm.v1.FcmError".equals(detail.path("@type").asText())
                        && "UNREGISTERED".equals(detail.path("errorCode").asText())) invalid = true;
        } catch (Exception ignored) { /* Provider text is never logged. */ }
        long delay = 0;
        if (status == 429 || status == 503) {
            delay = 60;
            if (retryAfter != null) {
                try { delay = Math.max(1, Math.min(86400, Long.parseLong(retryAfter))); }
                catch (NumberFormatException ignored) {
                    try { delay = Math.max(1, Math.min(86400, Duration.between(now, ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds())); }
                    catch (RuntimeException invalidHeader) { /* bounded default */ }
                }
            }
        }
        return new Result(Status.FAILED, invalid, status == 401 || status == 403, delay);
    }
    private static String b64(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    @Override public void close() throws java.io.IOException { http.close(); }
}
