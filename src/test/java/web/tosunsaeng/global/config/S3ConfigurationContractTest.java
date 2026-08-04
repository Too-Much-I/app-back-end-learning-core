package web.tosunsaeng.global.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3ConfigurationContractTest {

    private static final Path MAIN_CONFIGURATION = Path.of("src/main/resources/application.yml");
    private static final Path TEST_CONFIGURATION = Path.of("src/test/resources/application-test.yml");
    private static final Path ENV_EXAMPLE = Path.of(".env.example");

    @Test
    void applicationConfigurationKeepsRegionAndBucketEnvironmentVariables() throws IOException {
        String configuration = Files.readString(MAIN_CONFIGURATION);

        assertTrue(configuration.contains("${AWS_REGION:ap-northeast-2}"));
        assertTrue(configuration.contains("${AWS_S3_BUCKET_NAME}"));
    }

    @Test
    void applicationConfigurationDoesNotRequireProjectSpecificStaticCredentials() throws IOException {
        assertDoesNotContainStaticCredentialConfiguration(Files.readString(MAIN_CONFIGURATION));
    }

    @Test
    void testConfigurationDoesNotProvideStaticCredentials() throws IOException {
        assertDoesNotContainStaticCredentialConfiguration(Files.readString(TEST_CONFIGURATION));
    }

    @Test
    void environmentExampleKeepsRegionAndBucketButOmitsAwsKeys() throws IOException {
        String envExample = Files.readString(ENV_EXAMPLE);

        assertTrue(envExample.contains("AWS_REGION="));
        assertTrue(envExample.contains("AWS_S3_BUCKET_NAME="));
        assertFalse(envExample.lines().anyMatch(line -> line.startsWith("AWS_ACCESS_KEY=")));
        assertFalse(envExample.lines().anyMatch(line -> line.startsWith("AWS_SECRET_KEY=")));
        assertFalse(envExample.lines().anyMatch(line -> line.startsWith("AWS_ACCESS_KEY_ID=")));
        assertFalse(envExample.lines().anyMatch(line -> line.startsWith("AWS_SECRET_ACCESS_KEY=")));
    }

    private void assertDoesNotContainStaticCredentialConfiguration(String configuration) {
        assertFalse(configuration.contains("credentials.access-key"));
        assertFalse(configuration.contains("credentials.secret-key"));
        assertFalse(configuration.contains("access-key:"));
        assertFalse(configuration.contains("secret-key:"));
    }
}
