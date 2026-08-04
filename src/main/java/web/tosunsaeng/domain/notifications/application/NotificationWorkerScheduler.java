package web.tosunsaeng.domain.notifications.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.notification.push", name = "enabled", havingValue = "true")
public class NotificationWorkerScheduler {

    private final NotificationOutboxWorker outboxWorker;
    private final ExpoPushTicketWorker ticketWorker;
    private final ExpoPushReceiptWorker receiptWorker;
    private final NotificationOutboxFinalizer finalizer;

    @Scheduled(fixedDelayString = "${app.notification.push.worker-delay}")
    public void processNotifications() {
        outboxWorker.processBatch();
        ticketWorker.processBatch();
        receiptWorker.processBatch();
        finalizer.finalizeBatch();
    }
}
