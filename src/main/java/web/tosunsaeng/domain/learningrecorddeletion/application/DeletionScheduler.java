package web.tosunsaeng.domain.learningrecorddeletion.application;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.SmartLifecycle;
import java.util.concurrent.*;

/** Two workers, bounded ten passes per worker per poll; failures never cancel future scheduled polls. */
public final class DeletionScheduler implements SmartLifecycle {
    private final DeletionWorker worker;
    private final MeterRegistry metrics;
    private ScheduledExecutorService executor;
    private volatile boolean running;
    public DeletionScheduler(DeletionWorker worker, MeterRegistry metrics) { this.worker = worker; this.metrics = metrics; }
    @Override public synchronized void start() {
        if (running) return;
        executor = Executors.newScheduledThreadPool(2, Thread.ofPlatform().name("learning-deletion-", 0).factory());
        running = true;
        for (int i = 0; i < 2; i++) executor.scheduleWithFixedDelay(this::poll, 0, 10, TimeUnit.SECONDS);
    }
    private void poll() {
        try {
            for (int i = 0; i < 10 && running; i++) if (!worker.tick()) break;
        } catch (RuntimeException failure) {
            metrics.counter("learning_core.deletion.worker", "outcome", "temporary_failure").increment();
        } finally {
            // Retention must progress even when the active queue never becomes empty.
            try { worker.maintainRetention(); }
            catch (RuntimeException failure) { metrics.counter("learning_core.deletion.worker", "outcome", "retention_delayed").increment(); }
        }
    }
    @Override public synchronized void stop() {
        if (!running) return;
        running = false; executor.shutdown();
        try { if (!executor.awaitTermination(35, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException interrupted) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }
    @Override public boolean isRunning() { return running; }
    @Override public int getPhase() { return Integer.MAX_VALUE; }
}
