package web.tosunsaeng.domain.withdrawal.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserWithdrawnMetricsTest {

    @Test
    void recordsDeliveryLagWithoutIdentifiersOrNegativeValues() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        UserWithdrawnMetrics metrics = new UserWithdrawnMetrics(registry);

        metrics.recordDeliveryLag(
                Instant.parse("2026-08-28T00:00:00Z"),
                Instant.parse("2026-08-28T00:00:01.500Z")
        );
        metrics.recordDeliveryLag(
                Instant.parse("2026-08-28T00:00:01Z"),
                Instant.parse("2026-08-28T00:00:00Z")
        );

        var summary = registry.get("learning_core.user_withdrawn.delivery_lag").summary();
        assertEquals(2, summary.count());
        assertEquals(1.5, summary.totalAmount());
        assertEquals(0, summary.getId().getTags().size());
    }
}
