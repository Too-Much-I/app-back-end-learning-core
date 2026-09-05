package web.tosunsaeng.domain.usermerge.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserMergedEventMetricsTest {

    @Test
    void recordsOnlyBoundedOutcomeAndNonNegativeDuration() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        UserMergedEventMetrics metrics = new UserMergedEventMetrics(registry);

        metrics.record("PROCESSED", -1L);

        assertThat(registry.get("user_merged_event_total")
                .tag("outcome", "processed").counter().count()).isEqualTo(1.0d);
        assertThat(registry.get("user_merged_transaction_duration")
                .tag("outcome", "processed").timer().totalTime(java.util.concurrent.TimeUnit.NANOSECONDS))
                .isZero();
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags())
                        .allSatisfy(tag -> assertThat(tag.getKey()).isEqualTo("outcome")));
    }
}
