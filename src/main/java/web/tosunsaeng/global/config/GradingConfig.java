package web.tosunsaeng.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(GradingProperties.class)
public class GradingConfig {

    @Bean
    public Clock gradingClock() {
        return Clock.systemUTC();
    }
}
