package web.tosunsaeng.domain.notifications.dto;

public final class NotificationDeviceResponse {

    private NotificationDeviceResponse() {
    }

    public record Registered(boolean registered) {
    }

    public record Disabled(boolean disabled) {
    }
}
