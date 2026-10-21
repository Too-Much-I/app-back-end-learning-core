package web.tosunsaeng.domain.usermerge.application;

import com.mongodb.MongoException;
import web.tosunsaeng.global.exception.MongoFailureDiagnostics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import web.tosunsaeng.domain.usermerge.config.UserMergedProperties;

import java.time.Clock;
import java.util.function.Supplier;
import java.util.function.LongConsumer;

public class UserOwnedTransactionExecutor {

    private static final int MAX_ATTEMPTS = 3;

    private final UserMergedProperties properties;
    private final UserOwnershipGuardService guardService;
    private final ObjectProvider<TransactionOperations> transactionProvider;
    private final Clock clock;
    private final LongConsumer retryPause;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private web.tosunsaeng.domain.learningrecorddeletion.application.DeletionAccess deletionAccess;

    public UserOwnedTransactionExecutor(
            UserMergedProperties properties,
            UserOwnershipGuardService guardService,
            ObjectProvider<TransactionOperations> transactionProvider,
            Clock clock
    ) {
        this(properties, guardService, transactionProvider, clock, UserOwnedTransactionExecutor::pause);
    }

    UserOwnedTransactionExecutor(UserMergedProperties properties, UserOwnershipGuardService guardService,
            ObjectProvider<TransactionOperations> transactionProvider, Clock clock, LongConsumer retryPause) {
        this.properties = properties;
        this.guardService = guardService;
        this.transactionProvider = transactionProvider;
        this.clock = clock;
        this.retryPause = retryPause;
    }

    public boolean enabled() {
        return properties.isWriterEnabled() || deletionAccess != null;
    }

    public <T> T execute(String userId, Supplier<T> command) {
        if (!enabled()) {
            return command.get();
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            guardService.touchActive(userId, clock.instant());
            if (deletionAccess != null) deletionAccess.requireWriter(userId);
            return command.get();
        }
        return executeWithRetry(() -> {
            guardService.touchActive(userId, clock.instant());
            if (deletionAccess != null) deletionAccess.requireWriter(userId);
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
            if (deletionAccess != null) deletionAccess.requireWriter(userId);
        }
    }

    /** Existing reservation status/confirm/cancel only; never Session creation or reserve dispatch. */
    public void touchReservationCoordinationWithinExistingTransaction(String userId) {
        if (enabled()) guardService.touchActive(userId, clock.instant());
    }

    public void requirePublicWrite(String userId) {
        if (deletionAccess != null) deletionAccess.requirePublicWrite(userId);
    }

    /** Capability issuance only: never use this read-only check in place of a writer fence. */
    public void requireUploadUrlAccess(String userId) {
        if (!enabled()) return;
        guardService.find(userId).ifPresent(guard -> {
            if (guard.isMerged())
                throw new UserOwnershipGuardException(UserOwnershipGuardException.Reason.ALREADY_MERGED);
            if (guard.getState() != web.tosunsaeng.domain.usermerge.domain.OwnershipGuardState.ACTIVE)
                throw new UserOwnershipGuardException(UserOwnershipGuardException.Reason.STATE_CONFLICT);
        });
        if (deletionAccess != null) deletionAccess.requireWriter(userId);
    }

    /** Read-only recheck after an aborted post-submission transaction; never replaces a writer fence. */
    public void requireDeferredExamCoordinationAllowed(String userId, String examId) {
        if (!enabled()) throw new IllegalStateException("User-owned writer guard is disabled");
        var guard = guardService.find(userId).orElseThrow(() ->
                new UserOwnershipGuardException(UserOwnershipGuardException.Reason.STATE_CONFLICT));
        if (guard.isMerged()) throw new UserOwnershipGuardException(UserOwnershipGuardException.Reason.ALREADY_MERGED);
        if (guard.getState() != web.tosunsaeng.domain.usermerge.domain.OwnershipGuardState.ACTIVE)
            throw new UserOwnershipGuardException(UserOwnershipGuardException.Reason.STATE_CONFLICT);
        if (deletionAccess != null) deletionAccess.examCoordination(examId, () -> {
            deletionAccess.requireWriter(userId);
            return null;
        });
    }

    public <T> T executeExamCoordination(String userId, String examId, Supplier<T> command) {
        return deletionAccess == null ? execute(userId, command)
                : deletionAccess.examCoordination(examId, () -> execute(userId, command));
    }

    public void touchExamWithinExistingTransaction(String userId, String examId) {
        if (deletionAccess == null) touchWithinExistingTransaction(userId);
        else deletionAccess.examCoordination(examId, () -> { touchWithinExistingTransaction(userId); return null; });
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
                MongoFailureDiagnostics.record(failure, MongoFailureDiagnostics.Boundary.USER_OWNED, attempt,
                        failureKind == TransactionFailureKind.UNKNOWN_COMMIT
                                ? MongoFailureDiagnostics.Outcome.UNKNOWN_COMMIT
                                : failureKind != TransactionFailureKind.TRANSIENT_RETRYABLE
                                        ? MongoFailureDiagnostics.Outcome.NON_RETRYABLE
                                        : attempt == MAX_ATTEMPTS ? MongoFailureDiagnostics.Outcome.EXHAUSTED
                                        : MongoFailureDiagnostics.Outcome.RETRYING);
                if (failureKind == TransactionFailureKind.UNKNOWN_COMMIT) {
                    throw new UserOwnedCommitOutcomeUnknownException(failure);
                }
                if (attempt == MAX_ATTEMPTS
                        || failureKind != TransactionFailureKind.TRANSIENT_RETRYABLE) {
                    throw failure;
                }
                // The failed transaction has exited. Let the competing owner transaction commit
                // before opening a new one; retain the existing three-attempt bound.
                retryPause.accept(25L << (attempt - 1));
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

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("User-owned transaction retry interrupted", interrupted);
        }
    }

    private enum TransactionFailureKind {
        TRANSIENT_RETRYABLE,
        UNKNOWN_COMMIT,
        NON_RETRYABLE
    }
}
