package web.tosunsaeng;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClients;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactoryBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import web.tosunsaeng.domain.challenge.*;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.usermerge.config.UserMergedConfiguration;
import web.tosunsaeng.domain.usermerge.config.UserMergedIndexValidator;
import web.tosunsaeng.domain.usermerge.config.UserMergedTransactionCapabilityProbe;
import web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;
import web.tosunsaeng.domain.withdrawal.config.UserWithdrawnConfiguration;
import web.tosunsaeng.global.config.auth.AuthProperties;
import web.tosunsaeng.global.config.security.SecurityErrorResponseHandler;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Real Boot discovery and real feature configurations; no repository mocks or manual factory. */
@Testcontainers
class MongoRepositoryDiscoveryIntegrationTest {
    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");

    private static final List<String> REPOSITORIES = List.of(
            "azureResultRepository", "examSummaryRepository", "mockExamRepository",
            "summaryGradingJobRepository", "questionGradingJobRepository", "examCreationOperationRepository",
            "speechAceResultRepository", "examResultRepository", "questionRepository", "examSessionRepository",
            "userOwnershipGuardRepository", "userMergedInboxRepository",
            "userWithdrawnEventInboxRepository", "withdrawnUserAccessDenyRepository");

    private WebApplicationContextRunner runner(String database) {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MongoAutoConfiguration.class,
                        MongoDataAutoConfiguration.class, MongoRepositoriesAutoConfiguration.class,
                        RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class,
                        SecurityAutoConfiguration.class))
                .withUserConfiguration(FeatureConfiguration.class)
                .withPropertyValues(
                        "spring.data.mongodb.uri=" + MONGO.getReplicaSetUrl(),
                        "spring.data.mongodb.database=" + database,
                        "app.user-merged.workload.issuer=https://identity.example.test",
                        "app.user-merged.workload.jwk-set-uri=https://identity.example.test/jwks",
                        "app.auth.identity.clock-skew=PT60S",
                        "app.user-withdrawn.max-accepted-access-token-lifetime=PT1H",
                        "app.user-withdrawn.allowed-verifier-clock-skew=PT60S",
                        "app.user-withdrawn.inbox-retention=P7D",
                        "app.user-withdrawn.maximum-future-event-skew=PT1M",
                        "app.user-withdrawn.workload.issuer=https://identity.example.test",
                        "app.user-withdrawn.workload.jwk-set-uri=https://identity.example.test/jwks",
                        "app.user-withdrawn.workload.audience=learning-core-user-withdrawn",
                        "app.user-withdrawn.workload.principal-claim=sub",
                        "app.user-withdrawn.workload.principal-value=identity-service",
                        "app.user-withdrawn.workload.max-token-lifetime=PT2M",
                        "app.user-withdrawn.workload.clock-skew=PT30S");
    }

    @ParameterizedTest
    @CsvSource({
            "local,false,false,false,false,false",
            "test,false,false,false,false,false",
            "staging,true,true,true,false,false",
            "prod,true,true,true,false,false",
            "test,false,false,false,true,true",
            "test,true,true,true,true,true",
            "test,true,false,false,false,false",
            "test,false,false,true,false,false",
            "test,false,false,false,false,true"
    })
    void repositoriesAreAlwaysRegisteredButBusinessFlagsStillApply(String profile, boolean writer,
            boolean consumer, boolean sourceDeny, boolean withdrawnConsumer, boolean withdrawnDeny) {
        runner("discovery-" + UUID.randomUUID()).withPropertyValues(
                "spring.profiles.active=" + profile,
                "app.user-merged.writer-enabled=" + writer,
                "app.user-merged.consumer-enabled=" + consumer,
                "app.user-merged.source-deny-enabled=" + sourceDeny,
                "app.user-withdrawn.consumer-enabled=" + withdrawnConsumer,
                "app.user-withdrawn.deny-gate-enabled=" + withdrawnDeny
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeanNamesForType(org.springframework.data.mongodb.repository.MongoRepository.class))
                    .containsExactlyInAnyOrderElementsOf(REPOSITORIES);
            for (String name : REPOSITORIES) {
                assertThat(context.getBean("&" + name)).isInstanceOf(MongoRepositoryFactoryBean.class);
            }
            assertThat(context.containsBean("userMergedConsumerService")).isEqualTo(consumer);
            assertThat(context.containsBean("userMergedWorkloadSecurityFilterChain")).isEqualTo(consumer);
            assertThat(context.containsBean("mergedUserAccessGateFilter")).isEqualTo(sourceDeny);
            assertThat(context.containsBean("userOwnedTransactionExecutor")).isEqualTo(writer || consumer || sourceDeny);
            assertThat(context.containsBean("userWithdrawnEventConsumerService")).isEqualTo(withdrawnConsumer);
            assertThat(context.containsBean("userWithdrawnAccessGateFilter")).isEqualTo(withdrawnDeny);
            assertThat(context).doesNotHaveBean(ChallengeWorker.class);
            assertThat(context.getBean(ExamSessionRepository.class).count()).isZero();
            var repository = context.getBean(UserOwnershipGuardRepository.class);
            String owner = UUID.randomUUID().toString();
            repository.save(UserOwnershipGuard.active(owner, Instant.now()));
            assertThat(repository.findById(owner)).isPresent();
        });
    }

    @Test
    void consumerWithoutWriterAndDenyStillFailsClosed() {
        runner("invalid-discovery").withPropertyValues("app.user-merged.consumer-enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "UserMerged configuration is invalid: consumer requires writer and source deny gate");
                });
    }

    @Test
    void challengeAndUserMergedStartTogetherWithRealRepositoriesAndTransactions() {
        String database = "challenge-discovery-" + UUID.randomUUID();
        try (var client = MongoClients.create(MONGO.getReplicaSetUrl())) {
            var mongo = new MongoTemplate(client, database);
            for (String collection : ChallengeStartupValidator.COLLECTIONS) mongo.createCollection(collection);
            for (var expected : ChallengeStartupValidator.INDEXES) {
                Index index = new Index().named(expected.name());
                expected.keys().keySet().forEach(key -> index.on(key, Sort.Direction.ASC));
                if (expected.unique()) index.unique();
                mongo.indexOps(expected.collection()).ensureIndex(index);
            }
            mongo.insert(new Document("dayNumber", 1).append("questions", List.of(
                    question(1), question(2), question(3))), "challenge_10s_questions");
            mongo.createCollection("user_ownership_guards");
            mongo.createCollection("user_merged_inbox_events");
            mongo.createCollection("user_merged_transaction_probe");
            mongo.indexOps("exam_results").ensureIndex(new Index().on("userId", Sort.Direction.ASC)
                    .named(UserMergedIndexValidator.RESULT_OWNER_INDEX));
            mongo.indexOps("exam_summaries").ensureIndex(new Index().on("userId", Sort.Direction.ASC)
                    .named(UserMergedIndexValidator.SUMMARY_OWNER_INDEX));
        }
        runner(database).withUserConfiguration(ChallengeConfiguration.class,
                        UserMergedIndexValidator.class, UserMergedTransactionCapabilityProbe.class)
                .withPropertyValues("spring.profiles.active=staging",
                        "app.user-merged.writer-enabled=true", "app.user-merged.consumer-enabled=true",
                        "app.user-merged.source-deny-enabled=true", "app.challenge.enabled=true",
                        "app.challenge.ai-endpoint=https://ai.example.test/v1/challenges/evaluations",
                        "app.challenge.outbound-credential=fixture-outbound",
                        "app.challenge.callback-credential=fixture-callback",
                        "spring.cloud.aws.s3.bucket=fixture-bucket")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // ContextRunner does not execute ApplicationRunner: explicitly exercise production probes.
                    context.getBean(UserMergedIndexValidator.class).run(null);
                    context.getBean(UserMergedTransactionCapabilityProbe.class).run(null);
                    assertThat(context).hasSingleBean(ChallengeWorker.class);
                    assertThat(context).hasSingleBean(ChallengeTransactions.class);
                    String owner = UUID.randomUUID().toString();
                    context.getBean(ChallengeTransactions.class).run(owner, () -> null);
                    assertThat(context.getBean(UserOwnershipGuardRepository.class).findById(owner)).isPresent();
                    assertThat(context.getBean(MongoTemplate.class).getCollection("user_merged_transaction_probe")
                            .countDocuments()).isZero();
                    assertThat(context).doesNotHaveBean("userWithdrawnEventConsumerService");
                });
    }

    private static Document question(int number) {
        return new Document("questionNumber", number).append("questionId", "fixture-" + number)
                .append("korean", "테스트 문장").append("referenceAnswer", "Fixture answer").append("difficulty", 1);
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage(basePackageClasses = TosunsaengApplication.class)
    @ComponentScan(basePackageClasses = {UserMergedConfiguration.class, UserWithdrawnConfiguration.class},
            useDefaultFilters = false, includeFilters = @ComponentScan.Filter(Configuration.class))
    @EnableConfigurationProperties({AuthProperties.class, ChallengeProperties.class})
    static class FeatureConfiguration {
        @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
        @Bean SecurityErrorResponseHandler errors() { return new SecurityErrorResponseHandler(new ObjectMapper()); }
        @Bean S3Client s3Client() { return mock(S3Client.class); }
        @Bean S3Presigner presigner() { return mock(S3Presigner.class); }
    }
}
