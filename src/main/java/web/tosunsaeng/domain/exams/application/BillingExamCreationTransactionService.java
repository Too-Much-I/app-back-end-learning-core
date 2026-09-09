package web.tosunsaeng.domain.exams.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.enums.ExamCreationState;
import web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupProjectionStatus;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventProperties;
import web.tosunsaeng.domain.exams.domain.repository.ExamCreationOperationRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.function.Supplier;

@Service
public class BillingExamCreationTransactionService {

    private static final Duration TERMINAL_RETENTION = Duration.ofDays(7);

    private final ExamCreationOperationRepository operationRepository;
    private final ExamSessionRepository sessionRepository;
    private final ExamSessionManager sessionManager;
    private final ObjectProvider<TransactionOperations> transactionOperationsProvider;
    private final AttemptGroupEventProperties attemptGroupProperties;

    @Autowired(required = false)
    private UserOwnedTransactionExecutor userOwnedTransactionExecutor;

    @Autowired(required = false)
    private web.tosunsaeng.domain.exams.billing.reconciliation.ReservationOperationExecution execution;

    @Autowired
    public BillingExamCreationTransactionService(
            ExamCreationOperationRepository operationRepository,
            ExamSessionRepository sessionRepository,
            ExamSessionManager sessionManager,
            @Qualifier("billingTransactionOperations")
            ObjectProvider<TransactionOperations> transactionOperationsProvider,
            AttemptGroupEventProperties attemptGroupProperties
    ) {
        this.operationRepository = operationRepository;
        this.sessionRepository = sessionRepository;
        this.sessionManager = sessionManager;
        this.transactionOperationsProvider = transactionOperationsProvider;
        this.attemptGroupProperties = attemptGroupProperties;
    }

    BillingExamCreationTransactionService(
            ExamCreationOperationRepository operationRepository,
            ExamSessionRepository sessionRepository,
            ExamSessionManager sessionManager,
            ObjectProvider<TransactionOperations> transactionOperationsProvider
    ) {
        this(operationRepository, sessionRepository, sessionManager, transactionOperationsProvider,
                new AttemptGroupEventProperties(
                        false, false, "", "ap-northeast-2", Duration.ofMinutes(30),
                        Duration.ofSeconds(1), 20, Duration.ofSeconds(30), Duration.ofMinutes(15),
                        Duration.ofSeconds(2), Duration.ofSeconds(5),
                        Duration.ofDays(30), Duration.ofDays(90)));
    }

    public ExamCreationOperation commitReservedSession(String commandId, Instant committedAt, ZoneId zone) {
        return inTransaction(() -> {
            ExamCreationOperation operation = required(commandId);
            touchOwner(operation.getUserId());
            if (operation.getState() == ExamCreationState.SESSION_COMMITTED
                    || operation.getState() == ExamCreationState.SUCCEEDED) {
                return operation;
            }
            if (execution != null && operation.getRecovery().getIntent()
                    != web.tosunsaeng.domain.exams.billing.reconciliation.ReservationRecovery.Intent.CONTINUE) {
                throw new IllegalStateException("Reservation cleanup already committed");
            }
            if (operation.getState() != ExamCreationState.RESERVED) {
                throw new IllegalStateException("Exam creation operation is not RESERVED");
            }

            for (ExamSession candidate : sessionManager.findInProgressSessions(operation.getUserId())) {
                if (!Objects.equals(candidate.getExamId(), operation.getSessionId())) {
                    sessionRepository.abandonIfInProgress(candidate.getExamId());
                }
            }

            ExamSession session = ExamSession.builder()
                    .examId(operation.getSessionId())
                    .userId(operation.getUserId())
                    .createdAt(LocalDateTime.ofInstant(operation.getCreatedAt(), zone))
                    .mockExamId(operation.getMockExamId())
                    .cycleNumber(operation.getCycleNumber())
                    .active(true)
                    .status(ExamSessionStatus.ENTITLEMENT_CONFIRMING)
                    .completedAt(null)
                    .creationOperationId(operation.getOperationId())
                    .billingReservationId(operation.getReservationId())
                    .billingReservationKind(operation.getReservationKind())
                    .attemptGroupId(operation.getAttemptGroupId())
                    .entitlementState(ExamEntitlementState.CONFIRMING)
                    .entitlementConfirmedAt(null)
                    .attemptGroupProjectionStatus(attemptGroupProperties.writerEnabled()
                            ? AttemptGroupProjectionStatus.OPEN : null)
                    .attemptGroupProjectionVersion(attemptGroupProperties.writerEnabled() ? 0L : null)
                    .build();
            sessionRepository.insert(session);
            operation.markSessionCommitted(committedAt);
            return persist(operation);
        });
    }

