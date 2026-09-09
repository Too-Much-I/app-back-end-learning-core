package web.tosunsaeng.domain.exams.billing.reconciliation;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

/** No unbounded queue; claim happens inside an available worker, never while queued. */
@Component
public class BillingReservationReconciliationScheduler {
    private final ReservationReconciliationProperties properties;
    private final ReservationRecoveryStore store;
    private final ReservationAuthCircuit circuit;
    private final BillingReservationReconciliationService service;
    private final ReservationReconciliationAlertReporter alerts;
    private final MeterRegistry metrics;
    private ScheduledExecutorService poller;
    private ThreadPoolExecutor workers;
    private volatile boolean stopping;
    private final java.util.concurrent.atomic.AtomicLong backlog = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong oldestClaimableSample = new java.util.concurrent.atomic.AtomicLong();

    public BillingReservationReconciliationScheduler(ReservationReconciliationProperties properties,
            ReservationRecoveryStore store, ReservationAuthCircuit circuit,
            BillingReservationReconciliationService service, ReservationReconciliationAlertReporter alerts,
            MeterRegistry metrics) {
        this.properties = properties; this.store = store; this.circuit = circuit;
        this.service = service; this.alerts = alerts; this.metrics = metrics;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (!properties.isEnabled() || poller != null) return;
        metrics.gauge("learning_core.billing.reconciliation.due", backlog);
        metrics.gauge("learning_core.billing.reconciliation.sample_oldest_due_seconds", oldestClaimableSample);
        workers = new ThreadPoolExecutor(properties.getConcurrency(), properties.getConcurrency(), 0,
                TimeUnit.SECONDS, new SynchronousQueue<>(), Thread.ofPlatform().name("reservation-recovery-", 0).factory());
        poller = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("reservation-recovery-poll").factory());
        poller.scheduleWithFixedDelay(this::poll, 0, properties.getPoll().toMillis(), TimeUnit.MILLISECONDS);
    }

    void poll() {
        if (stopping || !properties.isEnabled()) return;
        try {
            // Poll thread performs only bounded control/alert work. These never reserve/confirm/cancel.
            if (workers == null || workers.getActiveCount() == 0) service.probeAuth();
            try { alerts.poll(); } catch (RuntimeException alertFailure) { failureCounter(); }
            if (circuit.blocked()) return;
            store.wakeAuthBlocked();
            var candidates = store.due();
            backlog.set(store.dueCount());
            oldestClaimableSample.set(candidates.stream().mapToLong(store::dueAgeSeconds).max().orElse(0));
            for (var operation : candidates) {
                if (stopping) break;
                try {
                    workers.execute(() -> {
                        try { service.run(operation.getCommandId()); }
                        catch (RuntimeException failure) { failureCounter(); }
                    });
                } catch (RejectedExecutionException full) { break; }
            }
        } catch (RuntimeException failure) {
            failureCounter(); // Never send raw provider exceptions to logs/Sentry.
        }
    }

    private void failureCounter() {
        metrics.counter("learning_core.billing.reconciliation.scheduler_failure",
                "service", "learning-core", "operation", "billing_reservation_reconcile").increment();
    }

    @PreDestroy
    public synchronized void stop() {
        stopping = true;
        if (poller != null) poller.shutdown();
        if (workers != null) workers.shutdown();
        try {
            if (poller != null && !poller.awaitTermination(properties.getAttemptTimeout().toMillis(), TimeUnit.MILLISECONDS)) poller.shutdownNow();
            if (workers != null && !workers.awaitTermination(properties.getLease().toMillis(), TimeUnit.MILLISECONDS)) workers.shutdownNow();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (workers != null) workers.shutdownNow();
            if (poller != null) poller.shutdownNow();
        }
    }
}
