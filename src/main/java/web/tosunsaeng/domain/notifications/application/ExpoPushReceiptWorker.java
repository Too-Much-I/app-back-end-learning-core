package web.tosunsaeng.domain.notifications.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationDelivery;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationErrorCode;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationDeviceRepository;
import web.tosunsaeng.domain.notifications.infrastructure.NotificationDeliveryStore;
import web.tosunsaeng.domain.notifications.provider.PushProviderException;
import web.tosunsaeng.domain.notifications.provider.PushReceiptBatchResult;
import web.tosunsaeng.domain.notifications.provider.PushReceiptClient;
import web.tosunsaeng.domain.notifications.provider.PushReceiptResult;
import web.tosunsaeng.global.config.NotificationPushProperties;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.notification.push", name = "enabled", havingValue = "true")
public class ExpoPushReceiptWorker {

    private final NotificationDeliveryStore deliveryStore;
    private final NotificationDeviceRepository deviceRepository;
    private final PushReceiptClient receiptClient;
    private final NotificationBackoffPolicy backoffPolicy;
    private final NotificationOutboxFinalizer finalizer;
    private final NotificationPushProperties properties;
    private final Clock clock;

    public int processBatch() {
        Instant now = clock.instant();
        List<NotificationDelivery> claims = deliveryStore.claimReceiptBatch(
                now,
                now.minus(properties.receiptDelay()),
                properties.leaseDuration(),
                properties.batchSize()
        );
        if (claims.isEmpty()) {
            return 0;
        }

        List<NotificationDelivery> ready = claims.stream()
                .filter(this::rejectExhaustedAttempt)
                .toList();
        if (ready.isEmpty()) {
            return claims.size();
        }
        try {
            PushReceiptBatchResult batchResult = receiptClient.getReceipts(
                    ready.stream().map(NotificationDelivery::getExpoTicketId).toList()
            );
            for (NotificationDelivery claim : ready) {
                PushReceiptResult result = batchResult.results().get(claim.getExpoTicketId());
                handleResult(claim, result == null
                        ? PushReceiptResult.failure(NotificationErrorCode.RECEIPT_NOT_READY)
                        : result);
            }
        } catch (PushProviderException providerFailure) {
            ready.forEach(claim -> handleFailure(claim, providerFailure.getErrorCode()));
        }
        return claims.size();
    }

    private boolean rejectExhaustedAttempt(NotificationDelivery claim) {
        if (claim.getAttemptCount() <= properties.maxAttempts()) {
            return true;
        }
        if (deliveryStore.fail(claim, NotificationErrorCode.MAX_ATTEMPTS, clock.instant())) {
            finalizer.finalizeIfTerminal(claim.getNotificationId());
        }
        return false;
    }

    private void handleResult(NotificationDelivery claim, PushReceiptResult result) {
        if (result.successful()) {
            if (deliveryStore.markSent(claim, clock.instant())) {
                finalizer.finalizeIfTerminal(claim.getNotificationId());
            }
            return;
        }
        handleFailure(claim, result.errorCode() == null
                ? NotificationErrorCode.PROVIDER_RESPONSE_INVALID
                : result.errorCode());
    }

    private void handleFailure(NotificationDelivery claim, NotificationErrorCode errorCode) {
        Instant now = clock.instant();
        if (errorCode.isInvalidDevice()) {
            String tokenHash = claim.getDeviceTokenHashAtSend();
            if (deliveryStore.deviceNotRegistered(claim, errorCode, tokenHash, now)) {
                if (tokenHash != null) {
                    deviceRepository.disableEnabledDeviceIfTokenMatches(
                            claim.getDeviceId(), tokenHash, now
                    );
                }
                finalizer.finalizeIfTerminal(claim.getNotificationId());
            }
            return;
        }
        if (errorCode.isRetryable() && claim.getAttemptCount() < properties.maxAttempts()) {
            deliveryStore.rescheduleReceipt(
                    claim,
                    errorCode,
                    now.plus(backoffPolicy.delayForAttempt(claim.getAttemptCount())),
                    now
            );
            return;
        }
        NotificationErrorCode finalError = errorCode.isRetryable()
                ? NotificationErrorCode.MAX_ATTEMPTS
                : errorCode;
        if (deliveryStore.fail(claim, finalError, now)) {
            finalizer.finalizeIfTerminal(claim.getNotificationId());
        }
    }
}
