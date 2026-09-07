package web.tosunsaeng.domain.challenge;

import org.springframework.context.SmartLifecycle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Private executor: does not replace Boot's TaskScheduler used by existing exam jobs. */
public class ChallengeScheduler implements SmartLifecycle {
    private final ChallengeWorker worker;
    private final ChallengeProperties properties;
    private volatile boolean running;
    private ScheduledExecutorService executor;
    public ChallengeScheduler(ChallengeWorker worker, ChallengeProperties properties) { this.worker = worker; this.properties = properties; }
    @Override public synchronized void start() {
        if (running) return;
        executor = Executors.newScheduledThreadPool(2, Thread.ofPlatform().name("challenge-", 0).factory());
        executor.scheduleWithFixedDelay(worker::tick, 0, properties.getPollMs(), TimeUnit.MILLISECONDS);
        executor.scheduleWithFixedDelay(worker::expire, 0, properties.getExpiryPollMs(), TimeUnit.MILLISECONDS);
        running = true;
    }
    @Override public synchronized void stop() {
        if (!running) return;
        running = false; executor.shutdown();
        try { if (!executor.awaitTermination(35, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }
    @Override public boolean isRunning() { return running; }
    @Override public int getPhase() { return Integer.MAX_VALUE; }
}
