package web.tosunsaeng.domain.notifications.dto;

public final class NotificationDeviceRequest {

    private NotificationDeviceRequest() {
    }

    public record Register(
            String installationId,
            String platform,
            String expoPushToken
    ) {
    }
}
