package web.tosunsaeng.domain.notifications.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationDelivery;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationOutbox;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationDeliveryStatus;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationErrorCode;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationOutboxStatus;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationDeliveryRepository;
import web.tosunsaeng.domain.notifications.infrastructure.NotificationOutboxStore;
import web.tosunsaeng.global.config.NotificationPushProperties;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.notification.push", name = "enabled", havingValue = "true")
public class NotificationOutboxFinalizer {

    private final NotificationOutboxStore outboxStore;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationPushProperties properties;
    private final Clock clock;

    public int finalizeBatch() {
        int finalized = 0;
        for (NotificationOutbox outbox : outboxStore.findAwaitingFinalization(properties.batchSize())) {
            if (finalizeIfTerminal(outbox.getNotificationId())) {
                finalized++;
            }
        }
        return finalized;
    }

    public boolean finalizeIfTerminal(String notificationId) {
        List<NotificationDelivery> deliveries = deliveryRepository.findByNotificationId(notificationId);
        if (deliveries == null || deliveries.isEmpty()
                || deliveries.stream().anyMatch(delivery -> !isTerminal(delivery.getStatus()))) {
            return false;
        }
        boolean anySent = deliveries.stream()
                .anyMatch(delivery -> delivery.getStatus() == NotificationDeliveryStatus.SENT);
        boolean allDeviceNotRegistered = deliveries.stream()
                .allMatch(delivery -> delivery.getStatus()
                        == NotificationDeliveryStatus.DEVICE_NOT_REGISTERED);
        return outboxStore.completeIfDeliveriesCreated(
                notificationId,
                anySent ? NotificationOutboxStatus.COMPLETED : NotificationOutboxStatus.FAILED,
                anySent ? null : (allDeviceNotRegistered
                        ? NotificationErrorCode.DEVICE_NOT_REGISTERED
                        : NotificationErrorCode.UNKNOWN_PROVIDER_ERROR),
                clock.instant()
        );
    }

    private boolean isTerminal(NotificationDeliveryStatus status) {
        return status == NotificationDeliveryStatus.SENT
                || status == NotificationDeliveryStatus.FAILED
                || status == NotificationDeliveryStatus.DEVICE_NOT_REGISTERED;
    }
}
