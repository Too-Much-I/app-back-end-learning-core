package web.tosunsaeng.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GradingInfrastructureConfigTest {

    private static final GradingProperties PROPERTIES = new GradingProperties(
            Duration.ofMinutes(1),
            Duration.ofMinutes(3),
            3,
            Duration.ofSeconds(4),
            Duration.ofSeconds(31),
            2,
            7
    );

    @Test
    void summaryExecutorUsesBoundedConfiguredPoolAndQueue() {
        ThreadPoolTaskExecutor executor = new GradingConfig().summaryDispatchExecutor(PROPERTIES);
        executor.initialize();
        try {
            assertAll(
                    () -> assertEquals(2, executor.getCorePoolSize()),
                    () -> assertEquals(2, executor.getMaxPoolSize()),
                    () -> assertEquals(7, executor.getThreadPoolExecutor().getQueue().remainingCapacity()),
                    () -> assertEquals("summary-grading-", executor.getThreadNamePrefix())
            );
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void restTemplateUsesConfiguredConnectAndReadTimeouts() {
        RestTemplateBuilder builder = new RestTemplateBuilder()
                .requestFactory(SimpleClientHttpRequestFactory::new);

        RestTemplate restTemplate = new RestTemplateConfig().restTemplate(builder, PROPERTIES);

        SimpleClientHttpRequestFactory requestFactory = assertInstanceOf(
                SimpleClientHttpRequestFactory.class,
                restTemplate.getRequestFactory()
        );
        assertAll(
                () -> assertEquals(4_000, ReflectionTestUtils.getField(requestFactory, "connectTimeout")),
                () -> assertEquals(31_000, ReflectionTestUtils.getField(requestFactory, "readTimeout"))
        );
    }
}
