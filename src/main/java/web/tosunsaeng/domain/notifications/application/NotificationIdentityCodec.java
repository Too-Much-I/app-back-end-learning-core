package web.tosunsaeng.domain.notifications.application;

import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.notifications.exception.NotificationException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class NotificationIdentityCodec {

    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    public String installationIdHash(String installationId) {
        return sha256Base64Url(canonicalUuidV4(installationId));
    }

    public String expoPushTokenHash(String expoPushToken) {
        return sha256Base64Url(expoPushToken);
    }

    public String notificationId(String eventKey) {
        return "ntf_" + sha256Base64Url(eventKey);
    }

    public String deviceId(String userId, String installationIdHash) {
        return "dev_" + sha256Base64Url(userId + ":" + installationIdHash);
    }

    public String deliveryId(String notificationId, String deviceId) {
        return "dlv_" + sha256Base64Url(notificationId + ":" + deviceId);
    }

    String canonicalUuidV4(String value) {
        if (value == null) {
            throw invalidRequest();
        }
        String normalized = value.trim();
        if (!value.equals(normalized)) {
            throw invalidRequest();
        }
        if (!CANONICAL_UUID.matcher(normalized).matches()) {
            throw invalidRequest();
        }
        try {
            UUID uuid = UUID.fromString(normalized);
            if (uuid.version() != 4 || uuid.variant() != 2) {
                throw invalidRequest();
            }
            return uuid.toString().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw invalidRequest();
        }
    }

    private String sha256Base64Url(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private NotificationException invalidRequest() {
        return new NotificationException(ErrorStatus._NOTIFICATION_DEVICE_INVALID_REQUEST);
    }
}
