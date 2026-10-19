package web.tosunsaeng.domain.learningrecorddeletion.application;

import com.mongodb.*;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.function.Supplier;

/** Unknown commit is returned to the command boundary; never retry its writes blindly. */
public final class DeletionTransactions {
    private final TransactionTemplate transactions;
    public DeletionTransactions(MongoDatabaseFactory factory) {
        transactions = new TransactionTemplate(new MongoTransactionManager(factory, TransactionOptions.builder()
                .readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY).readPreference(ReadPreference.primary()).build()));
        transactions.setTimeout(10);
    }
    public <T> T run(Supplier<T> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Deletion owns its transaction boundary");
        for (int attempt = 0; ; attempt++) {
            try { return transactions.execute(status -> action.get()); }
            catch (RuntimeException failure) {
                if (unknown(failure) || attempt == 2 || !retryable(failure)) throw failure;
            }
        }
    }
    public static boolean unknown(Throwable failure) {
        for (int depth = 0; failure != null && depth < 16; depth++, failure = failure.getCause())
            if (failure instanceof MongoException m && m.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) return true;
        return false;
    }
    private static boolean retryable(Throwable failure) {
        for (int depth = 0; failure != null && depth < 16; depth++, failure = failure.getCause()) {
            if (failure instanceof org.springframework.dao.DuplicateKeyException
                    || failure instanceof org.springframework.dao.OptimisticLockingFailureException) return true;
            if (failure instanceof MongoException m && m.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) return true;
        }
        return false;
    }
}
