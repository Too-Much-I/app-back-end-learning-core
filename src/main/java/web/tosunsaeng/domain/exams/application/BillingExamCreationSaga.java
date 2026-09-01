package web.tosunsaeng.domain.exams.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.exams.billing.BillingClientException;
import web.tosunsaeng.domain.exams.billing.BillingReservationClient;
import web.tosunsaeng.domain.exams.billing.BillingSagaProperties;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind;
import web.tosunsaeng.domain.exams.domain.enums.ExamCreationState;
import web.tosunsaeng.domain.exams.domain.repository.ExamCreationOperationRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class BillingExamCreationSaga {

    private static final int MAX_STATE_STEPS = 8;

    private final BillingSagaProperties properties;
    private final ExamCreationOperationRepository operationRepository;
    private final ExamSessionRepository sessionRepository;
    private final ExamSessionManager sessionManager;
    private final MockExamCatalogService mockExamCatalogService;
    private final BillingReservationClient billingClient;
    private final BillingExamCreationTransactionService transactionService;
    private final Clock clock;

    public BillingExamCreationSaga(
            BillingSagaProperties properties,
            ExamCreationOperationRepository operationRepository,
            ExamSessionRepository sessionRepository,
            ExamSessionManager sessionManager,
            MockExamCatalogService mockExamCatalogService,
            BillingReservationClient billingClient,
            BillingExamCreationTransactionService transactionService,
            @Qualifier("gradingClock") Clock clock
    ) {
        this.properties = properties;
        this.operationRepository = operationRepository;
        this.sessionRepository = sessionRepository;
        this.sessionManager = sessionManager;
        this.mockExamCatalogService = mockExamCatalogService;
        this.billingClient = billingClient;
        this.transactionService = transactionService;
        this.clock = clock;
    }

    public ExamSessionManager.Assignment start(String userId, String rawOperationId) {
        if (!properties.isCreationSagaEnabled()) {
            throw new IllegalStateException("Billing exam creation saga is disabled");
        }
        String operationId = ExamCreationIdempotencyKey.parse(rawOperationId);
        ExamCreationOperation operation = operationRepository
                .findByUserIdAndOperationId(userId, operationId)
                .orElse(null);
        if (operation == null) {
            ExamSession durableReplay = sessionRepository
                    .findByUserIdAndCreationOperationId(userId, operationId)
                    .orElse(null);
            if (durableReplay != null) {
                return replayFromSession(userId, operationId, durableReplay);
            }
            operation = findOrPrepare(userId, operationId);
        }

        for (int step = 0; step < MAX_STATE_STEPS; step++) {
            operation = reload(operation.getCommandId());
            switch (operation.getState()) {
                case PREPARED -> reserve(operation);
                case RESERVED -> commitSession(operation);
                case SESSION_COMMITTED -> confirmOrReconcile(operation);
                case SUCCEEDED -> {
                    return successfulAssignment(operation);
                }
                case CANCEL_PENDING -> reconcileCancel(operation);
                case CANCELED, EXPIRED, FAILED_TERMINAL -> throw terminalOutcome(operation);
            }
        }
        throw new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE, 1);
    }

    private ExamCreationOperation findOrPrepare(String userId, String operationId) {
        ExamCreationOperation existing = operationRepository
                .findByUserIdAndOperationId(userId, operationId)
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        if (operationRepository.findByUserIdAndActiveGuardTrue(userId).isPresent()) {
            throw new ExamsException(ErrorStatus._EXAM_CREATION_PROCESSING, 1);
        }

        ExamSessionManager.PreparedAssignment prepared = sessionManager.prepareForBilling(userId);
        Instant now = now();
        ExamCreationOperation operation = ExamCreationOperation.prepared(
                userId,
                operationId,
                prepared.sessionId(),
                prepared.mockExam().getMockExamId(),
                prepared.cycleNumber(),
                prepared.replacementSourceSessionId(),
                prepared.expectedAttemptGroupId(),
                prepared.expectedMockExamId(),
                now
        );
        try {
            return operationRepository.insert(operation);
        } catch (DuplicateKeyException concurrent) {
            return operationRepository.findByUserIdAndOperationId(userId, operationId)
                    .orElseThrow(() -> new ExamsException(
                            ErrorStatus._EXAM_CREATION_PROCESSING, 1));
        }
    }

    private void reserve(ExamCreationOperation operation) {
        BillingReservationClient.ReservationSnapshot snapshot;
        try {
            snapshot = billingClient.reserve(
                    operation.getOperationId(),
                    operation.getUserId(),
                    operation.getSessionId(),
                    operation.getMockExamId()
            );
        } catch (BillingClientException failure) {
            if (failure.category() == BillingClientException.Category.ENTITLEMENT_INSUFFICIENT
                    || failure.category() == BillingClientException.Category.IDEMPOTENCY_CONFLICT
                    || failure.category() == BillingClientException.Category.CONTRACT_ERROR
                    || failure.category() == BillingClientException.Category.INVALID_REQUEST) {
                markTerminalFailure(operation, failure.category().name());
            }
            throw publicFailure(failure);
        }

        try {
            validateReserved(operation, snapshot);
        } catch (RuntimeException contractFailure) {
            markTerminalFailure(operation, "RESERVE_CONTRACT_MISMATCH");
            throw new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE);
        }

        try {
            operation.markReserved(
                    snapshot.reservationId(),
                    snapshot.reservationKind(),
                    snapshot.attemptGroupId(),
                    snapshot.expiresAt(),
                    now()
            );
            operationRepository.save(operation);
        } catch (OptimisticLockingFailureException concurrent) {
            // Another replay advanced the same operation. The caller loop reloads it.
        } catch (RuntimeException persistenceFailure) {
            throw new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE, 1);
        }
    }

    private void commitSession(ExamCreationOperation operation) {
        try {
            transactionService.commitReservedSession(
                    operation.getCommandId(), now(), clock.getZone());
        } catch (DuplicateKeyException | OptimisticLockingFailureException concurrent) {
            if (observeCommitOutcome(operation) == CommitObservation.ADVANCED) {
                return;
            }
            throw new ExamsException(ErrorStatus._EXAM_CREATION_PROCESSING, 1);
        } catch (IllegalStateException definiteLocalFailure) {
            CommitObservation observation = observeCommitOutcome(operation);
            if (observation == CommitObservation.ADVANCED) {
                return;
            }
            if (observation == CommitObservation.SESSION_VISIBLE) {
                throw new ExamsException(ErrorStatus._EXAM_CREATION_PROCESSING, 1);
            }
            cancelAfterCommitFailure(operation);
            throw new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE, 1);
        } catch (RuntimeException unknownCommitOutcome) {
            if (observeCommitOutcome(operation) == CommitObservation.ADVANCED) {
                return;
            }
            // A transient or unknown Mongo commit result may become visible later. The Billing
            // reservation is shared by every same-key replay, so it must never be canceled here.
            throw new ExamsException(ErrorStatus._EXAM_CREATION_PROCESSING, 1);
        }
    }

    private CommitObservation observeCommitOutcome(ExamCreationOperation operation) {
        ExamCreationOperation reloaded = operationRepository.findById(operation.getCommandId())
                .orElse(null);
        if (reloaded != null
                && (reloaded.getState() == ExamCreationState.SESSION_COMMITTED
                || reloaded.getState() == ExamCreationState.SUCCEEDED)) {
            return CommitObservation.ADVANCED;
        }
        return sessionRepository.findByUserIdAndCreationOperationId(
                        operation.getUserId(), operation.getOperationId())
                .isPresent()
                ? CommitObservation.SESSION_VISIBLE
                : CommitObservation.NOT_VISIBLE;
    }

    private void cancelAfterCommitFailure(ExamCreationOperation operation) {
        try {
            BillingReservationClient.ReservationSnapshot canceled = billingClient.cancel(
                    operation.getOperationId(),
                    operation.getReservationId(),
                    operation.getUserId()
            );
            validateCanceled(operation, canceled);
            transactionService.markCanceled(
                    operation.getCommandId(),
                    terminalTime(canceled)
            );
        } catch (BillingClientException failure) {
            if (failure.category() == BillingClientException.Category.RESERVATION_CONFLICT) {
                reconcileCancel(operation);
                return;
            }
            transactionService.markCancelPending(operation.getCommandId(), now());
        } catch (RuntimeException cancelFailure) {
            transactionService.markCancelPending(operation.getCommandId(), now());
        }
    }

    private void confirmOrReconcile(ExamCreationOperation operation) {
        try {
            BillingReservationClient.ReservationSnapshot confirmed = billingClient.confirm(
                    operation.getOperationId(),
                    operation.getReservationId(),
                    operation.getUserId(),
                    operation.getSessionId(),
                    operation.getSessionCommittedAt()
            );
            validateConfirmed(operation, confirmed);
            transactionService.finalizeConfirmed(
                    operation.getCommandId(),
                    terminalTime(confirmed)
            );
        } catch (BillingClientException failure) {
            if (failure.category() == BillingClientException.Category.TEMPORARILY_UNAVAILABLE
                    || failure.category() == BillingClientException.Category.PROCESSING
                    || failure.category() == BillingClientException.Category.RESERVATION_CONFLICT) {
                reconcileCommitted(operation, failure.retryAfterSeconds());
                return;
            }
            throw publicFailure(failure);
        } catch (OptimisticLockingFailureException concurrent) {
            // Another replay finalized it. The caller loop reloads it.
        } catch (RuntimeException contractFailure) {
            throw new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE, 1);
        }
    }

    private void reconcileCommitted(ExamCreationOperation operation, Integer retryAfterSeconds) {
        BillingReservationClient.ReservationSnapshot status;
        try {
            status = billingClient.status(operation.getUserId(), operation.getOperationId());
            validateStatusSnapshot(operation, status);
        } catch (BillingClientException statusFailure) {
            throw publicFailure(statusFailure);
        } catch (RuntimeException contractFailure) {
            throw new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE, 1);
        }

        switch (status.reservationStatus()) {
            case CONFIRMED -> {
                validateConfirmed(operation, status);
                transactionService.finalizeConfirmed(
                        operation.getCommandId(), terminalTime(status));
            }
            case RESERVED -> throw new ExamsException(
                    ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE,
                    retryAfterSeconds == null ? 1 : retryAfterSeconds
            );
            case CANCELED -> {
                transactionService.markCanceled(
                        operation.getCommandId(), terminalTime(status));
                throw new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE);
            }
            case EXPIRED -> {
                transactionService.markExpired(
                        operation.getCommandId(), terminalTime(status));
                throw new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE);
            }
        }
    }

    private void reconcileCancel(ExamCreationOperation operation) {
        BillingReservationClient.ReservationSnapshot status;
        try {
            status = billingClient.status(operation.getUserId(), operation.getOperationId());
            validateStatusSnapshot(operation, status);
        } catch (BillingClientException failure) {
            throw publicFailure(failure);
        } catch (RuntimeException contractFailure) {
            throw new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE, 1);
        }
        switch (status.reservationStatus()) {
            case CANCELED -> transactionService.markCanceled(
                    operation.getCommandId(), terminalTime(status));
            case EXPIRED -> transactionService.markExpired(
                    operation.getCommandId(), terminalTime(status));
            case RESERVED -> {
                try {
                    BillingReservationClient.ReservationSnapshot canceled = billingClient.cancel(
                            operation.getOperationId(), operation.getReservationId(), operation.getUserId());
                    validateCanceled(operation, canceled);
                    transactionService.markCanceled(
                            operation.getCommandId(), terminalTime(canceled));
                } catch (BillingClientException failure) {
                    throw publicFailure(failure);
                }
            }
            case CONFIRMED -> {
                if (sessionRepository.findByUserIdAndCreationOperationId(
                        operation.getUserId(), operation.getOperationId()).isPresent()) {
                    validateConfirmed(operation, status);
                    transactionService.finalizeConfirmed(
                            operation.getCommandId(), terminalTime(status));
                } else {
                    throw new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE, 1);
                }
            }
        }
    }

    private ExamSessionManager.Assignment successfulAssignment(ExamCreationOperation operation) {
        ExamSession session = sessionRepository.findByUserIdAndCreationOperationId(
                        operation.getUserId(), operation.getOperationId())
                .orElseThrow(() -> new ExamsException(
                        ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE));
        if (!Objects.equals(session.getExamId(), operation.getSessionId())
                || !Objects.equals(session.getUserId(), operation.getUserId())
                || !Objects.equals(session.getMockExamId(), operation.getMockExamId())
                || session.getEntitlementState()
                != web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState.CONFIRMED) {
            throw new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE);
        }
        return new ExamSessionManager.Assignment(
                session,
                mockExamCatalogService.getRequiredExam(operation.getMockExamId()),
                false
        );
    }

    private void validateReserved(
            ExamCreationOperation operation,
            BillingReservationClient.ReservationSnapshot snapshot
    ) {
        validateStatusIdentity(operation, snapshot);
        if (snapshot.reservationStatus() != BillingReservationClient.ReservationStatus.RESERVED
                || snapshot.reservationKind() == null
                || !isLowercaseUuidV4(snapshot.reservationId())
                || !isLowercaseUuidV4(snapshot.attemptGroupId())
                || snapshot.expiresAt() == null
                || !snapshot.expiresAt().isAfter(now())) {
            throw new IllegalStateException("Billing reserve response is invalid");
        }

        boolean replacementExpected = !isBlank(operation.getReplacementSourceSessionId())
                && !isBlank(operation.getExpectedAttemptGroupId())
                && !isBlank(operation.getExpectedMockExamId());
        if (snapshot.reservationKind() == BillingReservationKind.INITIAL && replacementExpected) {
            throw new IllegalStateException("Billing returned INITIAL for an existing group");
        }
        if (snapshot.reservationKind() == BillingReservationKind.REPLACEMENT
                && (!replacementExpected
                || !Objects.equals(operation.getExpectedAttemptGroupId(), snapshot.attemptGroupId())
                || !Objects.equals(operation.getExpectedMockExamId(), operation.getMockExamId()))) {
            throw new IllegalStateException("Billing replacement group does not match local state");
        }
    }

    private void validateConfirmed(
            ExamCreationOperation operation,
            BillingReservationClient.ReservationSnapshot snapshot
    ) {
        validateOperationAndReservation(operation, snapshot);
        if (snapshot.reservationStatus() != BillingReservationClient.ReservationStatus.CONFIRMED
                || !Objects.equals(snapshot.sessionId(), operation.getSessionId())
                || !Objects.equals(snapshot.attemptGroupId(), operation.getAttemptGroupId())
                || snapshot.attemptGroupStatus()
                != BillingReservationClient.AttemptGroupStatus.OPEN
                || snapshot.terminalAt() == null) {
            throw new IllegalStateException("Billing confirm response is invalid");
        }
    }

    private void validateStatusIdentity(
            ExamCreationOperation operation,
            BillingReservationClient.ReservationSnapshot snapshot
    ) {
        if (snapshot == null
                || snapshot.reservationStatus() == null
                || !Objects.equals(snapshot.operationId(), operation.getOperationId())
                || !Objects.equals(snapshot.sessionId(), operation.getSessionId())
                || !Objects.equals(snapshot.mockExamId(), operation.getMockExamId())) {
            throw new IllegalStateException("Billing operation response does not match");
        }
        if (operation.getReservationId() != null
                && !Objects.equals(snapshot.reservationId(), operation.getReservationId())) {
            throw new IllegalStateException("Billing reservation response does not match");
        }
    }

    private void validateStatusSnapshot(
            ExamCreationOperation operation,
            BillingReservationClient.ReservationSnapshot snapshot
    ) {
        validateStatusIdentity(operation, snapshot);
        if (!Objects.equals(snapshot.reservationKind(), operation.getReservationKind())
                || !Objects.equals(snapshot.attemptGroupId(), operation.getAttemptGroupId())
                || (snapshot.reservationStatus()
                == BillingReservationClient.ReservationStatus.RESERVED
                && snapshot.expiresAt() == null)
                || (snapshot.reservationStatus()
                != BillingReservationClient.ReservationStatus.RESERVED
                && snapshot.terminalAt() == null)) {
            throw new IllegalStateException("Billing status response is invalid");
        }
    }

    private void validateCanceled(
            ExamCreationOperation operation,
            BillingReservationClient.ReservationSnapshot snapshot
    ) {
        validateOperationAndReservation(operation, snapshot);
        if (snapshot.reservationStatus()
                != BillingReservationClient.ReservationStatus.CANCELED
                || snapshot.terminalAt() == null) {
            throw new IllegalStateException("Billing cancel response is invalid");
        }
    }

    private void validateOperationAndReservation(
            ExamCreationOperation operation,
            BillingReservationClient.ReservationSnapshot snapshot
    ) {
        if (snapshot == null
                || snapshot.reservationStatus() == null
                || !Objects.equals(snapshot.operationId(), operation.getOperationId())
                || !Objects.equals(snapshot.reservationId(), operation.getReservationId())) {
            throw new IllegalStateException("Billing lifecycle response does not match");
        }
    }

    private void markTerminalFailure(ExamCreationOperation operation, String category) {
        try {
            transactionService.markFailedTerminal(
                    operation.getCommandId(), category, now());
        } catch (RuntimeException persistenceFailure) {
            log.warn(
                    "시험 생성 terminal 상태 저장 실패 event=exam.creation.saga "
                            + "outcome=failed reason=terminal_persistence examId={} errorType={}",
                    operation.getSessionId(), persistenceFailure.getClass().getName()
            );
        }
    }

    private ExamsException terminalOutcome(ExamCreationOperation operation) {
        if (operation.getState() == ExamCreationState.FAILED_TERMINAL) {
            return switch (Objects.toString(operation.getFailureCategory(), "")) {
                case "ENTITLEMENT_INSUFFICIENT" ->
                        new ExamsException(ErrorStatus._ENTITLEMENT_INSUFFICIENT);
                case "IDEMPOTENCY_CONFLICT", "IDEMPOTENCY_KEY_CONFLICT" ->
                        new ExamsException(ErrorStatus._IDEMPOTENCY_KEY_CONFLICT);
                default -> new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE);
            };
        }
        return new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE);
    }

    private ExamsException publicFailure(BillingClientException failure) {
        return switch (failure.category()) {
            case ENTITLEMENT_INSUFFICIENT ->
                    new ExamsException(ErrorStatus._ENTITLEMENT_INSUFFICIENT);
            case PROCESSING -> new ExamsException(
                    ErrorStatus._EXAM_CREATION_PROCESSING,
                    defaultRetryAfter(failure)
            );
            case IDEMPOTENCY_CONFLICT ->
                    new ExamsException(ErrorStatus._IDEMPOTENCY_KEY_CONFLICT);
            case RATE_LIMITED -> new ExamsException(
                    ErrorStatus._BILLING_RATE_LIMITED,
                    defaultRetryAfter(failure)
            );
            default -> new ExamsException(
                    ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE,
                    retryable(failure.category()) ? defaultRetryAfter(failure) : null
            );
        };
    }

    private int defaultRetryAfter(BillingClientException failure) {
        return failure.retryAfterSeconds() == null ? 1 : failure.retryAfterSeconds();
    }

    private static boolean retryable(BillingClientException.Category category) {
        return category == BillingClientException.Category.TEMPORARILY_UNAVAILABLE
                || category == BillingClientException.Category.OPERATION_NOT_FOUND
                || category == BillingClientException.Category.RESERVATION_CONFLICT;
    }

    private ExamCreationOperation reload(String commandId) {
        return operationRepository.findById(commandId)
                .orElseThrow(() -> new ExamsException(
                        ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE));
    }

    private Instant terminalTime(BillingReservationClient.ReservationSnapshot snapshot) {
        if (snapshot.terminalAt() == null) {
            throw new IllegalStateException("Billing terminal timestamp is missing");
        }
        return snapshot.terminalAt();
    }

    private ExamSessionManager.Assignment replayFromSession(
            String userId,
            String operationId,
            ExamSession session
    ) {
        if (!Objects.equals(session.getUserId(), userId)
                || !Objects.equals(session.getCreationOperationId(), operationId)) {
            throw new ExamsException(ErrorStatus._IDEMPOTENCY_KEY_CONFLICT);
        }
        if (session.getEntitlementState()
                != web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState.CONFIRMED) {
            throw new ExamsException(ErrorStatus._EXAM_CREATION_PROCESSING, 1);
        }
        return new ExamSessionManager.Assignment(
                session,
                mockExamCatalogService.getRequiredExam(session.getMockExamId()),
                false
        );
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isLowercaseUuidV4(String value) {
        if (isBlank(value)) {
            return false;
        }
        try {
            UUID parsed = UUID.fromString(value);
            return parsed.version() == 4 && parsed.toString().equals(value);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private enum CommitObservation {
        ADVANCED,
        SESSION_VISIBLE,
        NOT_VISIBLE
    }
}