    public ExamCreationOperation finalizeConfirmed(String commandId, Instant confirmedAt) {
        return inTransaction(() -> {
            ExamCreationOperation operation = required(commandId);
            touchOwner(operation.getUserId());
            if (operation.getState() == ExamCreationState.SUCCEEDED) {
                return operation;
            }
            if (operation.getState() != ExamCreationState.SESSION_COMMITTED) {
                throw new IllegalStateException("Exam creation operation is not SESSION_COMMITTED");
            }
            if (execution != null) {
                var session = sessionRepository.findById(operation.getSessionId()).orElse(null);
                web.tosunsaeng.domain.exams.billing.reconciliation.ReservationSnapshotValidator.session(operation, session);
            }
            long updated = sessionRepository.confirmEntitlementIfConfirming(
                    operation.getSessionId(), confirmedAt);
            if (updated != 1) {
                ExamSession existing = sessionRepository.findById(operation.getSessionId())
                        .orElseThrow(() -> new IllegalStateException("Committed ExamSession is missing"));
                if (existing.getStatus() != ExamSessionStatus.IN_PROGRESS
                        || existing.getEntitlementState() != ExamEntitlementState.CONFIRMED
                        || !Objects.equals(existing.getCreationOperationId(), operation.getOperationId())) {
                    throw new IllegalStateException("Committed ExamSession cannot be finalized");
                }
            }
            operation.markSucceeded(confirmedAt, confirmedAt.plus(TERMINAL_RETENTION));
            return persist(operation);
        });
    }

    public ExamCreationOperation markCancelPending(String commandId, Instant now) {
        return inTransaction(() -> {
            ExamCreationOperation operation = required(commandId);
            touchOwner(operation.getUserId());
            if (operation.getState() == ExamCreationState.CANCEL_PENDING) {
                return operation;
            }
            if (execution != null) requireNoSession(operation);
            if (operation.getRecovery() != null) operation.getRecovery().setIntent(
                    web.tosunsaeng.domain.exams.billing.reconciliation.ReservationRecovery.Intent.CLEANUP_PRECOMMIT);
            operation.markCancelPending(now);
            return persist(operation);
        });
    }

    public ExamCreationOperation markCanceled(String commandId, Instant terminalAt) {
        return inTransaction(() -> {
            ExamCreationOperation operation = required(commandId);
            touchOwner(operation.getUserId());
            if (operation.getState() == ExamCreationState.CANCELED) {
                return operation;
            }
            abandonConfirming(operation);
            operation.markCanceled(terminalAt, terminalAt.plus(TERMINAL_RETENTION));
            return persist(operation);
        });
    }

    public ExamCreationOperation markExpired(String commandId, Instant terminalAt) {
        return inTransaction(() -> {
            ExamCreationOperation operation = required(commandId);
            touchOwner(operation.getUserId());
            if (operation.getState() == ExamCreationState.EXPIRED) {
                return operation;
            }
            abandonConfirming(operation);
            operation.markExpired(terminalAt, terminalAt.plus(TERMINAL_RETENTION));
            return persist(operation);
        });
    }

    public ExamCreationOperation markFailedTerminal(
            String commandId,
            String failureCategory,
            Instant terminalAt
    ) {
        return inTransaction(() -> {
            ExamCreationOperation operation = required(commandId);
            touchOwner(operation.getUserId());
            if (operation.getState() == ExamCreationState.FAILED_TERMINAL) {
                return operation;
            }
            abandonConfirming(operation);
            operation.markFailedTerminal(
                    failureCategory,
                    terminalAt,
                    terminalAt.plus(TERMINAL_RETENTION)
            );
            return persist(operation);
        });
    }

    public ExamCreationOperation insertPrepared(ExamCreationOperation operation) {
        return inTransaction(() -> {
            touchOwner(operation.getUserId());
            if (execution != null) execution.scheduleInitialProgress(operation);
            return operationRepository.insert(operation);
        });
    }

    public ExamCreationOperation saveOperation(ExamCreationOperation operation) {
        return inTransaction(() -> {
            if (execution != null) operation.adoptExecutionMetadata(
                    execution.fence(operation.getCommandId(), operation.getVersion()));
            touchOwner(operation.getUserId());
            return persist(operation);
        });
    }

    public boolean userMergedWriterEnabled() {
        return userOwnedTransactionExecutor != null && userOwnedTransactionExecutor.enabled();
    }

    private void touchOwner(String userId) {
        if (execution != null) execution.requireOwner(userId);
        if (userOwnedTransactionExecutor != null) {
            userOwnedTransactionExecutor.touchWithinExistingTransaction(userId);
        }
    }

