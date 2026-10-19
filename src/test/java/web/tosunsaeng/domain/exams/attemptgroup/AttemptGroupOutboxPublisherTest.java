package web.tosunsaeng.domain.exams.attemptgroup;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import web.tosunsaeng.domain.exams.attemptgroup.application.AttemptGroupEventMetrics;
import web.tosunsaeng.domain.exams.attemptgroup.application.AttemptGroupOutboxPublisher;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupEventOutbox;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupEventTarget;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventClient;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupOutboxStore;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupPublisherStateStore;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupTraceContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptGroupOutboxPublisherTest {
    private static final Instant NOW = Instant.parse("2026-09-01T03:00:00Z");
    @Mock AttemptGroupOutboxStore outboxStore;
    @Mock AttemptGroupPublisherStateStore stateStore;
    @Mock AttemptGroupEventClient client;
    @Mock AttemptGroupTraceContext traceContext;
    @Mock AttemptGroupEventMetrics metrics;
    @Mock ObjectProvider<Tracer> tracerProvider;
    @Mock AttemptGroupEventOutbox event;
    private AttemptGroupOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AttemptGroupOutboxPublisher(
                AttemptGroupEvidenceEvaluatorTest.properties(), outboxStore, stateStore,
                client, traceContext, metrics, tracerProvider, Clock.fixed(NOW, ZoneOffset.UTC));
        when(stateStore.acquireGate(any(), any(), any()))
                .thenReturn(AttemptGroupPublisherStateStore.Gate.OPEN);
        when(outboxStore.claimNext(anyString(), any(), any()))
                .thenReturn(Optional.of(event), Optional.empty());
        when(event.getEventId()).thenReturn("018f6f36-2f42-4bf5-8c17-0be35de4872f");
        when(event.getLeaseToken()).thenReturn("lease-token");
        when(event.getCanonicalPayload()).thenReturn("{}");
        when(event.getTraceId()).thenReturn("0123456789abcdef0123456789abcdef");
        when(event.getParentSpanId()).thenReturn("0123456789abcdef");
        when(event.getTraceFlags()).thenReturn("01");
        when(event.getOccurredAt()).thenReturn(NOW.minusSeconds(10));
        when(event.getTargetStatus()).thenReturn(AttemptGroupEventTarget.GRADING);
        when(traceContext.valid("0123456789abcdef0123456789abcdef", "0123456789abcdef"))
                .thenReturn(true);
        when(tracerProvider.getIfAvailable()).thenReturn(null);
    }

    @Test
    void successMarksDeliveredAndOpensCircuit() {
        when(client.send(anyString(), anyString()))
                .thenReturn(new AttemptGroupEventClient.Response(204, null));
        when(outboxStore.markDelivered(anyString(), anyString(), any(), any())).thenReturn(true);

        publisher.publishAvailable();

        verify(outboxStore).markDelivered(anyString(), anyString(), any(), any());
        verify(stateStore).open(NOW);
    }

    @Test
    void authFailureBlocksEventAndGlobalCircuit() {
        when(client.send(anyString(), anyString()))
                .thenReturn(new AttemptGroupEventClient.Response(403, null));
        when(outboxStore.markBlockedAuth(anyString(), anyString(), anyString(), any()))
                .thenReturn(true);

        publisher.publishAvailable();

        verify(outboxStore).markBlockedAuth(anyString(), anyString(), anyString(), any());
        verify(stateStore).block(NOW, AttemptGroupEvidenceEvaluatorTest.properties().authProbeInterval());
        verify(outboxStore, never()).markDeadLetter(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void temporaryHttpFailureKeepsSameEventForRetry() {
        when(event.getAttemptCount()).thenReturn(1);
        when(client.send(anyString(), anyString()))
                .thenReturn(new AttemptGroupEventClient.Response(503, null));
        when(outboxStore.scheduleRetry(anyString(), anyString(), any(), anyString(), any()))
                .thenReturn(true);

        publisher.publishAvailable();

        verify(outboxStore).scheduleRetry(
                org.mockito.ArgumentMatchers.eq("018f6f36-2f42-4bf5-8c17-0be35de4872f"),
                org.mockito.ArgumentMatchers.eq("lease-token"), any(),
                org.mockito.ArgumentMatchers.eq("http_503"), any());
        verify(outboxStore, never()).markDeadLetter(anyString(), anyString(), anyString(), any(), any());
    }
}
