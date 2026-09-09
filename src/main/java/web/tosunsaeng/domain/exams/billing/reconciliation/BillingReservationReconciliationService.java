package web.tosunsaeng.domain.exams.billing.reconciliation;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.exams.application.BillingExamCreationTransactionService;
import web.tosunsaeng.domain.exams.billing.BillingClientException;
import web.tosunsaeng.domain.exams.billing.BillingReservationClient;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.enums.ExamCreationState;
import web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardException;
import web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard;
import web.tosunsaeng.domain.withdrawal.domain.WithdrawnUserAccessDeny;

import java.util.concurrent.TimeUnit;

import static web.tosunsaeng.domain.exams.billing.reconciliation.ReservationSnapshotValidator.require;

@Slf4j
@Service
public class BillingReservationReconciliationService {
    private final ReservationOperationExecution execution;
    private final ReservationRecoveryStore store;
    private final ReservationAuthCircuit circuit;
    private final ReservationReconciliationProperties properties;
    private final BillingExamCreationTransactionService transactions;
    private final BillingReservationClient billing;
    private final MongoTemplate mongo;
    private final MeterRegistry metrics;
    private final Tracer tracer;

    public BillingReservationReconciliationService(ReservationOperationExecution execution, ReservationRecoveryStore store,
            ReservationAuthCircuit circuit, ReservationReconciliationProperties properties,
            BillingExamCreationTransactionService transactions, BillingReservationClient billing,
            MongoTemplate mongo, MeterRegistry metrics, ObjectProvider<Tracer> tracer) {
        this.execution = execution; this.store = store; this.circuit = circuit; this.properties = properties;
        this.transactions = transactions; this.billing = billing; this.mongo = mongo; this.metrics = metrics;
        this.tracer = tracer.getIfAvailable();
    }

    public void run(String commandId) {
        if (!properties.isEnabled()) return;
        ReservationOperationExecution.Lease lease = execution.claim(commandId, true);
        if (lease == null) return;
        long started = System.nanoTime();
        Span span = tracer == null ? null : tracer.nextSpan().name("billing_reservation_reconcile").start();
        String outcome = "retry_scheduled";
        try (Tracer.SpanInScope ignored = tracer == null ? null : tracer.withSpan(span)) {
            outcome = execution.within(lease, () -> attempt(lease));
        } finally {
            try { execution.release(lease); } catch (RuntimeException ignored) { /* expiration recovers ownership */ }
            long nanos = Math.max(0, System.nanoTime() - started);
            metrics.counter("learning_core.billing.reconciliation.attempt", "service", "learning-core",
                    "operation", "billing_reservation_reconcile", "outcome", outcome).increment();
            metrics.timer("learning_core.billing.reconciliation.duration", "service", "learning-core",
                    "operation", "billing_reservation_reconcile", "outcome", outcome).record(nanos, TimeUnit.NANOSECONDS);
            log.info("service=learning-core operation=billing_reservation_reconcile outcome={} traceId={} durationMs={}",
                    outcome, span == null ? "unavailable" : span.context().traceId(), TimeUnit.NANOSECONDS.toMillis(nanos));
            if (span != null) span.end();
        }
    }

