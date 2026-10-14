package web.tosunsaeng.domain.exams.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationSnapshot;
import web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus;
import web.tosunsaeng.domain.exams.billing.PaidReservationProtocol;
import web.tosunsaeng.domain.exams.billing.reconciliation.*;
import web.tosunsaeng.domain.exams.domain.entity.*;
import web.tosunsaeng.domain.exams.domain.enums.*;
import web.tosunsaeng.domain.exams.domain.repository.ExamCreationOperationRepository;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventProperties;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupProjectionStatus;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import java.time.*;
import java.util.Objects;
import java.util.function.Function;
import static web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.require;

/** Paid-only local transactions. Caller holds the shared execution lease; never performs HTTP. */
@Service
public class PaidExamLifecycleService {
    private final MongoTemplate mongo;
    private final ExamCreationOperationRepository operations;
    private final ObjectProvider<UserOwnedTransactionExecutor> owners;
    private final ReservationOperationExecution execution;
    private final AttemptGroupEventProperties groups;

    public PaidExamLifecycleService(MongoTemplate mongo, ExamCreationOperationRepository operations,
            ObjectProvider<UserOwnedTransactionExecutor> owners, ReservationOperationExecution execution,
            AttemptGroupEventProperties groups) {
        this.mongo=mongo; this.operations=operations; this.owners=owners; this.execution=execution; this.groups=groups;
    }

    public ExamCreationOperation acceptReserved(String user, String id, ReservationSnapshot snapshot) {
        return mutate(user,id,op -> {
            require(snapshot.reservationStatus()==ReservationStatus.RESERVED);
            require(op.getState()==ExamCreationState.PREPARED || op.getState()==ExamCreationState.RESERVED);
            requireNoSession(op);
            op.observePaidReservation(snapshot);
            resolveMemberWait(op);
            if (op.getState()==ExamCreationState.PREPARED)
                op.markReserved(snapshot.reservationId(),snapshot.reservationKind(),snapshot.attemptGroupId(),snapshot.expiresAt(),execution.now());
            return save(op);
        });
    }

    public ExamCreationOperation beforeConfirm(String user,String id) {
        return mutate(user,id,op -> {
            require(op.getState()==ExamCreationState.SESSION_COMMITTED);
            pending(op);
            require(op.getPaidReservation().snapshot().reservationStatus()==ReservationStatus.RESERVED);
            op.getRecovery().setConfirmDispatch(ReservationRecovery.Dispatch.MAY_HAVE_BEEN_SENT);
            return save(op);
        });
    }

    public ExamCreationOperation beforeReserve(String user,String id) {
        return mutate(user,id,op -> {
            require(op.getState()==ExamCreationState.PREPARED && op.getReservationId()==null && op.getPaidReservation()==null
                    && op.getRecovery().getDispatch()==ReservationRecovery.Dispatch.NOT_DISPATCHED);
            requireNoSession(op);
            op.getRecovery().setDispatch(ReservationRecovery.Dispatch.MAY_HAVE_BEEN_SENT);
            return save(op);
        });
    }

    public ExamCreationOperation observeMemberWait(String user,String id) {
        return mutate(user,id,true,op -> {
            memberSafety(op);
            var r=op.getRecovery(); var wait=r.getMemberWait();
            if (wait==null) wait=MemberWaitEvidence.first(op.getOperationId(),op.getPaidRequestDigest(),execution.now());
            else { require(wait.bound(op.getOperationId(),op.getPaidRequestDigest())); wait=wait.observed(execution.now()); }
            r.setMemberWait(wait);
            if (!execution.now().isBefore(op.getCreatedAt().plus(Duration.ofDays(120)))) {
                r.setStatus(ReservationRecovery.Status.NEEDS_REVIEW); r.setFailureCode("member_resume_window_expired");
                if (r.getAlertIncidentId()==null) {
                    r.setAlertIncidentId(java.util.UUID.randomUUID().toString());
                    r.setAlertReason("state_inconsistent"); r.setAlertStatus("PENDING");
                    r.setAlertCreatedAt(execution.now()); r.setAlertNextAt(execution.now());
                }
            } else if (r.getStatus()!=ReservationRecovery.Status.NEEDS_REVIEW) r.setIntent(ReservationRecovery.Intent.CONTINUE);
            r.setNextAt(execution.now().plusSeconds(60+java.util.concurrent.ThreadLocalRandom.current().nextInt(1,6)));
            return operations.save(op);
        });
    }

