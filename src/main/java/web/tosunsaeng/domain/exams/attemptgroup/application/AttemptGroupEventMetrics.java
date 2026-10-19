package web.tosunsaeng.domain.exams.attemptgroup.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AttemptGroupEventMetrics {
    private static final String SERVICE = "learning-core";
    private static final String OPERATION = "attempt_group_outbox_publish";
    private final MeterRegistry registry;

    public AttemptGroupEventMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void event(String outcome) {
        Counter.builder("learning_core.attempt_group.outbox.events")
                .tag("service", SERVICE)
                .tag("operation", OPERATION)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public void duration(String outcome, long durationMs) {
        Timer.builder("learning_core.attempt_group.publish.duration")
                .tag("service", SERVICE)
                .tag("operation", OPERATION)
                .tag("outcome", outcome)
                .register(registry)
                .record(Duration.ofMillis(Math.max(0L, durationMs)));
    }

    public void age(String target, Duration age) {
        Timer.builder("learning_core.attempt_group.outbox.age")
                .tag("service", SERVICE)
                .tag("operation", OPERATION)
                .tag("target", target)
                .register(registry)
                .record(age.isNegative() ? Duration.ZERO : age);
    }

    public void counter(String name) {
        Counter.builder("learning_core.attempt_group." + name)
                .tag("service", SERVICE)
                .tag("operation", OPERATION)
                .register(registry)
                .increment();
    }
}
