package web.tosunsaeng.domain.withdrawal.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import web.tosunsaeng.domain.withdrawal.application.UserWithdrawnEventConsumerService;
import web.tosunsaeng.domain.withdrawal.application.UserWithdrawnEventTransactionService;
import web.tosunsaeng.domain.withdrawal.application.UserWithdrawnMetrics;
import web.tosunsaeng.domain.withdrawal.repository.UserWithdrawnEventInboxRepository;
import web.tosunsaeng.domain.withdrawal.repository.WithdrawnUserAccessDenyRepository;
import web.tosunsaeng.domain.withdrawal.security.JwtClaimEqualsValidator;
import web.tosunsaeng.domain.withdrawal.security.JwtMaximumLifetimeValidator;
import web.tosunsaeng.domain.withdrawal.security.UserWithdrawnAccessGateFilter;
import web.tosunsaeng.global.config.auth.AuthProperties;
import web.tosunsaeng.global.config.security.JwtAudienceValidator;
import web.tosunsaeng.global.config.security.SecurityErrorResponseHandler;

import java.time.Clock;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UserWithdrawnConsumerProperties.class)
@ConditionalOnExpression("${app.user-withdrawn.consumer-enabled:false} || "
        + "${app.user-withdrawn.deny-gate-enabled:false}")
public class UserWithdrawnConfiguration {

    private static final Profiles STAGING_OR_PROD = Profiles.of("staging", "prod");

    @Bean
    public Clock userWithdrawnClock() {
        return Clock.systemUTC();
    }

    @Bean
    public UserWithdrawnConfigurationValidator userWithdrawnConfigurationValidator(
            UserWithdrawnConsumerProperties properties,
            AuthProperties authProperties,
            Environment environment) {
        return new UserWithdrawnConfigurationValidator(
                properties,
                authProperties,
                environment.acceptsProfiles(STAGING_OR_PROD)
        );
    }

    @Bean(name = "userWithdrawnMongoTransactionManager")
    @ConditionalOnProperty(prefix = "app.user-withdrawn", name = "consumer-enabled", havingValue = "true")
    public MongoTransactionManager userWithdrawnMongoTransactionManager(
            MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }

    @Bean(name = "userWithdrawnTransactionOperations")
    @ConditionalOnProperty(prefix = "app.user-withdrawn", name = "consumer-enabled", havingValue = "true")
    public TransactionOperations userWithdrawnTransactionOperations(
            @Qualifier("userWithdrawnMongoTransactionManager") MongoTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean(name = "userWithdrawnWorkloadJwtDecoder")
    @ConditionalOnProperty(prefix = "app.user-withdrawn", name = "consumer-enabled", havingValue = "true")
    public JwtDecoder userWithdrawnWorkloadJwtDecoder(
            UserWithdrawnConsumerProperties properties) {
        UserWithdrawnConsumerProperties.Workload workload = properties.getWorkload();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(workload.getJwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(workload.getClockSkew());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtIssuerValidator(workload.getIssuer()),
                new JwtAudienceValidator(workload.getAudience()),
                new JwtClaimEqualsValidator(
                        workload.getPrincipalClaim(),
                        workload.getPrincipalValue()
                ),
                new JwtMaximumLifetimeValidator(workload.getMaxTokenLifetime())
        ));
        return decoder;
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.user-withdrawn", name = "consumer-enabled", havingValue = "true")
    public UserWithdrawnEventTransactionService userWithdrawnEventTransactionService(
            UserWithdrawnEventInboxRepository inboxRepository,
            WithdrawnUserAccessDenyRepository denyRepository) {
        return new UserWithdrawnEventTransactionService(inboxRepository, denyRepository);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.user-withdrawn", name = "consumer-enabled", havingValue = "true")
    public UserWithdrawnEventConsumerService userWithdrawnEventConsumerService(
            UserWithdrawnEventTransactionService transactionService,
            UserWithdrawnEventInboxRepository inboxRepository,
            WithdrawnUserAccessDenyRepository denyRepository,
            UserWithdrawnConsumerProperties properties,
            @Qualifier("userWithdrawnClock") Clock clock,
            UserWithdrawnMetrics metrics) {
        return new UserWithdrawnEventConsumerService(
                transactionService,
                inboxRepository,
                denyRepository,
                properties,
                clock,
                metrics
        );
    }

    @Bean
    public UserWithdrawnMetrics userWithdrawnMetrics(MeterRegistry meterRegistry) {
        return new UserWithdrawnMetrics(meterRegistry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.user-withdrawn", name = "deny-gate-enabled", havingValue = "true")
    public UserWithdrawnAccessGateFilter userWithdrawnAccessGateFilter(
            WithdrawnUserAccessDenyRepository denyRepository,
            @Qualifier("userWithdrawnClock") Clock clock,
            SecurityErrorResponseHandler responseHandler,
            UserWithdrawnMetrics metrics) {
        return new UserWithdrawnAccessGateFilter(denyRepository, clock, responseHandler, metrics);
    }

}
