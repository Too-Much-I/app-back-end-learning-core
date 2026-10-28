package web.tosunsaeng.domain.notification;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PromotionPushPolicyTest {
    final Instant opens=Instant.parse("2026-10-01T00:00:00Z"),closes=Instant.parse("2026-11-30T15:00:00Z");
    @Test void onlyWednesdayThursdayNoonKst(){
        assertThat(PromotionPushPolicy.inWindow(Instant.parse("2026-10-28T02:59:59Z"),opens,closes)).isFalse();
        assertThat(PromotionPushPolicy.inWindow(Instant.parse("2026-10-28T03:00:00Z"),opens,closes)).isTrue();
        assertThat(PromotionPushPolicy.inWindow(Instant.parse("2026-10-29T03:14:59Z"),opens,closes)).isTrue();
        assertThat(PromotionPushPolicy.inWindow(Instant.parse("2026-10-29T03:15:00Z"),opens,closes)).isFalse();
        assertThat(PromotionPushPolicy.inWindow(Instant.parse("2026-10-30T03:00:00Z"),opens,closes)).isFalse();
    }
    @Test void closeBoundaryAndOwnerCampaignDedup(){
        Instant now=Instant.parse("2026-10-28T03:05:00Z");
        assertThat(PromotionPushPolicy.inWindow(now,opens,now)).isFalse();
        assertThat(PromotionPushPolicy.expires(now,now.plusSeconds(1))).isEqualTo(now.plusSeconds(1));
        assertThat(PromotionPushPolicy.marker("campaign","owner",now)).isEqualTo(PromotionPushPolicy.marker("campaign","owner",now.plusSeconds(60)));
        assertThat(PromotionPushPolicy.marker("campaign","owner",now)).isNotEqualTo(PromotionPushPolicy.marker("other","owner",now));
    }
}
