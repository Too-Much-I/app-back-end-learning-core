package web.tosunsaeng.domain.exams.attemptgroup.application;

import com.mongodb.MongoException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AttemptGroupTransactionRetryTest {
    private MongoException transientFailure() {
        MongoException failure = new MongoException(112, "private command must not be logged");
        failure.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        return failure;
    }

    @Test void wrappedTransientRetriesOnlyLocalUnitWithBoundedDelays() {
        AtomicInteger calls = new AtomicInteger();
        var pauses = new ArrayList<Long>();
        String result = AttemptGroupTransactionRetry.execute(() -> {
            if (calls.incrementAndGet() < 3) throw new DataIntegrityViolationException("wrapped", transientFailure());
            return "done";
        }, pauses::add);
        assertEquals("done", result);
        assertEquals(java.util.List.of(25L, 50L), pauses);
        assertEquals(3, calls.get());
    }

    @Test void exhaustionDoesNotPretendSuccess() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(MongoException.class, () -> AttemptGroupTransactionRetry.execute(() -> {
            calls.incrementAndGet(); throw transientFailure();
        }, ignored -> {}));
        assertEquals(3, calls.get());
    }

    @Test void unknownCommitOverridesOuterDuplicateAndTransientLabel() {
        MongoException unknown = transientFailure();
        unknown.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        var failure = new DuplicateKeyException("outer", unknown);
        AtomicInteger calls = new AtomicInteger();
        assertSame(failure, assertThrows(DuplicateKeyException.class, () -> AttemptGroupTransactionRetry.execute(() -> {
            calls.incrementAndGet(); throw failure;
        }, ignored -> fail("must not pause"))));
        assertEquals(1, calls.get());
    }

    @Test void unlabelledMongoAndPermanentFailuresAreNotRetried() {
        assertFalse(AttemptGroupTransactionRetry.classify(new MongoException(112, "no label")).retryable());
        assertFalse(AttemptGroupTransactionRetry.classify(new IllegalStateException("denied")).retryable());
    }

    @Test void participatingTransactionIsNotReusedAfterFailure() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        AtomicInteger calls = new AtomicInteger();
        try {
            assertThrows(MongoException.class, () -> AttemptGroupTransactionRetry.execute(() -> {
                calls.incrementAndGet(); throw transientFailure();
            }, ignored -> fail("must not pause")));
            assertEquals(1, calls.get());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
