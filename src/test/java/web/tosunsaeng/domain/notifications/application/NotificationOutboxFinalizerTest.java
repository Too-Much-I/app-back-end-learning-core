package web.tosunsaeng.domain.notifications.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationDelivery;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationDeliveryStatus;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationErrorCode;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationOutboxStatus;
import web.tosunsaeng.domain.notifications.domain.repository.NotificationDeliveryRepository;
import web.tosunsaeng.domain.notifications.infrastructure.NotificationOutboxStore;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxFinalizerTest {

    private static final Instant NOW = Instant.parse("2026-08-04T06:00:00Z");

    @Mock
    private NotificationOutboxStore outboxStore;

    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    private NotificationOutboxFinalizer finalizer;

    @BeforeEach
    void setUp() {
        finalizer = new NotificationOutboxFinalizer(
                outboxStore,
                deliveryRepository,
                NotificationOutboxWorkerTest.properties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void atLeastOneSentDeliveryCompletesOutboxAfterAllAreTerminal() {
        when(deliveryRepository.findByNotificationId("notification-id")).thenReturn(List.of(
                delivery(NotificationDeliveryStatus.SENT),
                delivery(NotificationDeliveryStatus.FAILED)
        ));
        when(outboxStore.completeIfDeliveriesCreated(
                "notification-id", NotificationOutboxStatus.COMPLETED, null, NOW))
                .thenReturn(true);

        assertTrue(finalizer.finalizeIfTerminal("notification-id"));
    }

    @Test
    void allPermanentFailuresFailOutboxAndPendingDeliveryKeepsItOpen() {
        when(deliveryRepository.findByNotificationId("all-failed")).thenReturn(List.of(
                delivery(NotificationDeliveryStatus.FAILED),
                delivery(NotificationDeliveryStatus.DEVICE_NOT_REGISTERED)
        ));
        when(outboxStore.completeIfDeliveriesCreated(
                "all-failed",
                NotificationOutboxStatus.FAILED,
                NotificationErrorCode.UNKNOWN_PROVIDER_ERROR,
                NOW
        )).thenReturn(true);
        assertTrue(finalizer.finalizeIfTerminal("all-failed"));

        when(deliveryRepository.findByNotificationId("pending")).thenReturn(List.of(
                delivery(NotificationDeliveryStatus.PENDING)
        ));
        assertFalse(finalizer.finalizeIfTerminal("pending"));
        verify(outboxStore, never()).completeIfDeliveriesCreated(
                org.mockito.ArgumentMatchers.eq("pending"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private NotificationDelivery delivery(NotificationDeliveryStatus status) {
        return NotificationDelivery.builder().status(status).build();
    }
}
