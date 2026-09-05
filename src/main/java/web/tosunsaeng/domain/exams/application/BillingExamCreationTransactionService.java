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
            return operationRepository.save(operation);
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
            return operationRepository.save(operation);
        });
    }

    public ExamCreationOperation markCancelPending(String commandId, Instant now) {
        return inTransaction(() -> {
            ExamCreationOperation operation = required(commandId);
            touchOwner(operation.getUserId());
            if (operation.getState() == ExamCreationState.CANCEL_PENDING) {
                return operation;
            }
            operation.markCancelPending(now);
            return operationRepository.save(operation);
        });
    }

    public ExamCreationOperation markCanceled(String commandId, Instant terminalAt) {
        return inTransaction(() -> {
            ExamCreationOperation operation = required(commandId);
            touchOwner(operation.getUserId());
            if (operation.getState() == ExamCreationState.CANCELED) {
                return operation;
            }
            sessionRepository.findByUserIdAndCreationOperationId(
                            operation.getUserId(), operation.getOperationId())
                    .ifPresent(session -> sessionRepository.abandonIfEntitlementConfirming(session.getExamId()));
            operation.markCanceled(terminalAt, terminalAt.plus(TERMINAL_RETENTION));
            return operationRepository.save(operation);
        });
    }

    public ExamCreationOperation markExpired(String commandId, Instant terminalAt) {
        return inTransaction(() -> {
            ExamCreationOperation operation = required(commandId);
            touchOwner(operation.getUserId());
            if (operation.getState() == ExamCreationState.EXPIRED) {
                return operation;
            }
            sessionRepository.findByUserIdAndCreationOperationId(
                            operation.getUserId(), operation.getOperationId())
                    .ifPresent(session -> sessionRepository.abandonIfEntitlementConfirming(session.getExamId()));
            operation.markExpired(terminalAt, terminalAt.plus(TERMINAL_RETENTION));
            return operationRepository.save(operation);
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
            sessionRepository.findByUserIdAndCreationOperationId(
                            operation.getUserId(), operation.getOperationId())
                    .ifPresent(session -> sessionRepository.abandonIfEntitlementConfirming(session.getExamId()));
            operation.markFailedTerminal(
                    failureCategory,
                    terminalAt,
                    terminalAt.plus(TERMINAL_RETENTION)
            );
            return operationRepository.save(operation);
        });
    }

    public ExamCreationOperation insertPrepared(ExamCreationOperation operation) {
        return inTransaction(() -> {
            touchOwner(operation.getUserId());
            return operationRepository.insert(operation);
        });
    }

    public ExamCreationOperation saveOperation(ExamCreationOperation operation) {
        return inTransaction(() -> {
            touchOwner(operation.getUserId());
            return operationRepository.save(operation);
        });
    }

    public boolean userMergedWriterEnabled() {
        return userOwnedTransactionExecutor != null && userOwnedTransactionExecutor.enabled();
    }

    private void touchOwner(String userId) {
        if (userOwnedTransactionExecutor != null) {
            userOwnedTransactionExecutor.touchWithinExistingTransaction(userId);
        }
    }

    private ExamCreationOperation required(String commandId) {
        return operationRepository.findById(commandId)
                .orElseThrow(() -> new IllegalStateException("Exam creation operation is missing"));
    }

    private <T> T inTransaction(Supplier<T> work) {
        TransactionOperations operations = transactionOperationsProvider.getIfAvailable();
        if (operations == null) {
            throw new IllegalStateException("Billing Mongo transaction manager is unavailable");
        }
        T result = operations.execute(status -> work.get());
        if (result == null) {
            throw new IllegalStateException("Billing Mongo transaction returned no result");
        }
        return result;
    }
}
