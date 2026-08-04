package web.tosunsaeng.domain.notifications.provider;

import web.tosunsaeng.domain.notifications.domain.enums.NotificationErrorCode;

public record PushReceiptResult(boolean successful, NotificationErrorCode errorCode) {

    public static PushReceiptResult sent() {
        return new PushReceiptResult(true, null);
    }

    public static PushReceiptResult failure(NotificationErrorCode errorCode) {
        return new PushReceiptResult(false, errorCode);
    }
}
