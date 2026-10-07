package web.tosunsaeng.domain.notification;

import com.mongodb.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.function.Supplier;

public class NotificationTransactions {
    private final TransactionTemplate tx;
    public NotificationTransactions(MongoDatabaseFactory factory) {
        tx = new TransactionTemplate(new MongoTransactionManager(factory, TransactionOptions.builder()
                .readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY)
                .readPreference(ReadPreference.primary()).build()));
        tx.setTimeout(10);
    }
    public <T> T run(Supplier<T> command) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) return command.get();
        for (int attempt = 0; ; attempt++) {
            try { return tx.execute(status -> command.get()); }
            catch (RuntimeException e) {
                if (unknown(e) || attempt == 2 || !retryable(e)) throw e;
                // Avoid immediately colliding with the same committing transaction again.
                java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(20L * (attempt + 1)));
            }
        }
    }
    static boolean unknown(Throwable e) {
        for (int i = 0; e != null && i < 20; i++, e = e.getCause())
            if (e instanceof MongoException m && m.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) return true;
        return false;
    }
    private static boolean retryable(Throwable e) {
        for (int i = 0; e != null && i < 20; i++, e = e.getCause()) {
            if (e instanceof DuplicateKeyException || e instanceof OptimisticLockingFailureException) return true;
            if (e instanceof MongoException m && m.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) return true;
        }
        return false;
    }
}
