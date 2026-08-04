package web.tosunsaeng.domain.notifications.provider;

import java.util.List;

public interface PushNotificationSender {

    PushTicketBatchResult send(List<PushMessage> messages);
}
