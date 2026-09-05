package web.tosunsaeng.domain.usermerge.application;

import com.mongodb.MongoException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import web.tosunsaeng.domain.usermerge.config.UserMergedProperties;

import java.time.Clock;
import java.util.function.Supplier;

public class UserOwnedTransactionExecutor {

    private static final int MAX_ATTEMPTS = 3;

    private final UserMergedProperties properties;
    private final UserOwnershipGuardService guardService;
    private final ObjectProvider<TransactionOperations> transactionProvider;
    private final Clock clock;

    public UserOwnedTransactionExecutor(
            UserMergedProperties properties,
            UserOwnershipGuardService guardService,
            ObjectProvider<TransactionOperations> transactionProvider,
            Clock clock
    ) {
        this.properties = properties;
        this.guardService = guardService;
        this.transactionProvider = transactionProvider;
        this.clock = clock;
    }

    public boolean enabled() {
        return properties.isWriterEnabled();
    }

    public <T> T execute(String userId, Supplier<T> command) {
        if (!enabled()) {
            return command.get();
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            guardService.touchActive(userId, clock.instant());
            return command.get();
        }
        return executeWithRetry(() -> {
            guardService.touchActive(userId, clock.instant());
            return command.get();
        });
    }

    public void executeWithoutResult(String userId, Runnable command) {
        execute(userId, () -> {
            command.run();
            return Boolean.TRUE;
        });
    }

    public <T> T executeWithoutGuard(Supplier<T> command) {
        if (!enabled()) {
            throw new IllegalStateException("User-owned writer guard is disabled");
        }
        return executeWithRetry(command);
    }

    public void touchWithinExistingTransaction(String userId) {
        if (enabled()) {
            guardService.touchActive(userId, clock.instant());
        }
    }

    private <T> T executeWithRetry(Supplier<T> command) {
        TransactionOperations operations = transactionProvider.getIfAvailable();
        if (operations == null) {
            throw new IllegalStateException("User-owned Mongo transaction manager is unavailable");
        }
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return operations.execute(status -> command.get());
            } catch (UserOwnershipGuardException guardFailure) {
                throw guardFailure;
            } catch (RuntimeException failure) {
                lastFailure = failure;
                TransactionFailureKind failureKind = classify(failure);
                if (failureKind == TransactionFailureKind.UNKNOWN_COMMIT) {
                    throw new UserOwnedCommitOutcomeUnknownException(failure);
                }
                if (attempt == MAX_ATTEMPTS
                        || failureKind != TransactionFailureKind.TRANSIENT_RETRYABLE) {
                    throw failure;
                }
            }
        }
        throw lastFailure == null
                ? new IllegalStateException("User-owned Mongo transaction failed")
                : lastFailure;
    }

    private static TransactionFailureKind classify(RuntimeException failure) {
        boolean retryable = false;
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof DuplicateKeyException
                    || current instanceof OptimisticLockingFailureException) {
                retryable = true;
            }
            if (current instanceof MongoException mongoException) {
                if (mongoException.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) {
                    return TransactionFailureKind.UNKNOWN_COMMIT;
                }
                if (mongoException.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                    retryable = true;
                }
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return retryable
                ? TransactionFailureKind.TRANSIENT_RETRYABLE
                : TransactionFailureKind.NON_RETRYABLE;
    }

    private enum TransactionFailureKind {
        TRANSIENT_RETRYABLE,
        UNKNOWN_COMMIT,
        NON_RETRYABLE
    }
}
