package web.tosunsaeng.global.config.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AuthConfigurationTest {

    private static final String[] VALID_REMOTE_JWT_PROPERTIES = {
            "app.auth.mode=jwt",
            "app.auth.identity.issuer=https://identity.example.test",
            "app.auth.identity.jwk-set-uri=https://identity.example.test/.well-known/jwks.json",
            "app.auth.identity.audience=tosunsaeng-learning-core"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AuthConfiguration.class);

    @Test
    void legacyStringBindsToLegacyMode() {
        withProfile("local")
                .withPropertyValues("app.auth.mode=legacy")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AuthProperties.class).getMode()).isEqualTo(AuthMode.LEGACY);
                });
    }

    @Test
    void jwtStringBindsToJwtMode() {
        withProfile("local")
                .withPropertyValues(
                        "app.auth.mode=jwt",
                        "app.auth.identity.issuer=http://localhost:8081",
                        "app.auth.identity.jwk-set-uri=http://localhost:8081/.well-known/jwks.json",
                        "app.auth.identity.audience=tosunsaeng-learning-core"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AuthProperties.class).getMode()).isEqualTo(AuthMode.JWT);
                });
    }

    @Test
    void missingModeUsesLegacyDefaultInLocalProfile() {
        withProfile("local").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AuthProperties.class).getMode()).isEqualTo(AuthMode.LEGACY);
        });
    }

    @Test
    void testProfileAllowsLegacyMode() {
        withProfile("test")
                .withPropertyValues("app.auth.mode=legacy")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void legacyModeWithoutLocalOrTestProfileFails() {
        contextRunner
                .withPropertyValues("app.auth.mode=legacy")
                .run(context -> assertFailure(
                        context,
                        "Legacy authentication is allowed only for local and test profiles"
                ));
    }

    @Test
    void unsupportedModeFailsWithoutEchoingItsValue() {
        withProfile("local")
                .withPropertyValues("app.auth.mode=unsupported-mode")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(AuthMode.SUPPORTED_VALUES_MESSAGE)
                            .hasMessageNotContaining("unsupported-mode");
                });
    }

    @Test
    void uppercaseModeFailsBecauseOnlyLowercaseValuesAreSupported() {
        withProfile("local")
                .withPropertyValues("app.auth.mode=JWT")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(AuthMode.SUPPORTED_VALUES_MESSAGE);
                });
    }

    @Test
    void emptyModeFails() {
        withProfile("local")
                .withPropertyValues("app.auth.mode=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(AuthMode.SUPPORTED_VALUES_MESSAGE);
                });
    }

    @Test
    void stagingLegacyModeFails() {
        withProfile("staging")
                .withPropertyValues("app.auth.mode=legacy")
                .run(context -> assertFailure(
                        context,
                        "staging and prod profiles require jwt authentication mode"
                ));
    }

    @Test
    void prodLegacyModeFails() {
        withProfile("prod")
                .withPropertyValues("app.auth.mode=legacy")
                .run(context -> assertFailure(
                        context,
                        "staging and prod profiles require jwt authentication mode"
                ));
    }

    @Test
    void stagingJwtWithValidRemoteSettingsStarts() {
        withProfile("staging")
                .withPropertyValues(VALID_REMOTE_JWT_PROPERTIES)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void prodJwtWithValidRemoteSettingsStarts() {
        withProfile("prod")
                .withPropertyValues(VALID_REMOTE_JWT_PROPERTIES)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void stagingJwtWithoutIssuerFails() {
        withProfile("staging")
                .withPropertyValues(
                        "app.auth.mode=jwt",
                        "app.auth.identity.issuer=",
                        "app.auth.identity.jwk-set-uri=https://identity.example.test/.well-known/jwks.json",
                        "app.auth.identity.audience=tosunsaeng-learning-core"
                )
                .run(context -> assertFailure(
                        context,
                        "JWT issuer must be a non-empty HTTP(S) URI"
                ));
    }

    @Test
    void stagingJwtWithoutJwksUrlFails() {
        withProfile("staging")
                .withPropertyValues(
                        "app.auth.mode=jwt",
                        "app.auth.identity.issuer=https://identity.example.test",
                        "app.auth.identity.jwk-set-uri=",
                        "app.auth.identity.audience=tosunsaeng-learning-core"
                )
                .run(context -> assertFailure(
                        context,
                        "JWT JWKS URL must be a non-empty HTTP(S) URI"
                ));
    }

    @Test
    void stagingJwtWithoutAudienceFails() {
        withProfile("staging")
                .withPropertyValues(
                        "app.auth.mode=jwt",
                        "app.auth.identity.issuer=https://identity.example.test",
                        "app.auth.identity.jwk-set-uri=https://identity.example.test/.well-known/jwks.json"
                )
                .run(context -> assertFailure(context, "JWT audience must be configured"));
    }

    @Test
    void prodJwtWithoutIssuerFails() {
        withProfile("prod")
                .withPropertyValues(
                        "app.auth.mode=jwt",
                        "app.auth.identity.issuer=",
                        "app.auth.identity.jwk-set-uri=https://identity.example.test/.well-known/jwks.json",
                        "app.auth.identity.audience=tosunsaeng-learning-core"
                )
                .run(context -> assertFailure(
                        context,
                        "JWT issuer must be a non-empty HTTP(S) URI"
                ));
    }

    @Test
    void prodJwtWithoutJwksUrlFails() {
        withProfile("prod")
                .withPropertyValues(
                        "app.auth.mode=jwt",
                        "app.auth.identity.issuer=https://identity.example.test",
                        "app.auth.identity.jwk-set-uri=",
                        "app.auth.identity.audience=tosunsaeng-learning-core"
                )
                .run(context -> assertFailure(
                        context,
                        "JWT JWKS URL must be a non-empty HTTP(S) URI"
                ));
    }

    @Test
    void prodJwtWithoutAudienceFails() {
        withProfile("prod")
                .withPropertyValues(
                        "app.auth.mode=jwt",
                        "app.auth.identity.issuer=https://identity.example.test",
                        "app.auth.identity.jwk-set-uri=https://identity.example.test/.well-known/jwks.json",
                        "app.auth.identity.audience="
                )
                .run(context -> assertFailure(context, "JWT audience must be configured"));
    }

    @Test
    void prodJwtWithLocalIssuerFails() {
        withProfile("prod")
                .withPropertyValues(
                        "app.auth.mode=jwt",
                        "app.auth.identity.issuer=http://localhost:8081",
                        "app.auth.identity.jwk-set-uri=https://identity.example.test/.well-known/jwks.json",
                        "app.auth.identity.audience=tosunsaeng-learning-core"
                )
                .run(context -> assertFailure(
                        context,
                        "staging and prod profiles cannot use a local Identity issuer"
                ));
    }

    @Test
    void prodJwtWithLocalJwksUrlFails() {
        withProfile("prod")
                .withPropertyValues(
                        "app.auth.mode=jwt",
                        "app.auth.identity.issuer=https://identity.example.test",
                        "app.auth.identity.jwk-set-uri=http://localhost:8081/.well-known/jwks.json",
                        "app.auth.identity.audience=tosunsaeng-learning-core"
                )
                .run(context -> assertFailure(
                        context,
                        "staging and prod profiles cannot use a local Identity JWKS URL"
                ));
    }

    @Test
    void prodJwtWithPlaceholderAudienceFails() {
        withProfile("prod")
                .withPropertyValues(
                        "app.auth.mode=jwt",
                        "app.auth.identity.issuer=https://identity.example.test",
                        "app.auth.identity.jwk-set-uri=https://identity.example.test/.well-known/jwks.json",
                        "app.auth.identity.audience=change-me"
                )
                .run(context -> assertFailure(
                        context,
                        "staging and prod profiles require a non-placeholder JWT audience"
                ));
    }

    @Test
    void jwtIssuerMustBeAnHttpUri() {
        withProfile("local")
                .withPropertyValues(
                        "app.auth.mode=jwt",
                        "app.auth.identity.issuer=identity.example.test",
                        "app.auth.identity.jwk-set-uri=http://localhost:8081/.well-known/jwks.json",
                        "app.auth.identity.audience=tosunsaeng-learning-core"
                )
                .run(context -> assertFailure(
                        context,
                        "JWT issuer must be a non-empty HTTP(S) URI"
                ));
    }

    @Test
    void jwtJwksUrlMustBeAnHttpUri() {
        withProfile("local")
                .withPropertyValues(
                        "app.auth.mode=jwt",
                        "app.auth.identity.issuer=http://localhost:8081",
                        "app.auth.identity.jwk-set-uri=file:///tmp/jwks.json",
                        "app.auth.identity.audience=tosunsaeng-learning-core"
                )
                .run(context -> assertFailure(
                        context,
                        "JWT JWKS URL must be a non-empty HTTP(S) URI"
                ));
    }

    private ApplicationContextRunner withProfile(String profile) {
        return contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profile));
    }

    private void assertFailure(
            org.springframework.boot.test.context.assertj.AssertableApplicationContext context,
            String rootCauseMessage) {
        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure()).hasRootCauseMessage(rootCauseMessage);
    }
}
