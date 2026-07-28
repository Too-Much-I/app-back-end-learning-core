package web.tosunsaeng.global.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacJwtRemovalTest {

    @Test
    void legacyHmacClassesAreNotOnTheClasspath() {
        assertThatThrownBy(() -> Class.forName(
                "web.tosunsaeng.global.config.security.JwtAuthenticationFilter"
        )).isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName(
                "web.tosunsaeng.global.config.security.JwtTokenProvider"
        )).isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void buildAndActiveConfigurationDoNotUseJjwtOrSharedJwtSecret() throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        String buildFile = Files.readString(projectRoot.resolve("build.gradle"));
        String applicationConfig = Files.readString(projectRoot.resolve("src/main/resources/application.yml"));
        String localApplicationConfig = Files.readString(
                projectRoot.resolve("src/main/resources/application-local.yml")
        );
        String testConfig = Files.readString(projectRoot.resolve("src/test/resources/application-test.yml"));

        assertThat(buildFile).doesNotContain("io.jsonwebtoken", "jjwt-api", "jjwt-impl", "jjwt-jackson");
        assertThat(applicationConfig).doesNotContain("JWT_SECRET_KEY", "jwt:\n  secret:");
        assertThat(applicationConfig).contains(
                "issuer: ${IDENTITY_ISSUER:}",
                "jwk-set-uri: ${IDENTITY_JWK_SET_URI:}",
                "audience: ${IDENTITY_AUDIENCE:}"
        );
        assertThat(localApplicationConfig).contains(
                "issuer: ${IDENTITY_ISSUER:http://localhost:8081}",
                "jwk-set-uri: ${IDENTITY_JWK_SET_URI:http://localhost:8081/.well-known/jwks.json}",
                "audience: ${IDENTITY_AUDIENCE:tosunsaeng-learning-core}"
        );
        assertThat(testConfig).doesNotContain("JWT_SECRET_KEY", "jwt:\n  secret:");
    }
}
