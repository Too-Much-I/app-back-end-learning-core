package web.tosunsaeng.domain.notifications.application;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationOutbox;
import web.tosunsaeng.domain.notifications.provider.PushMessage;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NotificationPayloadFactoryTest {

    @Test
    void gradingCompletedPayloadContainsOnlyRoutingDataAndExactCopy() {
        NotificationOutbox outbox = NotificationOutbox.pending(
                "notification-id",
                "EXAM_GRADING_COMPLETED:exam-id",
                "internal-user-id",
                "exam-id",
                Instant.parse("2026-08-04T06:00:00Z")
        );

        PushMessage message = new NotificationPayloadFactory()
                .examGradingCompleted(outbox, "ExpoPushToken[placeholder-value]");

        assertAll(
                () -> assertEquals("채점이 완료됐어요", message.title()),
                () -> assertEquals("모의고사 결과와 피드백을 확인해 보세요.", message.body()),
                () -> assertEquals("default", message.sound()),
                () -> assertEquals("grading", message.channelId()),
                () -> assertEquals(Set.of(
                        "type", "notificationId", "examId", "deepLink"),
                        message.data().keySet()),
                () -> assertEquals("EXAM_GRADING_COMPLETED", message.data().get("type")),
                () -> assertEquals("notification-id", message.data().get("notificationId")),
                () -> assertEquals("exam-id", message.data().get("examId")),
                () -> assertEquals("/exams/exam-id/summary", message.data().get("deepLink")),
                () -> assertFalse(message.data().containsKey("userId")),
                () -> assertFalse(message.data().containsKey("score")),
                () -> assertFalse(message.data().containsKey("feedback")),
                () -> assertFalse(message.data().containsKey("transcript")),
                () -> assertFalse(message.data().containsKey("presignedUrl")),
                () -> assertFalse(message.toString().contains("placeholder-value"))
        );
    }
}
