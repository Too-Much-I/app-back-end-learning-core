package web.tosunsaeng.domain.challenge;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;

public class ChallengeMetrics {
    public enum Stage { dispatch, callback, expiry }
    public enum Outcome { accepted, completed, no_speech, failed, retry, generation_retry, duplicate, stale, conflict, temporary_failure, lease_lost, expired }
    private static final Logger LOG = LoggerFactory.getLogger(ChallengeMetrics.class);
    private final MeterRegistry registry;
    public ChallengeMetrics(MeterRegistry registry) { this.registry = registry; }
    public void record(Stage stage, Outcome outcome, long startNanos) {
        registry.counter("learning_core.challenge.events", "stage", stage.name(), "outcome", outcome.name()).increment();
        io.micrometer.core.instrument.Timer.builder("learning_core.challenge.duration")
                .tags("stage", stage.name(), "outcome", outcome.name()).publishPercentileHistogram().register(registry)
                .record(Math.max(0, System.nanoTime() - startNanos), TimeUnit.NANOSECONDS);
        // Deliberately omit payloads, owner identifiers and exception stacks (including provider bodies).
        if (outcome == Outcome.failed || outcome == Outcome.conflict)
            LOG.warn("service=learning-core operation=challenge stage={} outcome={}", stage, outcome);
    }
}
