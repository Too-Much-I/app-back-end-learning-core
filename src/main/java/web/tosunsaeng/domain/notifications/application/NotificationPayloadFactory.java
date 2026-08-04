package web.tosunsaeng.domain.notifications.application;

import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationOutbox;
import web.tosunsaeng.domain.notifications.provider.PushMessage;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class NotificationPayloadFactory {

    static final String TITLE = "채점이 완료됐어요";
    static final String BODY = "모의고사 결과와 피드백을 확인해 보세요.";

    public PushMessage examGradingCompleted(NotificationOutbox outbox, String expoPushToken) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", outbox.getType().name());
        data.put("notificationId", outbox.getNotificationId());
        data.put("examId", outbox.getExamId());
        data.put("deepLink", "/exams/" + outbox.getExamId() + "/summary");
        return new PushMessage(
                expoPushToken,
                "default",
                "grading",
                TITLE,
                BODY,
                data
        );
    }
}
