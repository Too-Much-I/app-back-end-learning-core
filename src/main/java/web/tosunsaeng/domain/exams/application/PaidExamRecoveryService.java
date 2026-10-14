package web.tosunsaeng.domain.exams.application;

import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.exams.billing.*;
import web.tosunsaeng.domain.exams.billing.reconciliation.*;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.enums.ExamCreationState;
import java.time.ZoneId;
import java.util.Objects;
import static web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.require;

/** Shared paid status-first pass. Not wired to public routes or scheduled until remaining rollout gates. */
@Service
public class PaidExamRecoveryService {
    private final ReservationOperationExecution execution;
    private final PaidExamLifecycleService lifecycle;
    private final BillingReservationClient billing;
    private final ReservationRecoveryStore store;
    private final ReservationAuthCircuit circuit;

    public PaidExamRecoveryService(ReservationOperationExecution execution,PaidExamLifecycleService lifecycle,
            BillingReservationClient billing,ReservationRecoveryStore store,ReservationAuthCircuit circuit) {
        this.execution=execution; this.lifecycle=lifecycle; this.billing=billing; this.store=store; this.circuit=circuit;
    }

    public ExamCreationOperation resumeHttp(String user,String id,ZoneId zone) {
        ExamCreationOperation existing=execution.freshOperation(id);
        require(existing!=null && Objects.equals(existing.getUserId(),user) && existing.getPaidSelection()!=null);
        if (existing.isTerminal()) return lifecycle.replay(user,id);
        return execution.httpPaid(id,()-> {
            var op=execution.freshOperation(id);
            if (op.getState()==ExamCreationState.PREPARED && op.getRecovery().getDispatch()==ReservationRecovery.Dispatch.NOT_DISPATCHED) {
                op=lifecycle.beforeReserve(user,id);
                return reserve(user,id,zone,op);
            }
            return pass(user,id,zone,false);
        });
    }

    public void runWorker(String id) {
        var lease=execution.claimPaidWorker(id);
        if (lease==null) return;
        try {
            execution.within(lease,()-> {
                var op=execution.freshOperation(id);
                require(op!=null && op.getPaidSelection()!=null);
                if (!store.canStartAttempt(lease,op)) return null;
                try {
                    var result=pass(op.getUserId(),id,ZoneId.of("UTC"),true);
                    if (!result.isTerminal() && result.getRecovery().getStatus()!=ReservationRecovery.Status.NEEDS_REVIEW
                            && !(result.getState()==ExamCreationState.PREPARED && result.getRecovery().getMemberWait()!=null
                            && result.getRecovery().getMemberWait().phase()==MemberWaitEvidence.Phase.WAITING))
                        chargedRetry(lease,op.getUserId(),1);
                } catch (BillingClientException failure) {
                    switch (failure.category()) {
                        case AUTH_FAILURE -> { circuit.block(); store.blockAuth(lease); }
                        case OWNER_BLOCKED -> store.quarantine(lease,ReservationRecovery.Status.BLOCKED_OWNER,"owner_blocked");
                        case OPERATION_NOT_FOUND, TEMPORARILY_UNAVAILABLE, RATE_LIMITED, PROCESSING -> chargedRetry(lease,op.getUserId(),failure.retryAfterSeconds());
                        default -> store.quarantine(lease,ReservationRecovery.Status.NEEDS_REVIEW,"state_inconsistent");
                    }
                } catch (ReservationRecoveryException failure) {
                    if (failure.reason()==ReservationRecoveryException.Reason.OWNER_BLOCKED)
                        store.quarantine(lease,ReservationRecovery.Status.BLOCKED_OWNER,"owner_blocked");
                    else if (failure.reason()==ReservationRecoveryException.Reason.AUTH_BLOCKED) store.blockAuth(lease);
                    else if (failure.reason()==ReservationRecoveryException.Reason.STATE_INCONSISTENT)
                        store.quarantine(lease,ReservationRecovery.Status.NEEDS_REVIEW,"state_inconsistent");
                    else if (failure.reason()==ReservationRecoveryException.Reason.BUDGET_EXHAUSTED) chargedRetry(lease,op.getUserId(),1);
                    else throw failure;
                } catch (web.tosunsaeng.domain.usermerge.application.UserOwnedCommitOutcomeUnknownException
                         | ReservationCommitOutcomeUnknownException unknown) {
                    chargedRetry(lease,op.getUserId(),1); // Fresh majority observation; never assume rollback or cancel.
                } catch (web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardException denied) {
                    store.quarantine(lease,ReservationRecovery.Status.BLOCKED_OWNER,"owner_blocked");
                }
                return null;
            });
        } finally {
            try { execution.release(lease); } catch (RuntimeException ignored) { /* Lease expiry preserves evidence. */ }
        }
    }

