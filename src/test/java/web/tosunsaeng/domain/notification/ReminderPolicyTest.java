package web.tosunsaeng.domain.notification;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ReminderPolicyTest {
    @Test void kstWindowAndHistoryRetention() {
        assertThat(ReminderPolicy.inWindow(Instant.parse("2026-10-07T11:59:59Z"))).isFalse();
        assertThat(ReminderPolicy.inWindow(Instant.parse("2026-10-07T12:00:00Z"))).isTrue();
        assertThat(ReminderPolicy.inWindow(Instant.parse("2026-10-07T12:14:59Z"))).isTrue();
        assertThat(ReminderPolicy.inWindow(Instant.parse("2026-10-07T12:15:00Z"))).isFalse();
        assertThat(ReminderPolicy.day(Instant.parse("2026-10-07T15:00:00Z"))).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(ReminderPolicy.expiry(LocalDate.of(2026, 10, 7))).isEqualTo(Instant.parse("2026-11-06T15:00:00Z"));
    }
    @Test void safeDefaultsAndExplicitLifecycleGates() {
        var p = new NotificationProperties(); var env = new MockEnvironment();
        assertThat(p.isSendingEnabled()).isFalse(); assertThat(p.isTrackingEnabled()).isFalse(); assertThat(p.isApiEnabled()).isFalse(); assertThat(p.isDryRun()).isTrue();
        NotificationStartupValidator.validateSettings(p, env);
        p.setSendingEnabled(true);
        assertThatThrownBy(() -> NotificationStartupValidator.validateSettings(p, env)).isInstanceOf(IllegalStateException.class);
        p.setApiEnabled(true); p.setTrackingEnabled(true); p.setTrackingReadyAt(Instant.EPOCH); env.setProperty("app.auth.mode", "jwt");
        assertThatThrownBy(() -> NotificationStartupValidator.validateSettings(p, env)).isInstanceOf(IllegalStateException.class);
        for (String flag : List.of("app.user-merged.writer-enabled", "app.user-merged.consumer-enabled", "app.user-merged.source-deny-enabled",
                "app.user-withdrawn.consumer-enabled", "app.user-withdrawn.deny-gate-enabled")) env.setProperty(flag, "true");
        NotificationStartupValidator.validateSettings(p, env);
    }
    @Test void noSensitiveProviderDataInResultsAndPayloadOnlyRoutes() throws Exception {
        var now = Instant.parse("2026-10-07T12:00:00Z");
        assertThat(FcmPushGateway.classify(200, new byte[0], null, now).status()).isEqualTo(PushGateway.Status.ACCEPTED);
        assertThat(FcmPushGateway.classify(401, new byte[0], null, now).authFailure()).isTrue();
        assertThat(FcmPushGateway.classify(403, new byte[0], null, now).authFailure()).isTrue();
        assertThat(FcmPushGateway.classify(429, new byte[0], "120", now).retryAfterSeconds()).isEqualTo(120);
        assertThat(FcmPushGateway.classify(503, new byte[0], "Wed, 7 Oct 2026 12:03:00 GMT", now).retryAfterSeconds()).isEqualTo(180);
        byte[] error = "{\"error\":{\"details\":[{\"@type\":\"type.googleapis.com/google.firebase.fcm.v1.FcmError\",\"errorCode\":\"UNREGISTERED\"}]}}".getBytes();
        assertThat(FcmPushGateway.classify(404, error, null, now).invalidToken()).isTrue();
        assertThat(FcmPushGateway.classify(400, new byte[0], null, now).invalidToken()).isFalse();
        Map<?, ?> message = (Map<?, ?>) FcmPushGateway.payload("fake-token", "opaque-id", now.plusSeconds(900), 900).get("message");
        assertThat(message.get("data")).isEqualTo(Map.of("notificationId", "opaque-id", "type", ReminderPolicy.TYPE, "route", "MOCK_EXAM_HOME"));
        assertThat(new NotificationStore.Claim("id", "device", "owner", "sensitive-token", "hash", 1).toString()).doesNotContain("sensitive-token", "owner");
    }
    @Test void validationRejectsMalformedProofTokenAndStalePermission() {
        String id = UUID.randomUUID().toString(), secret = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        Instant now = Instant.parse("2026-10-07T12:00:00Z");
        NotificationStore.validate(id, secret, "IOS", "fixture-token-only-123456", "AUTHORIZED", now, now);
        assertThatThrownBy(() -> NotificationStore.validate(id, "bad", "IOS", "fixture-token-only-123456", "AUTHORIZED", now, now)).isInstanceOf(NotificationFailure.class);
        assertThatThrownBy(() -> NotificationStore.validate(id, secret, "IOS", "fixture-token-only-123456", "AUTHORIZED", now.minusSeconds(31 * 86400), now)).isInstanceOf(NotificationFailure.class);
        assertThatThrownBy(() -> NotificationStore.validate(id, secret, "IOS", "fixture-token-only-123456", "PROVISIONAL", now, now)).isInstanceOf(NotificationFailure.class);
    }
}
