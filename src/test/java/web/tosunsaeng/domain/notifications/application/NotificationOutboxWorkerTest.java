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
import web.tosunsaeng.domain.notifications.domain.enums.NotificationDevicePlatform;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationDeliveryRepository;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationDeviceRepository;
import web.tosunsaeng.domain.notifications.infrastructure.NotificationOutboxStore;
import web.tosunsaeng.global.config.NotificationPushProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationOutboxWorkerTest {

    private static final Instant NOW = Instant.parse("2026-08-04T06:00:00Z");

    @Mock
    private NotificationOutboxStore outboxStore;

    @Mock
    private NotificationDeviceRepository deviceRepository;

    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    private NotificationOutboxWorker worker;

    @BeforeEach
    void setUp() {
        NotificationPushProperties properties = properties();
        worker = new NotificationOutboxWorker(
                outboxStore,
                deviceRepository,
                deliveryRepository,
                new NotificationIdentityCodec(),
                new NotificationBackoffPolicy(properties),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void enabledDevicesReceiveOneDeliveryEachWithoutCopyingPushToken() {
        NotificationOutbox claim = outboxClaim();
        when(outboxStore.claimNext(NOW, Duration.ofMinutes(2)))
                .thenReturn(claim, (NotificationOutbox) null);
        when(deviceRepository.findByUserIdAndEnabledTrue("user-id"))
                .thenReturn(List.of(device("device-1"), device("device-2")));

        assertEquals(1, worker.processBatch());

        ArgumentCaptor<NotificationDelivery> captor =
                ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals(2, captor.getAllValues().stream()
                .map(NotificationDelivery::getDeviceId).distinct().count());
        captor.getAllValues().forEach(delivery -> {
            assertEquals("notification-id", delivery.getNotificationId());
            assertNull(delivery.getDeviceTokenHashAtSend());
            assertNull(delivery.getExpoTicketId());
        });
        verify(outboxStore).markDeliveriesCreated(claim, NOW);
    }

    @Test
    void noEnabledDeviceMarksOutboxSkippedWithoutDelivery() {
        NotificationOutbox claim = outboxClaim();
        when(outboxStore.claimNext(NOW, Duration.ofMinutes(2)))
                .thenReturn(claim, (NotificationOutbox) null);
        when(deviceRepository.findByUserIdAndEnabledTrue("user-id")).thenReturn(List.of());

        worker.processBatch();

        verify(outboxStore).markSkippedNoDevice(claim, NOW);
        verify(deliveryRepository, never()).insert(any(NotificationDelivery.class));
    }

    private NotificationOutbox outboxClaim() {
        return NotificationOutbox.builder()
                .notificationId("notification-id")
                .eventKey("EXAM_GRADING_COMPLETED:exam-id")
                .userId("user-id")
                .examId("exam-id")
                .status(web.tosunsaeng.domain.notifications.domain.enums.NotificationOutboxStatus.PROCESSING)
                .attemptCount(1)
                .createdAt(NOW)
                .build();
    }

    private NotificationDevice device(String id) {
        return NotificationDevice.builder()
                .id(id)
                .userId("user-id")
                .installationIdHash("installation-hash-" + id)
                .platform(NotificationDevicePlatform.IOS)
                .expoPushToken("ExpoPushToken[placeholder-" + id + "]")
                .expoPushTokenHash("token-hash-" + id)
                .enabled(true)
                .build();
    }

    static NotificationPushProperties properties() {
        return new NotificationPushProperties(
                true,
                "expo",
                new NotificationPushProperties.Expo(
                        "https://expo.example.test/send",
                        "https://expo.example.test/receipts",
                        "",
                        false
                ),
                Duration.ofSeconds(5),
                Duration.ofMinutes(15),
                Duration.ofMinutes(2),
                5,
                Duration.ofSeconds(30),
                Duration.ofMinutes(30),
                100,
                Duration.ofSeconds(3),
                Duration.ofSeconds(10)
        );
    }
}
