package web.tosunsaeng.domain.notifications.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationDelivery;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationDevice;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationOutbox;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationDeliveryStatus;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationDevicePlatform;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpoPushTicketWorkerTest {

    private static final Instant NOW = Instant.parse("2026-08-04T06:00:00Z");

    @Mock
    private NotificationDeliveryStore deliveryStore;

    @Mock
    private NotificationDeviceRepository deviceRepository;

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @Mock
    private PushNotificationSender sender;

    @Mock
    private NotificationOutboxFinalizer finalizer;

    private NotificationPushProperties properties;
    private ExpoPushTicketWorker worker;

    @BeforeEach
    void setUp() {
        properties = NotificationOutboxWorkerTest.properties();
        worker = new ExpoPushTicketWorker(
                deliveryStore,
                deviceRepository,
                outboxRepository,
                new NotificationPayloadFactory(),
                new ExpoPushTokenValidator(),
                sender,
                new NotificationBackoffPolicy(properties),
                finalizer,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void ticketSuccessStoresTicketIdAndTokenHashWithoutMarkingSent() {
        NotificationDelivery claim = claim(1);
        stubReady(claim);
        when(sender.send(any())).thenReturn(new PushTicketBatchResult(
                List.of(PushTicketResult.ticket("ticket-id"))
        ));

        worker.processBatch();

        verify(deliveryStore).markTicketReceived(
                claim,
                "ticket-id",
                "token-hash",
                NOW.plus(Duration.ofMinutes(15)),
                NOW
        );
        verify(deliveryStore, never()).markSent(any(), any());
        ArgumentCaptor<List<PushMessage>> messages = ArgumentCaptor.forClass(List.class);
        verify(sender).send(messages.capture());
        assertEquals("/exams/exam-id/summary", messages.getValue().getFirst().data().get("deepLink"));
    }

    @Test
    void providerRateLimitBacksOffWithoutLosingClaim() {
        NotificationDelivery claim = claim(1);
        stubReady(claim);
        when(sender.send(any())).thenThrow(
                new PushProviderException(NotificationErrorCode.PROVIDER_RATE_LIMITED)
        );

        worker.processBatch();

        verify(deliveryStore).rescheduleTicket(
                claim,
                NotificationErrorCode.PROVIDER_RATE_LIMITED,
                NOW.plusSeconds(30),
                NOW
        );
        verify(deliveryStore, never()).fail(any(), any(), any());
    }

    @Test
    void deviceNotRegisteredDisablesOnlyCurrentTokenAfterDeliveryTransitionWins() {
        NotificationDelivery claim = claim(1);
        NotificationDevice device = stubReady(claim);
        when(sender.send(any())).thenReturn(new PushTicketBatchResult(List.of(
                PushTicketResult.failure(NotificationErrorCode.DEVICE_NOT_REGISTERED)
        )));
        when(deliveryStore.deviceNotRegistered(
                claim,
                NotificationErrorCode.DEVICE_NOT_REGISTERED,
                "token-hash",
                NOW
        )).thenReturn(true);

        worker.processBatch();

        verify(deviceRepository).disableEnabledDeviceIfTokenMatches(
                device.getId(), "token-hash", NOW
        );
        verify(finalizer).finalizeIfTerminal("notification-id");
    }

    @Test
    void exhaustedReclaimedLeaseFailsWithoutCallingExpo() {
        NotificationDelivery exhausted = claim(6);
        when(deliveryStore.claimTicketBatch(NOW, Duration.ofMinutes(2), 100))
                .thenReturn(List.of(exhausted));
        when(deliveryStore.fail(exhausted, NotificationErrorCode.MAX_ATTEMPTS, NOW))
                .thenReturn(true);

        worker.processBatch();

        verify(sender, never()).send(any());
        verify(deliveryStore).fail(exhausted, NotificationErrorCode.MAX_ATTEMPTS, NOW);
    }

    private NotificationDevice stubReady(NotificationDelivery claim) {
        NotificationDevice device = NotificationDevice.builder()
                .id("device-id")
                .userId("user-id")
                .platform(NotificationDevicePlatform.IOS)
                .expoPushToken("ExpoPushToken[placeholder-value]")
                .expoPushTokenHash("token-hash")
                .enabled(true)
                .build();
        when(deliveryStore.claimTicketBatch(NOW, Duration.ofMinutes(2), 100))
                .thenReturn(List.of(claim));
        when(deviceRepository.findById("device-id")).thenReturn(Optional.of(device));
        when(outboxRepository.findById("notification-id")).thenReturn(Optional.of(
                NotificationOutbox.builder()
                        .notificationId("notification-id")
                        .type(web.tosunsaeng.domain.notifications.domain.enums.NotificationType.EXAM_GRADING_COMPLETED)
                        .userId("user-id")
                        .examId("exam-id")
                        .build()
        ));
        return device;
    }

    private NotificationDelivery claim(int attempts) {
        return NotificationDelivery.builder()
                .deliveryId("delivery-id")
                .notificationId("notification-id")
                .deviceId("device-id")
                .status(NotificationDeliveryStatus.PROCESSING)
                .attemptCount(attempts)
                .build();
    }
}