    public ExamCreationOperation beginCleanup(String commandId) {
        return inTransaction(() -> {
            ExamCreationOperation operation = required(commandId);
            touchOwner(operation.getUserId());
            requireNoSession(operation);
            if (operation.getState() != ExamCreationState.PREPARED
                    && operation.getState() != ExamCreationState.RESERVED
                    && operation.getState() != ExamCreationState.CANCEL_PENDING) {
                throw new IllegalStateException("Committed operation cannot enter cleanup");
            }
            operation.getRecovery().setIntent(
                    web.tosunsaeng.domain.exams.billing.reconciliation.ReservationRecovery.Intent.CLEANUP_PRECOMMIT);
            if (operation.getState() == ExamCreationState.RESERVED) operation.markCancelPending(execution.now());
            return persist(operation);
        });
    }

    public ExamCreationOperation markReserveDispatched(String commandId) {
        return inTransaction(() -> {
            ExamCreationOperation operation = required(commandId);
            touchOwner(operation.getUserId());
            if (operation.getState() != ExamCreationState.PREPARED
                    || operation.getRecovery().getIntent()
                    != web.tosunsaeng.domain.exams.billing.reconciliation.ReservationRecovery.Intent.CONTINUE) {
                throw new IllegalStateException("Reservation cannot be dispatched");
            }
            operation.getRecovery().setDispatch(
                    web.tosunsaeng.domain.exams.billing.reconciliation.ReservationRecovery.Dispatch.MAY_HAVE_BEEN_SENT);
            return persist(operation);
        });
    }

    private void abandonConfirming(ExamCreationOperation operation) {
        if (execution != null) {
            if (operation.getState() == ExamCreationState.SESSION_COMMITTED) {
                ExamSession session = sessionRepository.findById(operation.getSessionId()).orElse(null);
                web.tosunsaeng.domain.exams.billing.reconciliation.ReservationSnapshotValidator.session(operation, session);
                web.tosunsaeng.domain.exams.billing.reconciliation.ReservationSnapshotValidator.require(
                        session.getStatus() == ExamSessionStatus.ENTITLEMENT_CONFIRMING
                                && session.getEntitlementState() == ExamEntitlementState.CONFIRMING);
                if (sessionRepository.abandonIfEntitlementConfirming(session.getExamId()) != 1) {
                    throw new IllegalStateException("Confirming Session changed concurrently");
                }
            } else {
                requireNoSession(operation);
            }
        } else {
            sessionRepository.findByUserIdAndCreationOperationId(operation.getUserId(), operation.getOperationId())
                    .ifPresent(session -> sessionRepository.abandonIfEntitlementConfirming(session.getExamId()));
        }
    }

    private void requireNoSession(ExamCreationOperation operation) {
        if (sessionRepository.findById(operation.getSessionId()).isPresent()
                || sessionRepository.findByUserIdAndCreationOperationId(
                        operation.getUserId(), operation.getOperationId()).isPresent()) {
            throw new IllegalStateException("Session evidence forbids reservation cleanup");
        }
    }

    private ExamCreationOperation persist(ExamCreationOperation operation) {
        if (execution != null) {
            if (operation.isTerminal()) operation.completeRecovery(execution.now());
            else if (operation.getRecovery() != null) {
                operation.getRecovery().setLastProgressAt(operation.getUpdatedAt());
                execution.scheduleInitialProgress(operation);
            }
        }
        return operationRepository.save(operation);
    }

    private ExamCreationOperation required(String commandId) {
        if (execution != null) return execution.fence(commandId, null);
        return operationRepository.findById(commandId)
                .orElseThrow(() -> new IllegalStateException("Exam creation operation is missing"));
    }

    private <T> T inTransaction(Supplier<T> work) {
        TransactionOperations operations = transactionOperationsProvider.getIfAvailable();
        if (operations == null) {
            throw new IllegalStateException("Billing Mongo transaction manager is unavailable");
        }
        try {
            T result = operations.execute(status -> work.get());
            if (result == null) throw new IllegalStateException("Billing Mongo transaction returned no result");
            return result;
        } catch (RuntimeException failure) {
            Throwable cause = failure;
            for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
                if (cause instanceof com.mongodb.MongoException mongo
                        && mongo.hasErrorLabel(com.mongodb.MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) {
                    throw new web.tosunsaeng.domain.exams.billing.reconciliation.ReservationCommitOutcomeUnknownException(failure);
                }
                if (cause == cause.getCause()) break;
            }
            // Retry at the command/pass boundary after fresh reload, never replay a mutated entity.
            throw failure;
        }
    }
}
