package web.tosunsaeng.global.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import web.tosunsaeng.global.config.auth.AuthConfiguration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeploymentEnvironmentTest {
    MockEnvironment env(String target, String... profiles) {
        var env = new MockEnvironment().withProperty(DeploymentEnvironment.PROPERTY, target)
                .withProperty("app.auth.mode", "jwt");
        env.setActiveProfiles(profiles);
        return env;
    }

    @ParameterizedTest @ValueSource(strings = {"prod", "test"})
    void matchingDeploymentIsStrict(String target) {
        var env = env(target, target);
        assertThatCode(() -> new DeploymentEnvironment(env).afterPropertiesSet()).doesNotThrowAnyException();
        assertThat(DeploymentEnvironment.restricted(env)).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"staging", "production", "local", "unknown"})
    void unsupportedTargetsFail(String target) {
        assertThatThrownBy(() -> new DeploymentEnvironment(env(target, target)).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void mismatchMixedAndMissingProfilesFail() {
        for (String[] profiles : new String[][]{{"staging"}, {"prod"}, {"test", "local"}, {}}) {
            assertThatThrownBy(() -> new DeploymentEnvironment(env("test", profiles)).afterPropertiesSet())
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test void ecsCannotOmitDeploymentTarget() {
        for (String key : new String[]{"ECS_CONTAINER_METADATA_URI_V4", "ECS_CONTAINER_METADATA_URI"}) {
            var env = env("", "test").withProperty(key, "http://metadata.invalid");
            assertThat(DeploymentEnvironment.restricted(env)).isTrue();
            assertThatThrownBy(() -> new DeploymentEnvironment(env).afterPropertiesSet())
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test void localTestCompatibilityRemainsWithoutDeploymentTarget() {
        var env = env("", "test").withProperty("app.auth.mode", "legacy");
        assertThatCode(() -> new DeploymentEnvironment(env).afterPropertiesSet()).doesNotThrowAnyException();
        assertThat(DeploymentEnvironment.restricted(env)).isFalse();
    }

    @Test void deployedTestRejectsLegacyLocalIssuerAndPlaceholderAudience() {
        var runner = new ApplicationContextRunner().withUserConfiguration(AuthConfiguration.class, DeploymentEnvironment.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
                .withPropertyValues("app.deployment.environment=test", "app.auth.mode=jwt",
                        "app.auth.identity.issuer=https://identity.example.test",
                        "app.auth.identity.jwk-set-uri=https://identity.example.test/jwks",
                        "app.auth.identity.audience=learning-core");
        runner.run(context -> assertThat(context).hasNotFailed());
        for (String invalid : new String[]{"app.auth.mode=legacy",
                "app.auth.identity.issuer=http://localhost:8080",
                "app.auth.identity.jwk-set-uri=http://127.0.0.1/jwks",
                "app.auth.identity.audience=placeholder"}) {
            runner.withPropertyValues(invalid).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test void deployedTestEnablesAllPreviouslySkippedIndexAndTransactionChecks() {
        var context = mock(org.springframework.context.annotation.ConditionContext.class);
        when(context.getEnvironment()).thenReturn(env("test", "test"));
        for (Class<?> type : new Class<?>[]{
                ExamAssignmentIndexValidator.class,
                web.tosunsaeng.domain.usermerge.config.UserMergedIndexValidator.class,
                web.tosunsaeng.domain.withdrawal.config.UserWithdrawnIndexValidator.class,
                web.tosunsaeng.domain.exams.billing.BillingSagaTransactionCapabilityProbe.class,
                web.tosunsaeng.domain.usermerge.config.UserMergedTransactionCapabilityProbe.class,
                web.tosunsaeng.domain.withdrawal.config.UserWithdrawnTransactionCapabilityProbe.class,
                web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupTransactionCapabilityProbe.class}) {
            assertThat(type.getAnnotation(org.springframework.context.annotation.Profile.class)).isNull();
            var conditions = type.getAnnotation(org.springframework.context.annotation.Conditional.class);
            assertThat(conditions).as(type.getSimpleName()).isNotNull();
            for (var condition : conditions.value()) {
                try {
                    assertThat(condition.getDeclaredConstructor().newInstance().matches(context, null))
                            .as(type.getSimpleName()).isTrue();
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            }
        }
        when(context.getEnvironment()).thenReturn(env("", "test"));
        assertThat(new DeploymentEnvironment.Restricted().matches(context, null)).isFalse();
        assertThat(new DeploymentEnvironment.IndexChecks().matches(context, null)).isFalse();
    }
}
