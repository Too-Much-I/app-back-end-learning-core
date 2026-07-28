package web.tosunsaeng.global.config.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import web.tosunsaeng.global.auth.LegacyCurrentUserProvider;
import web.tosunsaeng.global.config.SecurityConfig;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyProfileIsolationTest {

    private static final String[] VALID_REMOTE_JWT_PROPERTIES = {
            "app.auth.mode=jwt",
            "app.auth.identity.issuer=https://identity.example.test",
            "app.auth.identity.jwk-set-uri=https://identity.example.test/.well-known/jwks.json",
            "app.auth.identity.audience=tosunsaeng-learning-core"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AuthConfiguration.class, LegacyCurrentUserProvider.class);

    @Test
    void localLegacyRegistersLegacyProvider() {
        withProfile("local")
                .withPropertyValues("app.auth.mode=legacy")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LegacyCurrentUserProvider.class);
                });
    }

    @Test
    void testLegacyRegistersLegacyProvider() {
        withProfile("test")
                .withPropertyValues("app.auth.mode=legacy")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LegacyCurrentUserProvider.class);
                });
    }

    @Test
    void stagingJwtDoesNotRegisterLegacyProvider() {
        withProfile("staging")
                .withPropertyValues(VALID_REMOTE_JWT_PROPERTIES)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(LegacyCurrentUserProvider.class);
                });
    }

    @Test
    void prodJwtDoesNotRegisterLegacyProvider() {
        withProfile("prod")
                .withPropertyValues(VALID_REMOTE_JWT_PROPERTIES)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(LegacyCurrentUserProvider.class);
                });
    }

    @Test
    void legacySecurityFilterChainIsLimitedToLocalAndTestProfiles() {
        Method legacyFilterChain = Arrays.stream(SecurityConfig.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("legacySecurityFilterChain"))
                .findFirst()
                .orElseThrow();

        assertThat(legacyFilterChain.getAnnotation(Profile.class)).isNotNull();
        assertThat(legacyFilterChain.getAnnotation(Profile.class).value())
                .containsExactlyInAnyOrder("local", "test");
    }

    @Test
    void stagingRejectsAForcedLegacyProviderRegistration() {
        withProfile(
                new ApplicationContextRunner().withUserConfiguration(
                        AuthConfiguration.class,
                        ForcedLegacyProviderConfiguration.class
                ),
                "staging"
        )
                .withPropertyValues(VALID_REMOTE_JWT_PROPERTIES)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessage(
                            "LegacyCurrentUserProvider must not be registered in staging or prod"
                    );
                });
    }

    @Test
    void prodRejectsAForcedLegacySecurityFilterChainRegistration() {
        withProfile(
                new ApplicationContextRunner().withUserConfiguration(
                        AuthConfiguration.class,
                        ForcedLegacyFilterChainConfiguration.class
                ),
                "prod"
        )
                .withPropertyValues(VALID_REMOTE_JWT_PROPERTIES)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessage(
                            "Legacy SecurityFilterChain must not be registered in staging or prod"
                    );
                });
    }

    private ApplicationContextRunner withProfile(String profile) {
        return withProfile(contextRunner, profile);
    }

    private ApplicationContextRunner withProfile(ApplicationContextRunner runner, String profile) {
        return runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profile));
    }

    @Configuration(proxyBeanMethods = false)
    static class ForcedLegacyProviderConfiguration {

        @Bean
        LegacyCurrentUserProvider forcedLegacyCurrentUserProvider(AuthProperties authProperties) {
            return new LegacyCurrentUserProvider(authProperties);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ForcedLegacyFilterChainConfiguration {

        @Bean(name = "legacySecurityFilterChain")
        Object forcedLegacySecurityFilterChain() {
            return new Object();
        }
    }
}