    private String attempt(ReservationOperationExecution.Lease lease) {
        try {
            ExamCreationOperation op = execution.freshOperation(lease.id());
            if (op == null || op.isTerminal()) return "noop";
            ReservationSnapshotValidator.require(ReservationSnapshotValidator.uuid4(op.getCommandId())
                    && ReservationSnapshotValidator.uuid4(op.getOperationId()));
            if (circuit.blocked()) { store.blockAuth(lease); return "blocked_auth"; }
            requireOwner(op);
            require(op.getRecovery() != null && op.getRecovery().getLastProgressAt() != null);
            boolean precommit = op.getState() == ExamCreationState.PREPARED || op.getState() == ExamCreationState.RESERVED;
            if (precommit && execution.now().isBefore(op.getRecovery().getLastProgressAt().plus(properties.getPrecommitStale()))) {
                store.retry(lease, (int) properties.getPoll().toSeconds());
                return "retry_scheduled";
            }
            if (!store.startAttempt(lease, op)) return "needs_review";
            op = execution.freshOperation(lease.id());
            require(op != null);
            if (precommit || op.getState() == ExamCreationState.CANCEL_PENDING) {
                op = transactions.beginCleanup(op.getCommandId());
                if (op.getState() == ExamCreationState.PREPARED
                        && op.getRecovery().getDispatch() == ReservationRecovery.Dispatch.NOT_DISPATCHED) {
                    transactions.markFailedTerminal(op.getCommandId(), "RESERVE_NOT_DISPATCHED", execution.now());
                    return "canceled";
                }
            } else {
                require(op.getState() == ExamCreationState.SESSION_COMMITTED && op.getSessionCommittedAt() != null);
                ExamSession session = execution.freshSession(op.getSessionId());
                ReservationSnapshotValidator.session(op, session);
                require(session.getStatus() == ExamSessionStatus.ENTITLEMENT_CONFIRMING
                        && session.getEntitlementState() == ExamEntitlementState.CONFIRMING);
            }

            execution.beforeRemote(op.getCommandId());
            BillingReservationClient.ReservationSnapshot status = billing.status(op.getUserId(), op.getOperationId());
            ReservationSnapshotValidator.status(op, status);
            if (op.getState() == ExamCreationState.PREPARED) {
                if (status.reservationStatus() == BillingReservationClient.ReservationStatus.CONFIRMED) {
                    throw new ReservationRecoveryException(ReservationRecoveryException.Reason.STATE_INCONSISTENT);
                }
                op.observeReservation(status.reservationId(), status.reservationKind(), status.attemptGroupId(), status.expiresAt());
                if (status.reservationStatus() == BillingReservationClient.ReservationStatus.RESERVED) op.markCancelPending(execution.now());
                op = transactions.saveOperation(op);
            }
            requireOwner(op);
            switch (status.reservationStatus()) {
                case CANCELED -> { transactions.markCanceled(op.getCommandId(), status.terminalAt()); return "canceled"; }
                case EXPIRED -> { transactions.markExpired(op.getCommandId(), status.terminalAt()); return "expired"; }
                case CONFIRMED -> {
                    require(op.getState() == ExamCreationState.SESSION_COMMITTED);
                    ReservationSnapshotValidator.confirmed(op, status);
                    transactions.finalizeConfirmed(op.getCommandId(), status.terminalAt());
                    return "confirmed";
                }
                case RESERVED -> {
                    execution.beforeRemote(op.getCommandId());
                    if (op.getState() == ExamCreationState.SESSION_COMMITTED) {
                        BillingReservationClient.ReservationSnapshot confirmed = billing.confirm(op.getOperationId(),
                                op.getReservationId(), op.getUserId(), op.getSessionId(), op.getSessionCommittedAt());
                        ReservationSnapshotValidator.confirmed(op, confirmed);
                        transactions.finalizeConfirmed(op.getCommandId(), confirmed.terminalAt());
                        return "confirmed";
                    }
                    require(op.getRecovery().getIntent() == ReservationRecovery.Intent.CLEANUP_PRECOMMIT);
                    BillingReservationClient.ReservationSnapshot canceled = billing.cancel(op.getOperationId(),
                            op.getReservationId(), op.getUserId());
                    ReservationSnapshotValidator.canceled(op, canceled);
                    transactions.markCanceled(op.getCommandId(), canceled.terminalAt());
                    return "canceled";
                }
            }
            throw new IllegalStateException("Unreachable Reservation state");
        } catch (BillingClientException failure) {
            return handleBilling(lease, failure);
        } catch (UserOwnershipGuardException failure) {
            return quarantine(lease, ReservationRecovery.Status.BLOCKED_OWNER, "owner_blocked");
        } catch (ReservationRecoveryException failure) {
            return switch (failure.reason()) {
                case LEASE_LOST -> "lease_lost";
                case AUTH_BLOCKED -> { store.blockAuth(lease); yield "blocked_auth"; }
                case OWNER_BLOCKED -> quarantine(lease, ReservationRecovery.Status.BLOCKED_OWNER, "owner_blocked");
                case STATE_INCONSISTENT -> quarantine(lease, ReservationRecovery.Status.NEEDS_REVIEW, "state_inconsistent");
                case BUDGET_EXHAUSTED -> retry(lease, null);
            };
        } catch (IllegalStateException definiteMismatch) {
            return quarantine(lease, ReservationRecovery.Status.NEEDS_REVIEW, "state_inconsistent");
        } catch (RuntimeException transactionOrStorageFailure) {
            // Observe fresh evidence, including wrapped unknown-commit failures. Never compensate from an exception.
            ExamCreationOperation observed = execution.freshOperation(lease.id());
            if (observed != null && observed.isTerminal()) {
                if (observed.getState() == ExamCreationState.SUCCEEDED) {
                    ReservationSnapshotValidator.session(observed, execution.freshSession(observed.getSessionId()));
                }
                return "noop";
            }
            return retry(lease, null);
        }
    }

    private String handleBilling(ReservationOperationExecution.Lease lease, BillingClientException failure) {
        return switch (failure.category()) {
            case AUTH_FAILURE -> { circuit.block(); store.blockAuth(lease); yield "blocked_auth"; }
            case CONTRACT_ERROR, INVALID_REQUEST, IDEMPOTENCY_CONFLICT, ENTITLEMENT_INSUFFICIENT ->
                    quarantine(lease, ReservationRecovery.Status.NEEDS_REVIEW, "state_inconsistent");
            default -> retry(lease, failure.retryAfterSeconds());
        };
    }

    private String retry(ReservationOperationExecution.Lease lease, Integer after) {
        store.retry(lease, after);
        return "retry_scheduled";
    }

    private String quarantine(ReservationOperationExecution.Lease lease, ReservationRecovery.Status status, String reason) {
        store.quarantine(lease, status, reason);
        return status == ReservationRecovery.Status.BLOCKED_OWNER ? "blocked_owner" : "needs_review";
    }

    private void requireOwner(ExamCreationOperation op) {
        WithdrawnUserAccessDeny deny = mongo.findById(op.getUserId(), WithdrawnUserAccessDeny.class);
        UserOwnershipGuard guard = mongo.findById(op.getUserId(), UserOwnershipGuard.class);
        if ((deny != null && deny.isActiveAt(execution.now())) || (guard != null && guard.isMerged())) {
            throw new ReservationRecoveryException(ReservationRecoveryException.Reason.OWNER_BLOCKED);
        }
    }

    /** Circuit lease, not an operation lease: this probe is strictly read-only. */
    public void probeAuth() {
        if (!properties.isEnabled() || !circuit.blocked()) return;
        String token = circuit.claimProbe();
        if (token == null) return;
        ExamCreationOperation op = mongo.findOne(Query.query(Criteria.where("activeGuard").is(true)
                .and("recovery.schemaVersion").is(1)).limit(1), ExamCreationOperation.class);
        if (op == null) return;
        try {
            ReservationSnapshotValidator.status(op, billing.status(op.getUserId(), op.getOperationId()));
            circuit.recovered(token);
        } catch (BillingClientException failure) {
            if (failure.category() == BillingClientException.Category.OPERATION_NOT_FOUND) circuit.recovered(token);
        }
    }
}
