package web.tosunsaeng.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    private final Region region;

    public S3Config(@Value("${spring.cloud.aws.region.static}") String region) {
        this.region = Region.of(region);
    }

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public S3Presigner s3Presigner(AwsCredentialsProvider awsCredentialsProvider) {
        return S3Presigner.builder()
                .region(region)
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }

    @Bean
    public S3Client s3Client(AwsCredentialsProvider awsCredentialsProvider) {
        return S3Client.builder()
                .region(region)
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }
}
