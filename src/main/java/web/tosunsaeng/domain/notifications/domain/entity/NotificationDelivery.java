package web.tosunsaeng.domain.notifications.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationDeliveryStatus;

import java.time.Instant;

@Document(collection = "notification_deliveries")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDelivery {

    @Id
    private String deliveryId;
    private String notificationId;
    private String deviceId;
    private String deviceTokenHashAtSend;
    private NotificationDeliveryStatus status;
    private int attemptCount;
    private Instant nextAttemptAt;
    private Instant leaseUntil;
    private String expoTicketId;
    private String lastErrorCode;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant ticketReceivedAt;
    private Instant receiptCheckedAt;
    private Instant sentAt;

    public static NotificationDelivery pending(
            String deliveryId,
            String notificationId,
            String deviceId,
            Instant now) {
        return NotificationDelivery.builder()
                .deliveryId(deliveryId)
                .notificationId(notificationId)
                .deviceId(deviceId)
                .status(NotificationDeliveryStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
