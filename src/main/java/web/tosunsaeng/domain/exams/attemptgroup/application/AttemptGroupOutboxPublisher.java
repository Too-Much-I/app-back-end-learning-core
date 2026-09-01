package web.tosunsaeng.domain.exams.attemptgroup.application;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupEventOutbox;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventClient;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventProperties;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupOutboxStore;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupPublisherStateStore;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupTraceContext;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "app.attempt-group-events",
        name = "publisher-enabled",
        havingValue = "true"
)
public class AttemptGroupOutboxPublisher {
    private static final String SERVICE = "learning-core";
    private static final String OPERATION = "attempt_group_outbox_publish";
    private final AttemptGroupEventProperties properties;
    private final AttemptGroupOutboxStore outboxStore;
    private final AttemptGroupPublisherStateStore stateStore;
    private final AttemptGroupEventClient client;
    private final AttemptGroupTraceContext traceContext;
    private final AttemptGroupEventMetrics metrics;
    private final ObjectProvider<Tracer> tracerProvider;
    private final Clock clock;
    private final String owner = ManagementFactory.getRuntimeMXBean().getName()
            + ":" + UUID.randomUUID();

    public AttemptGroupOutboxPublisher(
            AttemptGroupEventProperties properties,
            AttemptGroupOutboxStore outboxStore,
            AttemptGroupPublisherStateStore stateStore,
            AttemptGroupEventClient client,
            AttemptGroupTraceContext traceContext,
            AttemptGroupEventMetrics metrics,
            ObjectProvider<Tracer> tracerProvider,
            @Qualifier("gradingClock") Clock clock
    ) {
        this.properties = properties;
        this.outboxStore = outboxStore;
        this.stateStore = stateStore;
        this.client = client;
        this.traceContext = traceContext;
        this.metrics = metrics;
        this.tracerProvider = tracerProvider;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.attempt-group-events.poll-interval:PT1S}")
    public void publishAvailable() {
        Instant now = clock.instant();
        AttemptGroupPublisherStateStore.Gate gate = stateStore.acquireGate(
                now, properties.authProbeInterval(), properties.leaseDuration());
        if (gate == AttemptGroupPublisherStateStore.Gate.BLOCKED) {
            return;
        }
        int limit = gate == AttemptGroupPublisherStateStore.Gate.PROBE ? 1 : properties.batchSize();
        if (gate == AttemptGroupPublisherStateStore.Gate.PROBE) {
            outboxStore.releaseBlocked(now);
        }
        for (int index = 0; index < limit; index++) {
            AttemptGroupEventOutbox event = outboxStore.claimNext(
                    owner, clock.instant(), clock.instant().plus(properties.leaseDuration()))
                    .orElse(null);
            if (event == null) {
                return;
            }
            PublishOutcome outcome = publish(event);
            if (outcome == PublishOutcome.AUTH_FAILURE) {
                return;
            }
            if (gate == AttemptGroupPublisherStateStore.Gate.PROBE) {
                return;
            }
        }
    }