    public ExamCreationOperation beforeMemberResume(String user,String id,Long observedVersion) {
        require(!execution.worker());
        require(observedVersion!=null);
        return mutate(user,id,true,observedVersion,op -> {
            memberSafety(op);
            var r=op.getRecovery(); var wait=r.getMemberWait();
            require(wait!=null && wait.bound(op.getOperationId(),op.getPaidRequestDigest()) && wait.phase()==MemberWaitEvidence.Phase.WAITING
                    && execution.now().isBefore(op.getCreatedAt().plus(Duration.ofDays(120))));
            boolean review=r.getStatus()==ReservationRecovery.Status.NEEDS_REVIEW;
            if (review) {
                require(wait.reviewEligible(op.getOperationId(),op.getPaidRequestDigest(),r.getFailureCode())
                        && wait.exhaustion().chargedAttempts()==r.getAttempts()
                        && Objects.equals(wait.exhaustion().firstAttemptAt(),r.getFirstAt()));
                // Only historically charged pure MEMBER attempts are reset; original values stay in the proof.
                r.setAttempts(0); r.setFirstAt(null); r.setStatus(ReservationRecovery.Status.IN_FLIGHT);
            }
            r.setIntent(ReservationRecovery.Intent.CONTINUE);
            r.setMemberWait(wait.leave(MemberWaitEvidence.Phase.RESUMING,execution.now(),review));
            r.setDispatch(ReservationRecovery.Dispatch.MAY_HAVE_BEEN_SENT);
            return save(op);
        });
    }

    public ExamCreationOperation interruptMemberWait(String user,String id) {
        return mutate(user,id,true,op -> {
            if (op.getRecovery().getMemberWait()!=null)
                op.getRecovery().setMemberWait(op.getRecovery().getMemberWait().leave(MemberWaitEvidence.Phase.INTERRUPTED,execution.now(),false));
            return operations.save(op);
        });
    }

    private void memberSafety(ExamCreationOperation op) {
        var r=op.getRecovery();
        require(op.getState()==ExamCreationState.PREPARED && Boolean.TRUE.equals(op.getActiveGuard())
                && op.getReservationId()==null && op.getAttemptGroupId()==null && op.getPaidReservation()==null
                && op.getSessionCommittedAt()==null && r.getConfirmDispatch()==ReservationRecovery.Dispatch.NOT_DISPATCHED
                && r.getCancelDispatch()==ReservationRecovery.Dispatch.NOT_DISPATCHED
                && (r.getStatus()==ReservationRecovery.Status.IN_FLIGHT || r.getStatus()==ReservationRecovery.Status.NEEDS_REVIEW));
        requireNoSession(op);
    }

    /** Called only for strict status-only RESERVE_REJECTED_BEFORE_CREATION evidence. */
    public ExamCreationOperation rejectBeforeCreation(String user,String id) {
        return mutate(user,id,op -> {
            require(op.getState()==ExamCreationState.PREPARED && op.getReservationId()==null
                    && op.getPaidReservation()==null && op.getAttemptGroupId()==null && op.getSessionCommittedAt()==null
                    && op.getRecovery().getConfirmDispatch()==ReservationRecovery.Dispatch.NOT_DISPATCHED);
            requireNoSession(op);
            op.markFailedTerminal("RESERVE_REJECTED_BEFORE_CREATION",execution.now(),execution.now().plus(Duration.ofDays(120)));
            resolveMemberWait(op);
            op.completeRecovery(execution.now());
            return operations.save(op);
        });
    }

    /** Terminal replay checks current actor/lifecycle before returning old results; never recreates data. */
    public ExamCreationOperation replay(String user,String id) {
        UserOwnedTransactionExecutor owner=owners.getIfAvailable();
        require(owner!=null && owner.enabled());
        return owner.execute(user,()-> {
            owner.requirePublicWrite(user); execution.requireOwner(user);
            ExamCreationOperation op=operations.findById(id).orElseThrow();
            require(Objects.equals(user,op.getUserId()) && op.getPaidSelection()!=null && op.isTerminal());
            if (op.getState()==ExamCreationState.SUCCEEDED) {
                ExamSession session=mongo.findById(op.getSessionId(),ExamSession.class);
                ReservationSnapshotValidator.session(op,session);
                require(session.getEntitlementState()==ExamEntitlementState.CONFIRMED && !session.isAbandoned());
            }
            return op;
        });
    }

