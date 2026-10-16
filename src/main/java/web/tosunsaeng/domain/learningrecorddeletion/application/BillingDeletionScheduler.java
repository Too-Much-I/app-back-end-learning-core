package web.tosunsaeng.domain.learningrecorddeletion.application;

import org.springframework.context.SmartLifecycle;
import java.util.concurrent.*;

/** Single fixed-delay tick, at most twenty sequential delivery attempts; no raw exception logging. */
public final class BillingDeletionScheduler implements SmartLifecycle {
    private final BillingDeletionSender sender;
    private ScheduledExecutorService executor;
    private volatile boolean running;
    public BillingDeletionScheduler(BillingDeletionSender sender) { this.sender = sender; }
    public synchronized void start() {
        if (running) return;
        running = true;
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("billing-deletion-").factory());
        executor.scheduleWithFixedDelay(() -> {
            for (int i = 0; i < 20 && running; i++) {
                try { if (!sender.tick()) break; }
                catch (RuntimeException unavailable) { break; }
            }
        }, 0, 5, TimeUnit.SECONDS);
    }
    public synchronized void stop() {
        running = false;
        if (executor != null) executor.shutdownNow();
    }
    public boolean isRunning() { return running; }
    public int getPhase() { return Integer.MAX_VALUE; }
}
