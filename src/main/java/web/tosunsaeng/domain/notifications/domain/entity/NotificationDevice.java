package web.tosunsaeng.domain.notifications.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationDevicePlatform;

import java.time.Instant;

@Document(collection = "notification_devices")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDevice {

    @Id
    private String id;
    private String userId;
    private String installationIdHash;
    private NotificationDevicePlatform platform;
    private String expoPushToken;
    private String expoPushTokenHash;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastRegisteredAt;
    private Instant disabledAt;

    public static NotificationDevice registered(
            String id,
            String userId,
            String installationIdHash,
            NotificationDevicePlatform platform,
            String expoPushToken,
            String expoPushTokenHash,
            Instant now) {
        return NotificationDevice.builder()
                .id(id)
                .userId(userId)
                .installationIdHash(installationIdHash)
                .platform(platform)
                .expoPushToken(expoPushToken)
                .expoPushTokenHash(expoPushTokenHash)
                .enabled(true)
                .createdAt(now)
                .updatedAt(now)
                .lastRegisteredAt(now)
                .build();
    }

    public void register(
            NotificationDevicePlatform platform,
            String expoPushToken,
            String expoPushTokenHash,
            Instant now) {
        this.platform = platform;
        this.expoPushToken = expoPushToken;
        this.expoPushTokenHash = expoPushTokenHash;
        this.enabled = true;
        this.updatedAt = now;
        this.lastRegisteredAt = now;
        this.disabledAt = null;
    }
}
