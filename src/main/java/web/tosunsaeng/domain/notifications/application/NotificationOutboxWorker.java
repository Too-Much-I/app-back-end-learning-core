package web.tosunsaeng.domain.notifications.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationDelivery;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationDevice;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationOutbox;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationErrorCode;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationDeliveryRepository;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationDeviceRepository;
import web.tosunsaeng.global.config.NotificationPushProperties;
import web.tosunsaeng.domain.notifications.infrastructure.NotificationOutboxStore;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.notification.push", name = "enabled", havingValue = "true")
public class NotificationOutboxWorker {

    private final NotificationOutboxStore outboxStore;
    private final NotificationDeviceRepository deviceRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationIdentityCodec identityCodec;
    private final NotificationBackoffPolicy backoffPolicy;
    private final NotificationPushProperties properties;
    private final Clock clock;

    public int processBatch() {
        int processed = 0;
        for (int index = 0; index < properties.batchSize(); index++) {
            Instant now = clock.instant();
            NotificationOutbox claim = outboxStore.claimNext(now, properties.leaseDuration());
            if (claim == null) {
                break;
            }
            processed++;
            processClaim(claim);
        }
        return processed;
    }

    private void processClaim(NotificationOutbox claim) {
        Instant now = clock.instant();
        if (claim.getAttemptCount() > properties.maxAttempts()) {
            outboxStore.fail(claim, NotificationErrorCode.MAX_ATTEMPTS, now);
            return;
        }
        try {
            List<NotificationDevice> devices = deviceRepository
                    .findByUserIdAndEnabledTrue(claim.getUserId());
            if (devices == null || devices.isEmpty()) {
                outboxStore.markSkippedNoDevice(claim, now);
                return;
            }
            for (NotificationDevice device : devices) {
                createDeliveryIdempotently(claim, device, now);
            }
            outboxStore.markDeliveriesCreated(claim, clock.instant());
        } catch (RuntimeException processingFailure) {
            Instant failedAt = clock.instant();
            if (claim.getAttemptCount() >= properties.maxAttempts()) {
                outboxStore.fail(claim, NotificationErrorCode.OUTBOX_PROCESSING_FAILED, failedAt);
            } else {
                outboxStore.reschedule(
                        claim,
                        NotificationErrorCode.OUTBOX_PROCESSING_FAILED,
                        failedAt.plus(backoffPolicy.delayForAttempt(claim.getAttemptCount())),
                        failedAt
                );
            }
        }
    }

    private void createDeliveryIdempotently(
            NotificationOutbox outbox,
            NotificationDevice device,
            Instant now) {
        String deliveryId = identityCodec.deliveryId(outbox.getNotificationId(), device.getId());
        try {
            deliveryRepository.insert(NotificationDelivery.pending(
                    deliveryId,
                    outbox.getNotificationId(),
                    device.getId(),
                    now
            ));
        } catch (DuplicateKeyException duplicate) {
            boolean expected = deliveryRepository.findById(deliveryId)
                    .filter(existing -> Objects.equals(
                            outbox.getNotificationId(), existing.getNotificationId()))
                    .filter(existing -> Objects.equals(device.getId(), existing.getDeviceId()))
                    .isPresent()
                    || deliveryRepository.existsByNotificationIdAndDeviceId(
                            outbox.getNotificationId(), device.getId()
                    );
            if (!expected) {
                throw duplicate;
            }
        }
    }
}
