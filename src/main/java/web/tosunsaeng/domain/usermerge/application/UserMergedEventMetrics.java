package web.tosunsaeng.domain.usermerge.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class UserMergedEventMetrics {

    private final MeterRegistry registry;

    public UserMergedEventMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String outcome, long durationNanos) {
        String normalized = outcome.toLowerCase(Locale.ROOT);
        registry.counter("user_merged_event_total", "outcome", normalized).increment();
        Timer.builder("user_merged_transaction_duration")
                .tag("outcome", normalized)
                .register(registry)
                .record(Math.max(0L, durationNanos), TimeUnit.NANOSECONDS);
    }
}
