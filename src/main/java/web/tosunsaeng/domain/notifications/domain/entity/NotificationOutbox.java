package web.tosunsaeng.domain.notifications.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationOutboxStatus;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationType;

import java.time.Instant;

@Document(collection = "notification_outbox")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationOutbox {

    @Id
    private String notificationId;
    private String eventKey;
    private NotificationType type;
    private String userId;
    private String examId;
    private NotificationOutboxStatus status;
    private int attemptCount;
    private Instant nextAttemptAt;
    private Instant leaseUntil;
    private String lastErrorCode;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    public static NotificationOutbox pending(
            String notificationId,
            String eventKey,
            String userId,
            String examId,
            Instant now) {
        return NotificationOutbox.builder()
                .notificationId(notificationId)
                .eventKey(eventKey)
                .type(NotificationType.EXAM_GRADING_COMPLETED)
                .userId(userId)
                .examId(examId)
                .status(NotificationOutboxStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
