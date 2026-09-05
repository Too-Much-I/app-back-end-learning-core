package web.tosunsaeng.domain.usermerge.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import web.tosunsaeng.domain.usermerge.application.UserMergedConsumerService;
import web.tosunsaeng.domain.usermerge.application.UserMergedEventMetrics;
import web.tosunsaeng.domain.usermerge.application.UserMergedTransactionService;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardService;
import web.tosunsaeng.domain.usermerge.repository.UserMergedInboxRepository;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;
import web.tosunsaeng.domain.usermerge.security.MergedUserAccessGateFilter;
import web.tosunsaeng.domain.usermerge.security.UserMergedWorkloadJwtValidator;
import web.tosunsaeng.global.config.security.JwtAudienceValidator;
import web.tosunsaeng.global.config.security.SecurityErrorResponseHandler;
import web.tosunsaeng.domain.exams.domain.repository.ExamCreationOperationRepository;
import web.tosunsaeng.domain.withdrawal.repository.WithdrawnUserAccessDenyRepository;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UserMergedProperties.class)
@ConditionalOnExpression("${app.user-merged.writer-enabled:false} || "
        + "${app.user-merged.consumer-enabled:false} || "
        + "${app.user-merged.source-deny-enabled:false}")
public class UserMergedConfiguration {

    private static final Profiles STAGING_OR_PROD = Profiles.of("staging", "prod");

    @Bean
    public UserMergedConfigurationValidator userMergedConfigurationValidator(
            UserMergedProperties properties,
            Environment environment
    ) {
        return new UserMergedConfigurationValidator(
                properties,
                environment.acceptsProfiles(STAGING_OR_PROD)
        );
    }

    @Bean(name = "userMergedClock")
    public Clock userMergedClock() {
        return Clock.systemUTC();
    }

    @Bean(name = "userOwnedMongoTransactionManager")
    @ConditionalOnExpression("${app.user-merged.writer-enabled:false} || "
            + "${app.user-merged.consumer-enabled:false}")
    public MongoTransactionManager userOwnedMongoTransactionManager(
            MongoDatabaseFactory databaseFactory
    ) {
        return new MongoTransactionManager(databaseFactory);
    }

    @Bean(name = "userOwnedTransactionOperations")
    @ConditionalOnExpression("${app.user-merged.writer-enabled:false} || "
            + "${app.user-merged.consumer-enabled:false}")
    public TransactionOperations userOwnedTransactionOperations(
            @Qualifier("userOwnedMongoTransactionManager") MongoTransactionManager transactionManager
    ) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    public UserOwnershipGuardService userOwnershipGuardService(
            MongoTemplate mongoTemplate,
            UserOwnershipGuardRepository repository
    ) {
        return new UserOwnershipGuardService(mongoTemplate, repository);
    }

    @Bean
    public UserOwnedTransactionExecutor userOwnedTransactionExecutor(
            UserMergedProperties properties,
            UserOwnershipGuardService guardService,
            @Qualifier("userOwnedTransactionOperations")
            ObjectProvider<TransactionOperations> transactionProvider,
            @Qualifier("userMergedClock") Clock clock
    ) {
        return new UserOwnedTransactionExecutor(
                properties,
                guardService,
                transactionProvider,
                clock
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.user-merged", name = "consumer-enabled", havingValue = "true")
    public UserMergedEventMetrics userMergedEventMetrics(MeterRegistry meterRegistry) {
        return new UserMergedEventMetrics(meterRegistry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.user-merged", name = "consumer-enabled", havingValue = "true")
    public UserMergedTransactionService userMergedTransactionService(
            UserMergedInboxRepository inboxRepository,
            WithdrawnUserAccessDenyRepository withdrawalRepository,
            ExamCreationOperationRepository operationRepository,
            UserOwnershipGuardService guardService,
            UserOwnedTransactionExecutor transactionExecutor,
            MongoTemplate mongoTemplate
    ) {
        return new UserMergedTransactionService(
                inboxRepository,
                withdrawalRepository,
                operationRepository,
                guardService,
                transactionExecutor,
                mongoTemplate
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.user-merged", name = "consumer-enabled", havingValue = "true")
    public UserMergedConsumerService userMergedConsumerService(
            UserMergedTransactionService transactionService,
            UserMergedInboxRepository inboxRepository,
            @Qualifier("userMergedClock") Clock clock,
            UserMergedEventMetrics metrics
    ) {
        return new UserMergedConsumerService(transactionService, inboxRepository, clock, metrics);
    }

    @Bean(name = "userMergedWorkloadJwtDecoder")
    @ConditionalOnProperty(prefix = "app.user-merged", name = "consumer-enabled", havingValue = "true")
    public JwtDecoder userMergedWorkloadJwtDecoder(UserMergedProperties properties) {
        UserMergedProperties.Workload workload = properties.getWorkload();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(workload.getJwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(workload.getClockSkew()),
                new JwtIssuerValidator(workload.getIssuer()),
                new JwtAudienceValidator(UserMergedProperties.AUDIENCE),
                new UserMergedWorkloadJwtValidator()
        ));
        return decoder;
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.user-merged", name = "source-deny-enabled", havingValue = "true")
    public MergedUserAccessGateFilter mergedUserAccessGateFilter(
            UserOwnershipGuardRepository repository,
            SecurityErrorResponseHandler responseHandler
    ) {
        return new MergedUserAccessGateFilter(repository, responseHandler);
    }
}
