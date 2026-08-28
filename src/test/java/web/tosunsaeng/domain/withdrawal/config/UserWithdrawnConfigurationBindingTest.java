package web.tosunsaeng.domain.withdrawal.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import web.tosunsaeng.global.config.auth.AuthProperties;

import static org.assertj.core.api.Assertions.assertThat;

class UserWithdrawnConfigurationBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ValidationConfiguration.class)
            .withPropertyValues("app.auth.identity.clock-skew=PT60S");

    @Test
    void gateOnlyRollbackModeStartsWithoutConsumerCredentials() {
        contextRunner
                .withPropertyValues(
                        "app.user-withdrawn.consumer-enabled=false",
                        "app.user-withdrawn.deny-gate-enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    UserWithdrawnConsumerProperties properties =
                            context.getBean(UserWithdrawnConsumerProperties.class);
                    assertThat(properties.isConsumerEnabled()).isFalse();
                    assertThat(properties.isDenyGateEnabled()).isTrue();
                });
    }

    @Test
    void consumerWithoutGateFailsStartup() {
        contextRunner
                .withPropertyValues(
                        "app.user-withdrawn.consumer-enabled=true",
                        "app.user-withdrawn.deny-gate-enabled=false"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void consumerAndGateStartWithExplicitContractValues() {
        contextRunner
                .withPropertyValues(
                        "app.user-withdrawn.consumer-enabled=true",
                        "app.user-withdrawn.deny-gate-enabled=true",
                        "app.user-withdrawn.max-accepted-access-token-lifetime=PT30M",
                        "app.user-withdrawn.allowed-verifier-clock-skew=PT60S",
                        "app.user-withdrawn.inbox-retention=P120D",
                        "app.user-withdrawn.maximum-future-event-skew=PT60S",
                        "app.user-withdrawn.workload.issuer=http://identity-workload.test",
                        "app.user-withdrawn.workload.jwk-set-uri=http://identity-workload.test/.well-known/jwks.json",
                        "app.user-withdrawn.workload.audience=learning-core-user-withdrawn",
                        "app.user-withdrawn.workload.principal-claim=service",
                        "app.user-withdrawn.workload.principal-value=identity",
                        "app.user-withdrawn.workload.max-token-lifetime=PT5M",
                        "app.user-withdrawn.workload.clock-skew=PT30S"
                )
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({UserWithdrawnConsumerProperties.class, AuthProperties.class})
    static class ValidationConfiguration {

        @Bean
        UserWithdrawnConfigurationValidator validator(
                UserWithdrawnConsumerProperties properties,
                AuthProperties authProperties) {
            return new UserWithdrawnConfigurationValidator(properties, authProperties, false);
        }
    }
}
