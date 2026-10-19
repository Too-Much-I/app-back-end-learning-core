package web.tosunsaeng.domain.withdrawal.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.time.Instant;

public class UserWithdrawnMetrics {

    private final MeterRegistry meterRegistry;

    public UserWithdrawnMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordConsumer(String outcome) {
        Counter.builder("learning_core.user_withdrawn.consumer")
                .tag("schemaVersion", "1")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    public void recordGate(String outcome) {
        Counter.builder("learning_core.user_withdrawn.deny_gate")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    public void recordDeliveryLag(Instant withdrawnAt, Instant receivedAt) {
        double seconds = Math.max(0L, Duration.between(withdrawnAt, receivedAt).toMillis()) / 1000.0;
        DistributionSummary.builder("learning_core.user_withdrawn.delivery_lag")
                .baseUnit("seconds")
                .register(meterRegistry)
                .record(seconds);
    }
}
