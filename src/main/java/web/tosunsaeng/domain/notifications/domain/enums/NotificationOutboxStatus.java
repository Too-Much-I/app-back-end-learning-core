package web.tosunsaeng.domain.notifications.domain.enums;

public enum NotificationOutboxStatus {
    PENDING,
    PROCESSING,
    DELIVERIES_CREATED,
    COMPLETED,
    FAILED,
    SKIPPED_NO_DEVICE
}
