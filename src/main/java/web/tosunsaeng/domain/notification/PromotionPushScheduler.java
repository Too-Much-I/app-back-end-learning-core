package web.tosunsaeng.domain.notification;

import java.util.concurrent.*;
import org.springframework.context.SmartLifecycle;

/** Isolated from exam, billing and daily reminder scheduling; one sweep per instance. */
public final class PromotionPushScheduler implements SmartLifecycle {
    private final PromotionPushWorker worker;
    private ScheduledExecutorService executor;
    private volatile boolean running;
    public PromotionPushScheduler(PromotionPushWorker worker){this.worker=worker;}
    @Override public synchronized void start(){
        if(running)return;
        executor=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("promotion-push").factory());
        executor.scheduleWithFixedDelay(worker::tick,0,5,TimeUnit.SECONDS);running=true;
    }
    @Override public synchronized void stop(){
        if(!running)return;running=false;executor.shutdownNow();
        try{executor.awaitTermination(15,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    @Override public boolean isRunning(){return running;}
    @Override public int getPhase(){return Integer.MAX_VALUE;}
}
