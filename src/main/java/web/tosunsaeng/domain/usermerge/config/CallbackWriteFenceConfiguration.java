package web.tosunsaeng.domain.usermerge.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import web.tosunsaeng.domain.usermerge.application.CallbackWriteFence;

@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("${app.callback-write-fences.lifecycle-enabled:false} || ${app.callback-write-fences.isolation-enabled:false}")
public class CallbackWriteFenceConfiguration {
    @Bean
    public CallbackWriteFence callbackWriteFence(MongoTemplate mongo, Environment environment) {
        boolean lifecycle = environment.getProperty("app.callback-write-fences.lifecycle-enabled", Boolean.class, false);
        boolean isolation = environment.getProperty("app.callback-write-fences.isolation-enabled", Boolean.class, false);
        boolean writer = environment.getProperty("app.user-merged.writer-enabled", Boolean.class, false)
                || environment.getProperty("app.learning-record-deletion.writer-fence-enabled", Boolean.class, false);
        if (!lifecycle || !writer) throw new IllegalStateException("Callback isolation requires lifecycle and owner writer fences");
        // Provision outside transactions; simultaneous startup may create the same collection.
        if (!mongo.collectionExists(CallbackWriteFence.COLLECTION)) {
            try { mongo.createCollection(CallbackWriteFence.COLLECTION); }
            catch (RuntimeException failure) {
                if (!mongo.collectionExists(CallbackWriteFence.COLLECTION)) throw failure;
            }
        }
        return new CallbackWriteFence(mongo, isolation);
    }
}
