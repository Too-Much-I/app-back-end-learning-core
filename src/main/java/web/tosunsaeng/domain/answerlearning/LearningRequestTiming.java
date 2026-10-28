package web.tosunsaeng.domain.answerlearning;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Synchronous servlet request only. Never retains commands, results, IDs or credentials. */
public final class LearningRequestTiming {
    private static final ThreadLocal<LearningRequestTiming> CURRENT = new ThreadLocal<>();
    private long mongoNanos;
    private int mongoCommands;
    private long billingNanos;
    private int billingCalls;

    static LearningRequestTiming begin() {
        var timing = new LearningRequestTiming();
        CURRENT.set(timing);
        return timing;
    }

    static void clear() { CURRENT.remove(); }

    static void mongo(long nanos) {
        var timing = CURRENT.get();
        if (timing != null) {
            timing.mongoNanos += Math.max(0, nanos);
            timing.mongoCommands++;
        }
    }

    public static <T> T billing(Supplier<T> action) {
        var timing = CURRENT.get();
        if (timing == null) return action.get();
        long started = System.nanoTime();
        try { return action.get(); }
        finally {
            timing.billingNanos += Math.max(0, System.nanoTime() - started);
            timing.billingCalls++;
        }
    }

    long mongoCommandMs() { return TimeUnit.NANOSECONDS.toMillis(mongoNanos); }
    int mongoCommands() { return mongoCommands; }
    long billingMs() { return TimeUnit.NANOSECONDS.toMillis(billingNanos); }
    int billingCalls() { return billingCalls; }
}
