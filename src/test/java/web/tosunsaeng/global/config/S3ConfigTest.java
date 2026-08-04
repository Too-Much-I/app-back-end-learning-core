package web.tosunsaeng.global.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3ConfigTest {

    private AnnotationConfigApplicationContext context;

    @BeforeAll
    void createContextWithoutCredentialProperties() {
        context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of(
                "spring.cloud.aws.region.static=ap-northeast-2",
                "spring.cloud.aws.s3.bucket=test-learning-core-bucket"
        ).applyTo(context);
        context.register(S3Config.class);
        context.refresh();
    }

    @AfterAll
    void closeContext() {
        context.close();
    }

    @Test
    void createsS3ClientAndPresignerBeansWithoutResolvingCredentials() {
        assertNotNull(context.getBean(S3Client.class));
        assertNotNull(context.getBean(S3Presigner.class));
    }

    @Test
    void usesDefaultCredentialsProviderInsteadOfStaticCredentials() {
        AwsCredentialsProvider provider = context.getBean(AwsCredentialsProvider.class);

        assertInstanceOf(DefaultCredentialsProvider.class, provider);
        assertFalse(provider instanceof StaticCredentialsProvider);
        assertEquals(1, context.getBeansOfType(AwsCredentialsProvider.class).size());
    }

    @Test
    void clientAndPresignerDependOnTheSameCredentialProviderBean() {
        assertTrue(Arrays.asList(
                context.getBeanFactory().getDependenciesForBean("s3Client")
        ).contains("awsCredentialsProvider"));
        assertTrue(Arrays.asList(
                context.getBeanFactory().getDependenciesForBean("s3Presigner")
        ).contains("awsCredentialsProvider"));
    }

    @Test
    void keepsConfiguredAwsRegion() {
        S3Client s3Client = context.getBean(S3Client.class);

        assertEquals(Region.AP_NORTHEAST_2, s3Client.serviceClientConfiguration().region());
    }

    @Test
    void presignerUsesConfiguredAwsRegionWithoutNetworkCall() {
        AwsCredentialsProvider testCredentialsProvider = () -> new TestCredentials();
        S3Config s3Config = new S3Config("ap-northeast-2");

        try (S3Presigner presigner = s3Config.s3Presigner(testCredentialsProvider)) {
            GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(1))
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket("test-bucket")
                            .key("test-object")
                            .build())
                    .build();

            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(request);

            assertTrue(extractCredentialScope(presignedRequest)
                    .endsWith("/ap-northeast-2/s3/aws4_request"));
        }
    }

    @Test
    void keepsExistingBucketPropertyContract() {
        assertEquals(
                "test-learning-core-bucket",
                context.getEnvironment().getProperty("spring.cloud.aws.s3.bucket")
        );
    }

    @Test
    void doesNotRequireProjectSpecificAccessKeyPropertiesAtStartup() {
        assertTrue(context.isActive());
        assertNull(context.getEnvironment().getProperty("spring.cloud.aws.credentials.access-key"));
        assertNull(context.getEnvironment().getProperty("spring.cloud.aws.credentials.secret-key"));
    }

    private String extractCredentialScope(PresignedGetObjectRequest presignedRequest) {
        return Arrays.stream(presignedRequest.url().getQuery().split("&"))
                .filter(parameter -> parameter.startsWith("X-Amz-Credential="))
                .map(parameter -> parameter.substring("X-Amz-Credential=".length()))
                .map(value -> URLDecoder.decode(value, StandardCharsets.UTF_8))
                .findFirst()
                .orElseThrow();
    }

    private static class TestCredentials implements AwsCredentials {

        @Override
        public String accessKeyId() {
            return "not-a-real-access-key";
        }

        @Override
        public String secretAccessKey() {
            return "not-a-real-secret-key";
        }
    }
}
