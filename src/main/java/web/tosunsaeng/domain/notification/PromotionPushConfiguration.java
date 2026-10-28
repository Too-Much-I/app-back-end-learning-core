package web.tosunsaeng.domain.notification;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import io.micrometer.core.instrument.MeterRegistry;
import web.tosunsaeng.domain.promotion.PromotionProperties;
import web.tosunsaeng.domain.exams.billing.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="app.promotion-push.enabled",havingValue="true")
@EnableConfigurationProperties(PromotionPushProperties.class)
public class PromotionPushConfiguration {
    @Bean @DependsOn({"notificationStartupValidator","promotionService"})
    PromotionPushWorker promotionPushWorker(NotificationStore store,NotificationTransactions tx,
            PromotionPushProperties p,PromotionProperties campaign,NotificationProperties notifications,
            BillingWorkloadTokenIssuer issuer,Clock clock,MeterRegistry meters,org.springframework.core.env.Environment env){
        if(!campaign.isEnabled() || campaign.getOpensAt()==null || campaign.getClosesAt()==null
                || p.getBatchSize()<1 || p.getBatchSize()>100 || p.getRoute()==null || !p.getRoute().matches("[A-Z][A-Z0-9_]{1,63}")
                || p.getOptOutNotice()==null || p.getOptOutNotice().isBlank() || p.getOptOutNotice().length()>120)
            throw new IllegalStateException("Promotion push requires campaign, app route and opt-out notice");
        if(!notifications.isApiEnabled() || !"jwt".equals(env.getProperty("app.auth.mode")))
            throw new IllegalStateException("Promotion push requires authenticated device registration");
        for(String flag:java.util.List.of("app.user-merged.writer-enabled","app.user-merged.consumer-enabled",
                "app.user-merged.source-deny-enabled","app.user-withdrawn.consumer-enabled","app.user-withdrawn.deny-gate-enabled"))
            if(!env.getProperty(flag,Boolean.class,false))throw new IllegalStateException("Promotion push requires lifecycle gates");
        // Fail startup rather than silently skipping every user when the sender is OFF.
        issuer.authorization(BillingWorkloadTokenIssuer.Purpose.PROMOTION_ELIGIBILITY);
        issuer.authorization(BillingWorkloadTokenIssuer.Purpose.MARKETING_PUSH_ELIGIBILITY);
        PushGateway gateway=p.isDryRun()?new PushGateway(){
            public void prepare(){throw new IllegalStateException("Dry run");}
            public Result send(String token,String id,java.time.Instant expiry){throw new IllegalStateException("Dry run");}
        }:new FcmPushGateway(notifications.getProjectId(),notifications.getCredentialFile(),clock,
                content(p));
        try{
            var client=new PromotionEligibilityClient(p,issuer,clock);
            return new PromotionPushWorker(store,tx,p,campaign,client,gateway,clock,meters);
        }catch(RuntimeException invalid){
            if(gateway instanceof AutoCloseable closeable)try{closeable.close();}catch(Exception ignored){}
            throw invalid;
        }
    }
    static FcmPushGateway.Content content(PromotionPushProperties p){
        return new FcmPushGateway.Content(PromotionPushPolicy.TITLE,
                PromotionPushPolicy.BODY+"\n"+p.getOptOutNotice(),PromotionPushPolicy.TYPE,p.getRoute());
    }
    @Bean PromotionPushScheduler promotionPushScheduler(PromotionPushWorker worker){return new PromotionPushScheduler(worker);}
}