    public ExamCreationOperation commitPending(String user, String id, ZoneId zone) {
        return mutate(user,id,op -> {
            if (op.getState()==ExamCreationState.SESSION_COMMITTED) { pending(op); return op; }
            require(op.getState()==ExamCreationState.RESERVED && op.getPaidReservation()!=null
                    && op.getPaidReservation().snapshot().reservationStatus()==ReservationStatus.RESERVED);
            requireNoSession(op);
            previous(op); // Check exact target without closing it before confirm.
            requireNoOtherActive(op);
            ExamSession session=ExamSession.builder().examId(op.getSessionId()).userId(user)
                    .createdAt(LocalDateTime.ofInstant(op.getCreatedAt(),zone)).mockExamId(op.getMockExamId())
                    .cycleNumber(op.getCycleNumber()).status(ExamSessionStatus.ENTITLEMENT_CONFIRMING).active(false)
                    .creationOperationId(op.getOperationId()).billingReservationId(op.getReservationId())
                    .billingReservationKind(op.getReservationKind()).attemptGroupId(op.getAttemptGroupId())
                    .entitlementState(ExamEntitlementState.CONFIRMING).paidReservation(op.getPaidReservation())
                    .submissionEvidenceVersion(1).requiredQuestionNumbers(op.getPaidSelection().requiredQuestionNumbers())
                    .attemptGroupProjectionStatus(groups.writerEnabled()?AttemptGroupProjectionStatus.OPEN:null)
                    .attemptGroupProjectionVersion(groups.writerEnabled()?0L:null).build();
            mongo.insert(session);
            op.markSessionCommitted(execution.now());
            return save(op);
        });
    }

    /** Full authoritative status only. An abbreviated confirm response cannot erase immutable evidence. */
    public ExamCreationOperation applyStatus(String user, String id, ReservationSnapshot snapshot) {
        return mutate(user,id,op -> {
            require(!op.isTerminal());
            op.observePaidReservation(snapshot);
            resolveMemberWait(op);
            if (op.getState()==ExamCreationState.PREPARED)
                op.observeReservation(snapshot.reservationId(),snapshot.reservationKind(),snapshot.attemptGroupId(),snapshot.expiresAt());
            if (snapshot.reservationStatus()==ReservationStatus.RESERVED) {
                if (op.getState()==ExamCreationState.PREPARED) {
                    requireNoSession(op);
                    op.markReserved(snapshot.reservationId(),snapshot.reservationKind(),snapshot.attemptGroupId(),snapshot.expiresAt(),execution.now());
                }
                if (op.getState()==ExamCreationState.SESSION_COMMITTED) updatePendingEvidence(op);
                return save(op); // RESERVED is not cancellation or consumption evidence.
            }
            if (snapshot.reservationStatus()==ReservationStatus.CONFIRMED) {
                require(op.getState()==ExamCreationState.SESSION_COMMITTED && op.getSessionCommittedAt()!=null);
                ReservationSnapshotValidator.confirmed(op,snapshot);
                ExamSession fresh=pending(op);
                ExamSession old=previous(op);
                requireNoOtherActive(op);
                if (old!=null) {
                    old.closeSubmissions(op.getOperationId(),execution.now());
                    mongo.save(old); // Same owner/Session write fence as admission.
                }
                fresh.observePaidReservation(op.getPaidReservation());
                fresh.activatePaid(snapshot.terminalAt());
                mongo.save(fresh);
                op.markPaidSucceeded(snapshot.terminalAt(),execution.now());
            } else {
                require(snapshot.reservationStatus()==ReservationStatus.CANCELED || snapshot.reservationStatus()==ReservationStatus.EXPIRED);
                if (op.getState()==ExamCreationState.SESSION_COMMITTED) {
                    ExamSession session=pending(op);
                    session.observePaidReservation(op.getPaidReservation());
                    session.cancelPaidPending(); mongo.save(session);
                } else requireNoSession(op);
                Instant now=execution.now(), purge=now.plus(Duration.ofDays(120));
                if (snapshot.confirmRejection()!=null) op.markFailedTerminal("ENTITLEMENT_REVOKED",now,purge);
                else if (snapshot.reservationStatus()==ReservationStatus.CANCELED) op.markCanceled(now,purge);
                else op.markExpired(now,purge);
            }
            op.completeRecovery(execution.now());
            return operations.save(op);
        });
    }

    private void updatePendingEvidence(ExamCreationOperation op) {
        ExamSession session=pending(op); session.observePaidReservation(op.getPaidReservation()); mongo.save(session);
    }

