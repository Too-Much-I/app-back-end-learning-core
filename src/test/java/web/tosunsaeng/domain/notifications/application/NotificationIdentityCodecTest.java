package web.tosunsaeng.domain.notifications.application;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.notifications.exception.NotificationException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationIdentityCodecTest {

    private final NotificationIdentityCodec codec = new NotificationIdentityCodec();

    @Test
    void installationIdIsCanonicalizedBeforeSha256Base64UrlHashing() {
        String lower = "550e8400-e29b-41d4-a716-446655440000";
        String upper = "550E8400-E29B-41D4-A716-446655440000";

        String hash = codec.installationIdHash(lower);

        assertEquals(hash, codec.installationIdHash(upper));
        assertEquals(43, hash.length());
        assertNotEquals(lower, hash);
    }

    @Test
    void nonV4UuidIsRejectedWithSafeNotificationError() {
        NotificationException exception = assertThrows(
                NotificationException.class,
                () -> codec.installationIdHash("550e8400-e29b-11d4-a716-446655440000")
        );

        assertSame(ErrorStatus._NOTIFICATION_DEVICE_INVALID_REQUEST, exception.getCode());
        assertThrows(
                NotificationException.class,
                () -> codec.installationIdHash(" 550e8400-e29b-41d4-a716-446655440000")
        );
    }

    @Test
    void deterministicIdsDoNotContainTheirSourceValues() {
        String installationHash = codec.installationIdHash(
                "550e8400-e29b-41d4-a716-446655440000"
        );
        String deviceId = codec.deviceId(
                "00000000-0000-4000-8000-000000000042",
                installationHash
        );
        String eventKey = "EXAM_GRADING_COMPLETED:exam-id";
        String notificationId = codec.notificationId(eventKey);
        String deliveryId = codec.deliveryId(notificationId, deviceId);

        assertEquals(true, deviceId.startsWith("dev_"));
        assertEquals(true, notificationId.startsWith("ntf_"));
        assertEquals(true, deliveryId.startsWith("dlv_"));
        assertEquals(false, notificationId.contains("exam-id"));
    }
}
