package web.tosunsaeng.domain.answerlearning;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import java.time.Clock;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(LearningProperties.class)
@ConditionalOnProperty(prefix="app.answer-learning",name="enabled",havingValue="true")
public class LearningConfiguration {
    @Bean public LearningStorage learningStorage(S3Client s3, S3Presigner signer, LearningProperties properties, Clock clock) {
        properties.validate(); return new LearningStorage(s3, signer, properties, clock);
    }
    @Bean public LearningStore learningStore(MongoTemplate mongo, ObjectProvider<UserOwnedTransactionExecutor> guards,
            ObjectProvider<web.tosunsaeng.domain.learningrecorddeletion.application.DeletionAccess> deletion) {
        var writer = guards.getIfAvailable();
        if (writer == null || !writer.enabled()) throw new IllegalStateException("Answer learning requires the user-owned transaction fence");
        LearningStore store = new LearningStore(mongo, writer, deletion.getIfAvailable()); store.indexes(); return store;
    }
    @Bean public LearningCatalog learningCatalog(MongoTemplate mongo, LearningProperties p, LearningStorage storage) { LearningCatalog catalog = new LearningCatalog(mongo,p,storage); catalog.validateDataset(); return catalog; }
    @Bean public LearningService learningService(LearningCatalog catalog, LearningStore store, LearningStorage storage, LearningProperties p, Clock clock) {
        return new LearningService(catalog,store,storage,p,clock);
    }
    @Bean public LearningLambdaClient learningLambdaClient(LearningProperties p, AwsCredentialsProvider credentials, ObjectMapper json) {
        return new LearningLambdaClient(p,credentials,json);
    }
    @Bean public LearningWorker learningWorker(LearningStore store, LearningLambdaClient lambda, LearningProperties p, MeterRegistry metrics, Clock clock) {
        return new LearningWorker(store,lambda,p,clock,metrics);
    }
}
