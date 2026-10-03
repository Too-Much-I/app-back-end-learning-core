package web.tosunsaeng.domain.learningrecorddeletion.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import web.tosunsaeng.domain.learningrecorddeletion.application.*;
import web.tosunsaeng.domain.learningrecorddeletion.infrastructure.*;
import web.tosunsaeng.domain.learningrecorddeletion.analytics.*;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardService;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.learning-record-deletion", name = "writer-fence-enabled", havingValue = "true")
public class DeletionConfiguration {
    @Bean public DeletionAccess deletionAccess(MongoTemplate mongo) { return new DeletionAccess(mongo); }
    @Bean public DeletionTransactions deletionTransactions(MongoDatabaseFactory factory) { return new DeletionTransactions(factory); }
    @Bean public DeletionCommandService deletionCommandService(MongoTemplate mongo, DeletionTransactions tx, UserOwnershipGuardService guards, Clock clock) {
        return new DeletionCommandService(mongo, tx, guards, clock);
    }
    @Bean public DeletedExamContinuationStore deletedExamContinuationStore(MongoTemplate mongo, UserOwnershipGuardService guards, Clock clock) {
        return new DeletedExamContinuationStore(mongo, guards, clock);
    }
    @Bean public DeletionPersistenceFence deletionPersistenceFence(ObjectProvider<MongoTemplate> mongo, ObjectProvider<DeletionAccess> access,
            ObjectProvider<UserOwnershipGuardService> guards, Clock clock) {
        return new DeletionPersistenceFence(mongo::getIfAvailable, access::getIfAvailable, guards::getIfAvailable, clock);
    }
    @Bean public DeletionStartupValidator deletionStartupValidator(MongoTemplate mongo, org.springframework.core.env.Environment env) {
        return new DeletionStartupValidator(mongo, env);
    }
    @Bean public static LearningActivityRepositoryHooks learningActivityRepositoryHooks(ObjectProvider<LearningActivityRecorder> recorder,
            ObjectProvider<DeletionPersistenceFence> fence, ObjectProvider<MongoTemplate> mongo) {
        return new LearningActivityRepositoryHooks(recorder, fence, mongo);
    }
    @Bean @ConditionalOnProperty(prefix = "app.learning-record-deletion", name = "aggregate-enabled", havingValue = "true")
    public LearningActivityRecorder learningActivityRecorder(MongoTemplate mongo, Clock clock) { return new LearningActivityRecorder(mongo, clock); }
    @Bean @ConditionalOnProperty(prefix = "app.learning-record-deletion", name = "aggregate-enabled", havingValue = "true")
    public LearningActivityMongoCallback learningActivityMongoCallback(ObjectProvider<LearningActivityRecorder> recorder) {
        return new LearningActivityMongoCallback(recorder::getIfAvailable);
    }

    @Bean @ConditionalOnProperty(prefix = "app.learning-record-deletion", name = "worker-enabled", havingValue = "true")
    public DeletionStorage deletionStorage(S3Client s3, StringRedisTemplate redis, @Value("${spring.cloud.aws.s3.bucket}") String bucket) {
        return new S3RedisDeletionStorage(s3, redis, bucket);
    }
    @Bean @ConditionalOnProperty(prefix = "app.learning-record-deletion", name = "worker-enabled", havingValue = "true")
    public DeletionWorker deletionWorker(MongoTemplate mongo, DeletionTransactions tx, UserOwnershipGuardService guards,
            DeletedExamContinuationStore continuations, DeletionStorage storage, Clock clock) {
        return new DeletionWorker(mongo, tx, guards, continuations, storage, clock);
    }
    @Bean @DependsOn("deletionStartupValidator")
    @ConditionalOnProperty(prefix = "app.learning-record-deletion", name = "worker-enabled", havingValue = "true")
    public DeletionScheduler deletionScheduler(DeletionWorker worker, io.micrometer.core.instrument.MeterRegistry metrics) {
        return new DeletionScheduler(worker, metrics);
    }
}
