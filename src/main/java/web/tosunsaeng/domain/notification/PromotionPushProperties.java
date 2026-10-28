package web.tosunsaeng.domain.notification;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties("app.promotion-push")
public class PromotionPushProperties {
    private boolean enabled;
    private boolean dryRun=true;
    private String billingEndpoint;
    private String identityEndpoint;
    // Existing app contract (app-front-end develop 934d4b8a, 2026-10-28).
    private String route=PromotionPushPolicy.ROUTE;
    private String optOutNotice=PromotionPushPolicy.OPT_OUT_NOTICE;
    private int batchSize=20;
}