    private PublishOutcome publish(AttemptGroupEventOutbox event) {
        long startedAt = System.nanoTime();
        Tracer tracer = tracerProvider.getIfAvailable();
        AttemptGroupTraceContext.StoredContext stored = effectiveContext(event);
        if (stored == null) {
            PublishOutcome lost = leaseLost();
            logOutcome(lost, safeTraceId(event.getTraceId()), event.getEventId(), 0L);
            return lost;
        }
        Span span = startSpan(tracer, stored);
        String traceId = span == null ? stored.traceId() : span.context().traceId();
        String spanId = span == null ? randomSpanId() : span.context().spanId();
        String flags = span == null ? safeFlags(stored.traceFlags())
                : Boolean.TRUE.equals(span.context().sampled()) ? "01" : "00";
        String traceparent = "00-%s-%s-%s".formatted(traceId, spanId, flags);

        PublishOutcome outcome;
        try (Tracer.SpanInScope ignored = span == null || tracer == null
                ? null : tracer.withSpan(span)) {
            AttemptGroupEventClient.Response response = client.send(
                    event.getCanonicalPayload(), traceparent);
            outcome = handleResponse(event, response);
        } catch (AttemptGroupEventClient.TransportException transportFailure) {
            outcome = scheduleRetry(event, "transport");
        } catch (RuntimeException unexpected) {
            outcome = scheduleRetry(event, "temporary_failure");
        } finally {
            if (span != null) {
                span.end();
            }
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        logOutcome(outcome, traceId, event.getEventId(), durationMs);
        metrics.event(outcome.logValue);
        metrics.duration(outcome.logValue, durationMs);
        if (event.getOccurredAt() != null) {
            metrics.age(event.getTargetStatus().name().toLowerCase(Locale.ROOT),
                    Duration.between(event.getOccurredAt(), clock.instant()));
        }
        return outcome;
    }

    private AttemptGroupTraceContext.StoredContext effectiveContext(AttemptGroupEventOutbox event) {
        if (traceContext.valid(event.getTraceId(), event.getParentSpanId())) {
            return new AttemptGroupTraceContext.StoredContext(
                    event.getTraceId(), event.getParentSpanId(), safeFlags(event.getTraceFlags()), false);
        }
        metrics.counter("trace_context_invalid");
        AttemptGroupTraceContext.StoredContext fallback = traceContext.captureOrCreate();
        return outboxStore.repairTraceContext(
                event.getEventId(), event.getLeaseToken(), fallback, clock.instant())
                ? fallback : null;
    }

    private Span startSpan(Tracer tracer, AttemptGroupTraceContext.StoredContext stored) {
        if (tracer == null) {
            return null;
        }
        TraceContext parent = traceContext.restore(tracer, stored);
        if (parent == null) {
            return tracer.spanBuilder().setNoParent().name(OPERATION).kind(Span.Kind.CLIENT).start();
        }
        return tracer.spanBuilder().setParent(parent).name(OPERATION).kind(Span.Kind.CLIENT).start();
    }

    private PublishOutcome handleResponse(
            AttemptGroupEventOutbox event,
            AttemptGroupEventClient.Response response
    ) {
        int status = response.statusCode();
        Instant now = clock.instant();
        if (status >= 200 && status < 300) {
            boolean updated = outboxStore.markDelivered(
                    event.getEventId(), event.getLeaseToken(), now,
                    now.plus(properties.deliveredRetention()));
            if (!updated) {
                return leaseLost();
            }
            stateStore.open(now);
            outboxStore.releaseBlocked(now);
            return PublishOutcome.DELIVERED;
        }
        if (status == 401 || status == 403) {
            boolean updated = outboxStore.markBlockedAuth(
                    event.getEventId(), event.getLeaseToken(), "http_" + status, now);
            if (!updated) {
                return leaseLost();
            }
            stateStore.block(now, properties.authProbeInterval());
            metrics.counter("auth_failure");
            return PublishOutcome.AUTH_FAILURE;
        }
        if (status == 408 || status == 425 || status == 429 || status >= 500) {
            return scheduleRetry(event, "http_" + status, response.retryAfterSeconds());
        }
        boolean updated = outboxStore.markDeadLetter(
                event.getEventId(), event.getLeaseToken(), "http_" + status,
                now, now.plus(properties.deadLetterRetention()));
        if (!updated) {
            return leaseLost();
        }
        metrics.counter("dead_letter");
        return PublishOutcome.DEAD_LETTER;
    }

    private PublishOutcome scheduleRetry(AttemptGroupEventOutbox event, String category) {
        return scheduleRetry(event, category, null);
    }

    private PublishOutcome scheduleRetry(
            AttemptGroupEventOutbox event,
            String category,
            Integer retryAfterSeconds
    ) {
        Instant now = clock.instant();
        Duration delay = retryDelay(event.getAttemptCount(), retryAfterSeconds);
        boolean updated = outboxStore.scheduleRetry(
                event.getEventId(), event.getLeaseToken(), now.plus(delay), category, now);
        return updated ? PublishOutcome.RETRY_SCHEDULED : leaseLost();
    }

    private Duration retryDelay(int attempt, Integer retryAfterSeconds) {
        long[] seconds = {5, 15, 60, 300, 900};
        long base = seconds[Math.min(Math.max(attempt - 1, 0), seconds.length - 1)];
        if (retryAfterSeconds != null && retryAfterSeconds >= 0) {
            base = Math.min(900, Math.max(base, retryAfterSeconds));
        }
        long jitter = base < 5 ? 0 : ThreadLocalRandom.current().nextLong(0, Math.max(1, base / 5));
        return Duration.ofSeconds(Math.min(900, base + jitter));
    }

    private PublishOutcome leaseLost() {
        metrics.counter("lease_lost");
        return PublishOutcome.LEASE_LOST;
    }

    private void logOutcome(PublishOutcome outcome, String traceId, String eventId, long durationMs) {
        log.info("service={} operation={} outcome={} traceId={} eventId={} durationMs={}",
                SERVICE, OPERATION, outcome.logValue, traceId, eventId, Math.max(0L, durationMs));
    }

    private static String safeFlags(String flags) {
        return flags != null && flags.matches("[0-9a-f]{2}") ? flags : "01";
    }

    private static String safeTraceId(String traceId) {
        return traceId != null && traceId.matches("[0-9a-f]{32}")
                ? traceId : "00000000000000000000000000000000";
    }

    private static String randomSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                .toLowerCase(Locale.ROOT);
    }

    private enum PublishOutcome {
        DELIVERED("delivered"),
        RETRY_SCHEDULED("retry_scheduled"),
        DEAD_LETTER("dead_letter"),
        AUTH_FAILURE("auth_failure"),
        LEASE_LOST("lease_lost");

        private final String logValue;

        PublishOutcome(String logValue) {
            this.logValue = logValue;
        }
    }
}
