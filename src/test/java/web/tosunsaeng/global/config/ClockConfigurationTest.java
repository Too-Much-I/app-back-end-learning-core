package web.tosunsaeng.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import web.tosunsaeng.domain.exams.application.GradingDispatchService;
import web.tosunsaeng.domain.exams.application.SummaryDispatchScheduler;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.SummaryGradingJobRepository;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ClockConfigurationTest {
    @Test
    void unqualifiedSchedulerAndLegacyNamesResolveToOneUtcClock() {
        new ApplicationContextRunner()
                .withUserConfiguration(ClockConfiguration.class, GradingConfig.class, SummaryDispatchScheduler.class)
                .withBean(ExamSessionRepository.class, () -> mock(ExamSessionRepository.class))
                .withBean(SummaryGradingJobRepository.class, () -> mock(SummaryGradingJobRepository.class))
                .withBean(GradingDispatchService.class, () -> mock(GradingDispatchService.class))
                .withPropertyValues("app.grading.pending-timeout=PT1M", "app.grading.processing-timeout=PT3M",
                        "app.grading.max-dispatch-attempts=3", "app.grading.ai-server-url=http://ai.example.test",
                        "app.grading.ai-connect-timeout=PT4S", "app.grading.ai-read-timeout=PT30S",
                        "app.grading.summary-dispatch-threads=1", "app.grading.summary-dispatch-queue-capacity=10")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(Clock.class);
                    Clock clock = context.getBean(Clock.class);
                    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
                    for (String name : new String[]{"applicationClock", "gradingClock", "userMergedClock", "userWithdrawnClock"}) {
                        assertThat(context.getBean(name)).isSameAs(clock);
                    }
                    assertThat(ReflectionTestUtils.getField(context.getBean(SummaryDispatchScheduler.class), "clock"))
                            .isSameAs(clock);
                });
    }
}
