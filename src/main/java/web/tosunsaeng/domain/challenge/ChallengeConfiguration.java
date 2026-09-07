package web.tosunsaeng.domain.challenge;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import java.time.Clock;

@Configuration
@ConditionalOnProperty(prefix = "app.challenge", name = "enabled", havingValue = "true")
public class ChallengeConfiguration {
    @Bean public ChallengeStore challengeStore(MongoTemplate mongo) { return new ChallengeStore(mongo); }
    @Bean public ChallengeTransactions challengeTransactions(MongoDatabaseFactory factory, UserOwnedTransactionExecutor guards) { return new ChallengeTransactions(factory, guards); }
    @Bean public ChallengeCatalog challengeCatalog(ChallengeStore store) { return new ChallengeCatalog(store.mongo, Clock.systemUTC()); }
    @Bean public ChallengeAudioStorage challengeAudioStorage(S3Client s3, S3Presigner presigner, @Value("${spring.cloud.aws.s3.bucket}") String bucket) {
        return new ChallengeAudioStorage(s3, presigner, bucket);
    }
    @Bean public ChallengeService challengeService(ChallengeStore store, ChallengeTransactions tx, ChallengeCatalog catalog, ChallengeAudioStorage audio) {
        return new ChallengeService(store, tx, catalog, audio, Clock.systemUTC());
    }
    @Bean public ChallengeMetrics challengeMetrics(MeterRegistry registry) { return new ChallengeMetrics(registry); }
    @Bean public ChallengeCallbackService challengeCallbackService(ChallengeStore store, ChallengeTransactions tx, ChallengeMetrics metrics) {
        return new ChallengeCallbackService(store, tx, Clock.systemUTC(), metrics);
    }
    @Bean public ChallengeAiClient challengeAiClient(ChallengeProperties properties, Environment environment) {
        properties.validate(environment.acceptsProfiles(Profiles.of("staging", "prod")));
        return new ChallengeAiClient(properties.getAiEndpoint(), properties.getOutboundCredential());
    }
    @Bean public ChallengeStartupValidator challengeStartupValidator(ChallengeStore store, ChallengeTransactions tx, ChallengeCatalog catalog,
                                                                    ChallengeProperties properties, Environment environment) {
        ChallengeStartupValidator validator = new ChallengeStartupValidator(store, tx, catalog);
        properties.validate(environment.acceptsProfiles(Profiles.of("staging", "prod")));
        validator.validate(); return validator;
    }
    @Bean @DependsOn("challengeStartupValidator")
    public ChallengeWorker challengeWorker(ChallengeStore store, ChallengeTransactions tx, ChallengeService service, ChallengeCallbackService callbacks,
                                            ChallengeAudioStorage audio, ChallengeAiClient ai, ChallengeMetrics metrics) {
        return new ChallengeWorker(store, tx, service, callbacks, audio, ai, Clock.systemUTC(), metrics);
    }
    @Bean
    public ChallengeScheduler challengeScheduler(ChallengeWorker worker, ChallengeProperties properties) {
        return new ChallengeScheduler(worker, properties);
    }
}
