package web.tosunsaeng.domain.notifications.provider;

import web.tosunsaeng.domain.notifications.domain.enums.NotificationErrorCode;

import java.util.List;

public final class DisabledPushNotificationSender implements PushNotificationSender {

    @Override
    public PushTicketBatchResult send(List<PushMessage> messages) {
        return new PushTicketBatchResult(messages.stream()
                .map(ignored -> PushTicketResult.failure(NotificationErrorCode.PUSH_DISABLED))
                .toList());
    }
}
