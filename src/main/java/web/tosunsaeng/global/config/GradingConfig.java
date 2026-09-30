package web.tosunsaeng.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import web.tosunsaeng.global.logging.MdcTaskDecorator;

@Configuration
@Import(ClockConfiguration.class)
@EnableConfigurationProperties(GradingProperties.class)
public class GradingConfig {

    @Bean(name = "summaryDispatchExecutor")
    public ThreadPoolTaskExecutor summaryDispatchExecutor(GradingProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.summaryDispatchThreads());
        executor.setMaxPoolSize(properties.summaryDispatchThreads());
        executor.setQueueCapacity(properties.summaryDispatchQueueCapacity());
        executor.setThreadNamePrefix("summary-grading-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setTaskDecorator(new MdcTaskDecorator());
        return executor;
    }
}
