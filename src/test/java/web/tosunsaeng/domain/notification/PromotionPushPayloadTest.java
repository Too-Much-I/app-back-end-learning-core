package web.tosunsaeng.domain.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PromotionPushPayloadTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Instant expiry = Instant.parse("2026-10-28T03:15:00Z");

    @Test void usesExistingAppRouteAndActualOptOutMenu() {
        var properties = new PromotionPushProperties();
        var message = json.valueToTree(FcmPushGateway.payload("fixture-token", "opaque-id", expiry, 900,
                PromotionPushConfiguration.content(properties))).path("message");

        assertThat(message.at("/data/route").asText()).isEqualTo("CASHBACK_EVENT_HOME");
        assertThat(message.at("/data/type").asText()).isEqualTo("CASHBACK_EVENT");
        assertThat(message.at("/notification/title").asText()).isEqualTo("(광고) 토선생 성적 인증 이벤트");
        assertThat(message.at("/notification/body").asText()).isEqualTo(
                "성적을 입력하고 기프티콘을 받아보세요!\n수신거부: 설정 > 알림 > 이벤트·혜택 알림");
        assertThat(message.at("/android/collapse_key").asText()).isEqualTo("CASHBACK_EVENT");
        assertThat(message.at("/apns/headers/apns-collapse-id").asText()).isEqualTo("CASHBACK_EVENT");
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isDryRun()).isTrue();
    }

    @Test void dailyReminderPayloadRemainsUnchangedAndSeparate() {
        var message = json.valueToTree(FcmPushGateway.payload("fixture-token", "opaque-id", expiry, 900))
                .path("message");
        assertThat(message.at("/data/route").asText()).isEqualTo("MOCK_EXAM_HOME");
        assertThat(message.at("/data/type").asText()).isEqualTo(ReminderPolicy.TYPE);
        assertThat(message.at("/notification/title").asText()).isEqualTo("오늘의 모의고사");
        assertThat(message.at("/notification/body").asText()).isEqualTo("오늘 모의고사로 영어 연습을 이어가 볼까요?");
        assertThat(message.at("/android/collapse_key").asText()).isNotEqualTo(PromotionPushPolicy.TYPE);
    }
}
