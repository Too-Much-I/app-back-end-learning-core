package web.tosunsaeng.domain.notifications.domain.enums;

public enum NotificationErrorCode {
    DEVICE_NOT_REGISTERED(false, true),
    INVALID_DEVICE_TOKEN(false, true),
    MESSAGE_RATE_EXCEEDED(true, false),
    PROVIDER_RATE_LIMITED(true, false),
    PROVIDER_UNAVAILABLE(true, false),
    PROVIDER_TIMEOUT(true, false),
    RECEIPT_NOT_READY(true, false),
    INVALID_CREDENTIALS(false, false),
    MISMATCH_SENDER_ID(false, false),
    MALFORMED_REQUEST(false, false),
    PROVIDER_RESPONSE_INVALID(true, false),
    DEVICE_UNAVAILABLE(false, false),
    OUTBOX_PROCESSING_FAILED(true, false),
    MAX_ATTEMPTS(false, false),
    PUSH_DISABLED(false, false),
    UNKNOWN_PROVIDER_ERROR(false, false);

    private final boolean retryable;
    private final boolean invalidDevice;

    NotificationErrorCode(boolean retryable, boolean invalidDevice) {
        this.retryable = retryable;
        this.invalidDevice = invalidDevice;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean isInvalidDevice() {
        return invalidDevice;
    }
}
