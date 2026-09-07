package web.tosunsaeng.domain.challenge;

import com.mongodb.MongoException;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import java.util.function.Supplier;

/** Always transactional, including when the optional ownership writer flag is OFF. */
public class ChallengeTransactions {
    private final TransactionTemplate transactions;
    private final UserOwnedTransactionExecutor guards;
    public ChallengeTransactions(MongoDatabaseFactory factory, UserOwnedTransactionExecutor guards) {
        transactions = new TransactionTemplate(new MongoTransactionManager(factory, TransactionOptions.builder()
                .readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY)
                .readPreference(ReadPreference.primary()).build()));
        transactions.setTimeout(10);
        this.guards = guards;
    }
    public <T> T run(String owner, Supplier<T> command) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Challenge commands must own their transaction boundary");
        }
        for (int i = 0; ; i++) {
            try {
                return transactions.execute(status -> {
                    if (owner != null) guards.touchWithinExistingTransaction(owner);
                    return command.get();
                });
            } catch (RuntimeException failure) {
                // Unknown commit MUST NOT rerun writes. Callers recover from durable receipts outside this transaction.
                if (unknown(failure) || i == 2 || !retryable(failure)) throw failure;
            }
        }
    }
    static boolean unknown(Throwable t) {
        for (int i = 0; t != null && i < 20; i++, t = t.getCause())
            if (t instanceof MongoException m && m.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) return true;
        return false;
    }
    private static boolean retryable(Throwable t) {
        for (int i = 0; t != null && i < 20; i++, t = t.getCause()) {
            if (t instanceof DuplicateKeyException || t instanceof OptimisticLockingFailureException) return true;
            if (t instanceof MongoException m && m.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) return true;
        }
        return false;
    }
}