    private ExamCreationOperation pass(String user,String id,ZoneId zone,boolean worker) {
        execution.beforeRemote(id);
        ExamCreationOperation op=execution.freshOperation(id);
        require(op!=null && Objects.equals(user,op.getUserId()) && op.getPaidSelection()!=null && op.getPaidRequestDigest()!=null);
        BillingReservationClient.ReservationSnapshot status;
        try { status=billing.statusPaid(user,op.getOperationId()); }
        catch (BillingClientException failure) {
            if (failure.category()==BillingClientException.Category.MEMBER_EVIDENCE_PENDING) {
                var waiting=lifecycle.observeMemberWait(user,id);
                if (worker) return waiting;
                if (!execution.now().isBefore(waiting.getCreatedAt().plus(java.time.Duration.ofDays(120))))
                    throw new web.tosunsaeng.domain.exams.exception.ExamsException(web.tosunsaeng.global.error.code.status.ErrorStatus._EXAM_CREATION_PROCESSING,1);
                var resumed=lifecycle.beforeMemberResume(user,id,waiting.getVersion());
                return reserve(user,id,zone,resumed);
            }
            if (failure.category()==BillingClientException.Category.PRE_RESERVE_REJECTED)
                return lifecycle.rejectBeforeCreation(user,id);
            if (!worker && op.getRecovery().getMemberWait()!=null)
                lifecycle.interruptMemberWait(user,id);
            throw failure;
        }
        op=lifecycle.applyStatus(user,id,status);
        if (op.isTerminal()) return op;
        if (op.getState()==ExamCreationState.RESERVED) {
            if (worker) return op; // Workers never create Sessions, reserve, discover or select rights.
            op=lifecycle.commitPending(user,id,zone);
        }
        return confirm(user,id,op);
    }

    private ExamCreationOperation confirm(String user,String id,ExamCreationOperation op) {
        if (op.getState()==ExamCreationState.SESSION_COMMITTED) {
            op=lifecycle.beforeConfirm(user,id);
            execution.beforeRemote(id);
            // Even definitive rejection must be recovered from a durable full status snapshot.
            try {
                var confirmed=billing.confirmPaid(op.getOperationId(),op.getReservationId(),user,op.getSessionId(),op.getSessionCommittedAt());
                ReservationSnapshotValidator.confirmed(op,confirmed);
            } catch (BillingClientException failure) {
                if (failure.category()!=BillingClientException.Category.ENTITLEMENT_REVOKED) throw failure;
            }
        }
        // Next pass observes CONFIRMED/CANCELED; at most status + confirm per worker attempt.
        return execution.freshOperation(id);
    }

    private ExamCreationOperation reserve(String user,String id,ZoneId zone,ExamCreationOperation op) {
        execution.beforeRemote(id);
        try {
            var reserved=op.isPhoneContinuation()?billing.reservePaidPhone(op.getOperationId(),PaidReservationProtocol.phoneRequest(op))
                    :billing.reservePaid(op.getOperationId(),PaidReservationProtocol.request(op));
            lifecycle.acceptReserved(user,id,reserved);
        } catch (BillingClientException failure) {
            if (failure.category()!=BillingClientException.Category.MEMBER_EVIDENCE_PENDING) throw failure;
            lifecycle.observeMemberWait(user,id);
            throw new web.tosunsaeng.domain.exams.exception.ExamsException(web.tosunsaeng.global.error.code.status.ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE,1);
        }
        return confirm(user,id,lifecycle.commitPending(user,id,zone));
    }

    private void chargedRetry(ReservationOperationExecution.Lease lease,String user,Integer after) {
        var fresh=execution.freshOperation(lease.id());
        if (fresh==null || fresh.isTerminal()) return;
        if (fresh.getRecovery().getMemberWait()!=null) lifecycle.interruptMemberWait(user,lease.id());
        if (store.startAttempt(lease,execution.freshOperation(lease.id()))) store.retry(lease,after);
    }
}
