package web.tosunsaeng.domain.notifications.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationDelivery;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationDeliveryStatus;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationErrorCode;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationDeviceRepository;
import web.tosunsaeng.domain.notifications.infrastructure.NotificationDeliveryStore;
import web.tosunsaeng.domain.notifications.provider.PushProviderException;
import web.tosunsaeng.domain.notifications.provider.PushReceiptBatchResult;
import web.tosunsaeng.domain.notifications.provider.PushReceiptClient;
import web.tosunsaeng.domain.notifications.provider.PushReceiptResult;
import web.tosunsaeng.global.config.NotificationPushProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpoPushReceiptWorkerTest {

    private static final Instant NOW = Instant.parse("2026-08-04T06:20:00Z");

    @Mock
    private NotificationDeliveryStore deliveryStore;

    @Mock
    private NotificationDeviceRepository deviceRepository;

    @Mock
    private PushReceiptClient receiptClient;

    @Mock
    private NotificationOutboxFinalizer finalizer;

    private ExpoPushReceiptWorker worker;

    @BeforeEach
    void setUp() {
        NotificationPushProperties properties = NotificationOutboxWorkerTest.properties();
        worker = new ExpoPushReceiptWorker(
                deliveryStore,
                deviceRepository,
                receiptClient,
                new NotificationBackoffPolicy(properties),
                finalizer,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void receiptSuccessIsTheOnlyPointThatMarksDeliverySent() {
        NotificationDelivery claim = claim(2);
        stubClaim(claim);
        when(receiptClient.getReceipts(List.of("ticket-id")))
                .thenReturn(new PushReceiptBatchResult(Map.of(
                        "ticket-id", PushReceiptResult.sent()
                )));
        when(deliveryStore.markSent(claim, NOW)).thenReturn(true);

        worker.processBatch();

        verify(deliveryStore).markSent(claim, NOW);
        verify(finalizer).finalizeIfTerminal("notification-id");
    }

    @Test
    void deviceNotRegisteredReceiptDisablesOnlyTokenUsedForThatTicket() {
        NotificationDelivery claim = claim(2);
        stubClaim(claim);
        when(receiptClient.getReceipts(List.of("ticket-id")))
                .thenReturn(new PushReceiptBatchResult(Map.of(
                        "ticket-id",
                        PushReceiptResult.failure(NotificationErrorCode.DEVICE_NOT_REGISTERED)
                )));
        when(deliveryStore.deviceNotRegistered(
                claim,
                NotificationErrorCode.DEVICE_NOT_REGISTERED,
                "sent-token-hash",
                NOW
        )).thenReturn(true);

        worker.processBatch();

        verify(deviceRepository).disableEnabledDeviceIfTokenMatches(
                "device-id", "sent-token-hash", NOW
        );
        verify(finalizer).finalizeIfTerminal("notification-id");
    }

    @Test
    void messageRateExceededAndHttpFailureAreRetriedWithoutResendingTicket() {
        NotificationDelivery claim = claim(2);
        stubClaim(claim);
        when(receiptClient.getReceipts(List.of("ticket-id")))
                .thenThrow(new PushProviderException(NotificationErrorCode.PROVIDER_UNAVAILABLE));

        worker.processBatch();

        verify(deliveryStore).rescheduleReceipt(
                claim,
                NotificationErrorCode.PROVIDER_UNAVAILABLE,
                NOW.plusSeconds(60),
                NOW
        );
        verify(deliveryStore, never()).rescheduleTicket(any(), any(), any(), any());
    }

    private void stubClaim(NotificationDelivery claim) {
        when(deliveryStore.claimReceiptBatch(
                NOW,
                NOW.minus(Duration.ofMinutes(15)),
                Duration.ofMinutes(2),
                100
        )).thenReturn(List.of(claim));
    }

    private NotificationDelivery claim(int attempts) {
        return NotificationDelivery.builder()
                .deliveryId("delivery-id")
                .notificationId("notification-id")
                .deviceId("device-id")
                .deviceTokenHashAtSend("sent-token-hash")
                .status(NotificationDeliveryStatus.PROCESSING)
                .attemptCount(attempts)
                .expoTicketId("ticket-id")
                .ticketReceivedAt(NOW.minus(Duration.ofMinutes(20)))
                .build();
    }
}
