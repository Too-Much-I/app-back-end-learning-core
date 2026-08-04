package web.tosunsaeng.domain.notifications.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationDelivery;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationDevice;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationOutbox;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationErrorCode;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationDeviceRepository;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationOutboxRepository;
import web.tosunsaeng.domain.notifications.infrastructure.NotificationDeliveryStore;
import web.tosunsaeng.domain.notifications.provider.PushMessage;
import web.tosunsaeng.domain.notifications.provider.PushNotificationSender;
import web.tosunsaeng.domain.notifications.provider.PushProviderException;
import web.tosunsaeng.domain.notifications.provider.PushTicketBatchResult;
import web.tosunsaeng.domain.notifications.provider.PushTicketResult;
import web.tosunsaeng.global.config.NotificationPushProperties;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.notification.push", name = "enabled", havingValue = "true")
public class ExpoPushTicketWorker {

    private final NotificationDeliveryStore deliveryStore;
    private final NotificationDeviceRepository deviceRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationPayloadFactory payloadFactory;
    private final ExpoPushTokenValidator tokenValidator;
    private final PushNotificationSender sender;
    private final NotificationBackoffPolicy backoffPolicy;
    private final NotificationOutboxFinalizer finalizer;
    private final NotificationPushProperties properties;
    private final Clock clock;

    public int processBatch() {
        List<NotificationDelivery> claims = deliveryStore.claimTicketBatch(
                clock.instant(),
                properties.leaseDuration(),
                properties.batchSize()
        );
        if (claims.isEmpty()) {
            return 0;
        }

        List<TicketContext> ready = new ArrayList<>();
        for (NotificationDelivery claim : claims) {
            TicketContext context = prepare(claim);
            if (context != null) {
                ready.add(context);
            }
        }
        if (!ready.isEmpty()) {
            sendBatch(ready);
        }
        return claims.size();
    }

    private TicketContext prepare(NotificationDelivery claim) {
        Instant now = clock.instant();
        if (claim.getAttemptCount() > properties.maxAttempts()) {
            if (deliveryStore.fail(claim, NotificationErrorCode.MAX_ATTEMPTS, now)) {
                finalizer.finalizeIfTerminal(claim.getNotificationId());
            }
            return null;
        }

        NotificationOutbox outbox = outboxRepository.findById(claim.getNotificationId()).orElse(null);
        NotificationDevice device = deviceRepository.findById(claim.getDeviceId()).orElse(null);
        if (outbox == null || device == null || !device.isEnabled()
                || !Objects.equals(outbox.getUserId(), device.getUserId())) {
            if (deliveryStore.fail(claim, NotificationErrorCode.DEVICE_UNAVAILABLE, now)) {
                finalizer.finalizeIfTerminal(claim.getNotificationId());
            }
            return null;
        }

        String token = device.getExpoPushToken();
        String tokenHash = device.getExpoPushTokenHash();
        if (!tokenValidator.isValid(token)) {
            boolean updated = deliveryStore.deviceNotRegistered(
                    claim,
                    NotificationErrorCode.INVALID_DEVICE_TOKEN,
                    tokenHash,
                    now
            );
            if (updated) {
                deviceRepository.disableEnabledDeviceIfTokenMatches(device.getId(), tokenHash, now);
                finalizer.finalizeIfTerminal(claim.getNotificationId());
            }
            return null;
        }
        return new TicketContext(
                claim,
                device,
                payloadFactory.examGradingCompleted(outbox, token)
        );
    }

    private void sendBatch(List<TicketContext> contexts) {
        try {
            PushTicketBatchResult batchResult = sender.send(
                    contexts.stream().map(TicketContext::message).toList()
            );
            List<PushTicketResult> results = batchResult.results();
            if (results.size() != contexts.size()) {
                handleBatchFailure(contexts, NotificationErrorCode.PROVIDER_RESPONSE_INVALID);
                return;
            }
            for (int index = 0; index < contexts.size(); index++) {
                handleResult(contexts.get(index), results.get(index));
            }
        } catch (PushProviderException providerFailure) {
            handleBatchFailure(contexts, providerFailure.getErrorCode());
        }
    }

    private void handleBatchFailure(
            List<TicketContext> contexts,
            NotificationErrorCode errorCode) {
        contexts.forEach(context -> handleFailure(context, errorCode));
    }

    private void handleResult(TicketContext context, PushTicketResult result) {
        Instant now = clock.instant();
        if (result.successful()) {
            deliveryStore.markTicketReceived(
                    context.claim(),
                    result.ticketId(),
                    context.device().getExpoPushTokenHash(),
                    now.plus(properties.receiptDelay()),
                    now
            );
            return;
        }
        handleFailure(context, result.errorCode() == null
                ? NotificationErrorCode.PROVIDER_RESPONSE_INVALID
                : result.errorCode());
    }

    private void handleFailure(TicketContext context, NotificationErrorCode errorCode) {
        NotificationDelivery claim = context.claim();
        Instant now = clock.instant();
        if (errorCode.isInvalidDevice()) {
            String tokenHash = context.device().getExpoPushTokenHash();
            if (deliveryStore.deviceNotRegistered(claim, errorCode, tokenHash, now)) {
                deviceRepository.disableEnabledDeviceIfTokenMatches(
                        context.device().getId(), tokenHash, now
                );
                finalizer.finalizeIfTerminal(claim.getNotificationId());
            }
            return;
        }
        if (errorCode.isRetryable() && claim.getAttemptCount() < properties.maxAttempts()) {
            deliveryStore.rescheduleTicket(
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

    private record TicketContext(
            NotificationDelivery claim,
            NotificationDevice device,
            PushMessage message) {
    }
}
