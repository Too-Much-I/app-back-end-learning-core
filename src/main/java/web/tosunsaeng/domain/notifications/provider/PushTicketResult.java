package web.tosunsaeng.domain.notifications.provider;

import web.tosunsaeng.domain.notifications.domain.enums.NotificationErrorCode;

public record PushTicketResult(String ticketId, NotificationErrorCode errorCode) {

    public static PushTicketResult ticket(String ticketId) {
        return new PushTicketResult(ticketId, null);
    }

    public static PushTicketResult failure(NotificationErrorCode errorCode) {
        return new PushTicketResult(null, errorCode);
    }

    public boolean successful() {
        return ticketId != null && errorCode == null;
    }
}
