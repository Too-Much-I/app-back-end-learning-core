package web.tosunsaeng.domain.promotion;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties("app.promotion")
public class PromotionProperties {
    private boolean enabled;
    private String campaignId;
    private String bucket;
    private Instant opensAt;
    private Instant closesAt;

    void validate() {
        if (campaignId == null || !campaignId.matches("[A-Za-z0-9_-]{1,64}")
                || bucket == null || bucket.isBlank() || opensAt == null || closesAt == null
                || !opensAt.isBefore(closesAt))
            throw new IllegalStateException("Promotion requires campaign, private bucket and reception period");
    }
}