    private ExamSession pending(ExamCreationOperation op) {
        ExamSession session=mongo.findById(op.getSessionId(),ExamSession.class);
        require(session!=null && Objects.equals(session.getUserId(),op.getUserId())
                && Objects.equals(session.getCreationOperationId(),op.getOperationId())
                && Objects.equals(session.getBillingReservationId(),op.getReservationId())
                && Objects.equals(session.getAttemptGroupId(),op.getAttemptGroupId())
                && Objects.equals(session.getMockExamId(),op.getMockExamId())
                && session.getBillingReservationKind()==op.getReservationKind()
                && session.getPaidReservation()!=null && session.isEntitlementConfirming()
                && session.getEntitlementState()==ExamEntitlementState.CONFIRMING
                && !session.isActive() && session.getSubmissionsClosedAt()==null && session.getCompletedAt()==null);
        session.getPaidReservation().observe(op.getPaidReservation().snapshot());
        return session;
    }

    private ExamSession previous(ExamCreationOperation op) {
        if (op.getReplacementSourceSessionId()==null) return null;
        require(!op.getReplacementSourceSessionId().equals(op.getSessionId()));
        ExamSession old=mongo.findById(op.getReplacementSourceSessionId(),ExamSession.class);
        require(old!=null && Objects.equals(old.getUserId(),op.getUserId())
                && Objects.equals(old.getAttemptGroupId(),op.getExpectedAttemptGroupId())
                && Objects.equals(old.getMockExamId(),op.getExpectedMockExamId()));
        require(old.getSubmissionsClosedAt()==null || Objects.equals(old.getSubmissionsClosedByOperationId(),op.getOperationId()));
        return old; // Keep terminal status/results; never resurrect or close a different current Session.
    }

    private void requireNoSession(ExamCreationOperation op) {
        require(!mongo.exists(Query.query(new Criteria().orOperator(Criteria.where("_id").is(op.getSessionId()),
                Criteria.where("userId").is(op.getUserId()).and("creationOperationId").is(op.getOperationId()))),ExamSession.class));
    }

    private void requireNoOtherActive(ExamCreationOperation op) {
        java.util.List<String> allowed=new java.util.ArrayList<>();
        allowed.add(op.getSessionId());
        if (op.getReplacementSourceSessionId()!=null) allowed.add(op.getReplacementSourceSessionId());
        require(!mongo.exists(Query.query(Criteria.where("userId").is(op.getUserId()).and("active").is(true)
                .and("_id").nin(allowed)),ExamSession.class));
    }

    private ExamCreationOperation save(ExamCreationOperation op) {
        op.getRecovery().setLastProgressAt(execution.now());
        op.getRecovery().setNextAt(execution.now().plusSeconds(10));
        return operations.save(op);
    }

    private void resolveMemberWait(ExamCreationOperation op) {
        var wait=op.getRecovery().getMemberWait();
        if (wait!=null) op.getRecovery().setMemberWait(wait.leave(MemberWaitEvidence.Phase.RESOLVED,execution.now(),false));
    }

    private ExamCreationOperation mutate(String user,String id,Function<ExamCreationOperation,ExamCreationOperation> action) {
        return mutate(user,id,false,action);
    }

    private ExamCreationOperation mutate(String user,String id,boolean memberInspection,Function<ExamCreationOperation,ExamCreationOperation> action) {
        return mutate(user,id,memberInspection,null,action);
    }

    private ExamCreationOperation mutate(String user,String id,boolean memberInspection,Long expectedVersion,Function<ExamCreationOperation,ExamCreationOperation> action) {
        UserOwnedTransactionExecutor owner=owners.getIfAvailable();
        require(owner!=null && owner.enabled());
        return owner.execute(user,()-> {
            owner.requirePublicWrite(user); execution.requireOwner(user);
            ExamCreationOperation op=execution.fence(id,expectedVersion);
            require(Objects.equals(user,op.getUserId()) && op.getPaidSelection()!=null && op.getPaidRequestDigest()!=null
                    && op.getRecovery().getSchemaVersion()==2
                    && (memberInspection || op.getRecovery().getIntent()==ReservationRecovery.Intent.CONTINUE));
            Object request=op.isPhoneContinuation()?PaidReservationProtocol.phoneRequest(op):PaidReservationProtocol.request(op);
            require(op.getPaidRequestDigest().equals(PaidReservationEvidenceStore.digest(request)));
            return action.apply(op);
        });
    }
}
