package web.tosunsaeng.domain.promotion;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.mongodb.core.MongoTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(PromotionProperties.class)
@ConditionalOnProperty(prefix="app.promotion",name="enabled",havingValue="true")
public class PromotionConfiguration {
    @Bean PromotionService promotionService(MongoTemplate mongo, S3Client s3, PromotionProperties config,
            UserOwnedTransactionExecutor guards, Clock clock) {
        config.validate();
        if (!guards.enabled()) throw new IllegalStateException("Promotion requires user-owned writer fence");
        // Collection must be prepared before transactional inserts; no runtime schema mutation.
        if (!mongo.collectionExists(PromotionService.COLLECTION))
            throw new IllegalStateException("Promotion application collection is not prepared");
        return new PromotionService(mongo,new PromotionReportStorage(s3,config.getBucket()),config,guards,clock);
    }
}
