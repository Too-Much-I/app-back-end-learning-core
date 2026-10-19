package web.tosunsaeng.domain.exams.attemptgroup.application;

import com.mongodb.MongoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicLong;

/** Retries an aborted local transaction, never an external dispatch or an unknown commit. */
@Slf4j
final class AttemptGroupTransactionRetry {
    private static final AtomicLong LAST_WARNING = new AtomicLong();
    private static final AtomicLong UNREPORTED_FAILURES = new AtomicLong();
    private AttemptGroupTransactionRetry() {}

    static <T> T execute(Supplier<T> transaction) {
        return execute(transaction, AttemptGroupTransactionRetry::pause);
    }

    static <T> T execute(Supplier<T> transaction, LongConsumer pause) {
        // A participating transaction belongs to its caller; it cannot be restarted here.
        boolean participating = TransactionSynchronizationManager.isActualTransactionActive();
        for (int attempt = 1; ; attempt++) {
            try {
                T result = transaction.get();
                if (attempt > 1) log.debug("AttemptGroup transaction event=attempt_group.transaction outcome=recovered attempt={}", attempt);
                return result;
            } catch (RuntimeException failure) {
                Failure detail = classify(failure);
                boolean retry = !participating && detail.retryable() && !detail.unknownCommit() && attempt < 3;
                // Do not log exception messages, BSON commands, URIs, or arbitrary labels.
                if (retry) {
                    log.debug("AttemptGroup transaction event=attempt_group.transaction outcome=retry attempt={} mongoCode={}", attempt, detail.mongoCode());
                } else {
                    reportFailure(detail, attempt);
                }
                if (!retry) throw failure;
                pause.accept(25L << (attempt - 1));
            }
        }
    }

    static Failure classify(Throwable failure) {
        boolean retryable = false;
        boolean transientError = false;
        boolean unknown = false;
        Integer code = null;
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 32; depth++) {
            retryable |= current instanceof DuplicateKeyException || current instanceof OptimisticLockingFailureException;
            if (current instanceof MongoException mongo) {
                code = mongo.getCode();
                transientError |= mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
                unknown |= mongo.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
            }
            if (current == current.getCause()) break;
            current = current.getCause();
        }
        // An unexamined cause chain must not conceal an unknown commit.
        boolean truncated = current != null && current.getCause() != null && current != current.getCause();
        return new Failure(!truncated && (retryable || transientError) && !unknown, transientError, unknown, code);
    }

    record Failure(boolean retryable, boolean transientError, boolean unknownCommit, Integer mongoCode) {}

    static void reportFailure(Failure detail, int attempt) {
        UNREPORTED_FAILURES.incrementAndGet();
        long now = System.nanoTime();
        long last = LAST_WARNING.get();
        if ((last == 0 || now - last >= java.time.Duration.ofMinutes(1).toNanos())
                && LAST_WARNING.compareAndSet(last, now)) {
            log.warn("AttemptGroup transaction event=attempt_group.transaction outcome=failed attempt={} mongoCode={} transient={} unknownCommit={} failureObservationsSinceLastLog={}",
                    attempt, detail.mongoCode(), detail.transientError(), detail.unknownCommit(), UNREPORTED_FAILURES.getAndSet(0));
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AttemptGroup transaction retry interrupted");
        }
    }
}
