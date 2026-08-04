package web.tosunsaeng.global.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder, GradingProperties properties) {
        return builder
                .connectTimeout(properties.aiConnectTimeout())
                .readTimeout(properties.aiReadTimeout())
                .build();
    }
}
