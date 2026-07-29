package web.tosunsaeng.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(GradingProperties.class)
public class GradingConfig {

    @Bean
    public Clock gradingClock() {
        return Clock.systemUTC();
    }

    @Bean(name = "summaryDispatchExecutor")
    public ThreadPoolTaskExecutor summaryDispatchExecutor(GradingProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.summaryDispatchThreads());
        executor.setMaxPoolSize(properties.summaryDispatchThreads());
        executor.setQueueCapacity(properties.summaryDispatchQueueCapacity());
        executor.setThreadNamePrefix("summary-grading-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
