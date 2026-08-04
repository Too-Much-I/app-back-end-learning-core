package web.tosunsaeng.domain.notifications.provider;

import web.tosunsaeng.domain.notifications.domain.enums.NotificationErrorCode;

public class PushProviderException extends RuntimeException {

    private final NotificationErrorCode errorCode;

    public PushProviderException(NotificationErrorCode errorCode) {
        super("Push provider request failed: " + errorCode.name());
        this.errorCode = errorCode;
    }

    public NotificationErrorCode getErrorCode() {
        return errorCode;
    }
}
