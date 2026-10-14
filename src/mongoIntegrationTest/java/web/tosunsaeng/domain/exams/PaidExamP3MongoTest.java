package web.tosunsaeng.domain.exams;

import com.mongodb.client.*;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.transaction.support.*;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import web.tosunsaeng.domain.exams.application.*;
import web.tosunsaeng.domain.exams.billing.*;
import web.tosunsaeng.domain.exams.billing.BillingReservationClient.*;
import web.tosunsaeng.domain.exams.billing.reconciliation.*;
import web.tosunsaeng.domain.exams.domain.entity.*;
import web.tosunsaeng.domain.exams.domain.enums.*;
import web.tosunsaeng.domain.exams.domain.repository.*;
import web.tosunsaeng.domain.usermerge.application.*;
import web.tosunsaeng.domain.usermerge.config.UserMergedProperties;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventProperties;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class PaidExamP3MongoTest {
    @Container static final MongoDBContainer MONGO=new MongoDBContainer("mongo:7.0.14");
    static MongoClient client;
    static final Instant NOW=Instant.parse("2026-10-14T00:00:00Z");
    static final String USER="30000000-0000-4000-8000-000000000001";
    static final String OLD_GROUP="30000000-0000-4000-8000-000000000002";
    static final String NEW_GROUP="30000000-0000-4000-8000-000000000003";
    static final String RESERVATION="30000000-0000-4000-8000-000000000004";
    MongoTemplate mongo;
    ExamCreationOperationRepository operations;
    ReservationOperationExecution execution;
    ReservationRecoveryStore recoveryStore;
    UserOwnedTransactionExecutor owners;
    PaidExamLifecycleService lifecycle;
    PaidExamRecoveryService recovery;
    BillingReservationClient billing;
    PaidReservationEvidenceStore evidence;
    ExamCreationOperation prepared;
    Clock recoveryClock;

    @BeforeAll static void connect() { client=MongoClients.create(MONGO.getReplicaSetUrl()); }
    @AfterAll static void close() { client.close(); }
    @BeforeEach void setup() {
        var factory=new SimpleMongoClientDatabaseFactory(client,"isolated-paid-p3");
        mongo=new MongoTemplate(factory);
        for (String name:List.of("exam_sessions","exam_creation_operations","user_ownership_guards","withdrawn_user_access_denies",
                "question_grading_jobs","exam_submission_receipts",ReservationAuthCircuit.COLLECTION)) {
            if (!mongo.collectionExists(name)) mongo.createCollection(name);
            mongo.remove(new Query(),name);
        }
        mongo.getCollection("exam_creation_operations").createIndex(new Document("userId",1).append("activeGuard",1),
                new IndexOptions().unique(true).partialFilterExpression(new Document("activeGuard",true)));
        var repos=new MongoRepositoryFactory(mongo);
        operations=repos.getRepository(ExamCreationOperationRepository.class);
        var beans=new StaticListableBeanFactory();
        beans.addBean("tx",new TransactionTemplate(new MongoTransactionManager(factory)));
        var props=new UserMergedProperties(); props.setWriterEnabled(true);
        owners=new UserOwnedTransactionExecutor(props,new UserOwnershipGuardService(mongo,repos.getRepository(UserOwnershipGuardRepository.class)),
                beans.getBeanProvider(TransactionOperations.class),Clock.fixed(NOW,ZoneOffset.UTC));
        beans.addBean("owners",owners);
        var recoveryProps=new ReservationReconciliationProperties();
        recoveryClock=mock(Clock.class); when(recoveryClock.instant()).thenReturn(NOW);
        execution=new ReservationOperationExecution(mongo,recoveryProps,recoveryClock);
        recoveryStore=new ReservationRecoveryStore(mongo,execution,recoveryProps);
        lifecycle=new PaidExamLifecycleService(mongo,operations,beans.getBeanProvider(UserOwnedTransactionExecutor.class),execution,mock(AttemptGroupEventProperties.class));
        billing=mock(BillingReservationClient.class);
        recovery=new PaidExamRecoveryService(execution,lifecycle,billing,recoveryStore,mock(ReservationAuthCircuit.class));
        evidence=new PaidReservationEvidenceStore(operations,mongo,beans.getBeanProvider(UserOwnedTransactionExecutor.class),Clock.fixed(NOW,ZoneOffset.UTC));
        mongo.insert(ExamSession.builder().examId("old").userId(USER).mockExamId("mock").attemptGroupId(OLD_GROUP)
                .status(ExamSessionStatus.IN_PROGRESS).active(true).submissionEvidenceVersion(1).requiredQuestionNumbers(List.of(1,2)).build());
        prepared=ExamCreationOperation.prepared(USER,UUID.randomUUID().toString(),"new","mock",1,"old",OLD_GROUP,"mock",NOW);
        prepared.fixPaidSelection(new PaidExamSelection(PaidExamSelection.StartReason.RESTART_INCOMPLETE,List.of(1,2)));
        operations.insert(prepared);
        evidence.prepareRequest(USER,prepared.getCommandId());
    }

    ReservationSnapshot snapshot(ReservationStatus status,boolean rejected) {
        return new ReservationSnapshot(prepared.getOperationId(),RESERVATION,BillingReservationKind.INITIAL,status,
                NEW_GROUP,status==ReservationStatus.CONFIRMED?AttemptGroupStatus.OPEN:null,"new","mock",null,null,
                NOW.plusSeconds(300),status==ReservationStatus.RESERVED?null:NOW.minusSeconds(1),
                PaidReservationProtocol.Source.SUBSCRIPTION,new PaidReservationProtocol.Transition(OLD_GROUP,"old"),
                rejected?new PaidReservationProtocol.ConfirmRejection("ENTITLEMENT_REVOKED"):null);
    }
    <T> T leased(Supplier<T> work) { return execution.http(prepared.getCommandId(),work); }
    ExamCreationOperation op() { return operations.findById(prepared.getCommandId()).orElseThrow(); }
    ExamSession session(String id) { return mongo.findById(id,ExamSession.class); }
    void pending() { leased(()-> {
        lifecycle.acceptReserved(USER,prepared.getCommandId(),snapshot(ReservationStatus.RESERVED,false));
        return lifecycle.commitPending(USER,prepared.getCommandId(),ZoneOffset.UTC);
    }); }

    void memberWaiting() {
        when(billing.reservePaid(anyString(),any())).thenThrow(new BillingClientException(BillingClientException.Category.MEMBER_EVIDENCE_PENDING,1));
        assertThatThrownBy(()->recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC)).isInstanceOf(RuntimeException.class);
        when(billing.statusPaid(USER,prepared.getOperationId())).thenThrow(new BillingClientException(BillingClientException.Category.MEMBER_EVIDENCE_PENDING,1));
    }
    void due() {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(prepared.getCommandId())),Update.update("recovery.nextAt",NOW),ExamCreationOperation.class);
    }
    @Test void memberWaitIsDurableWithoutCreatingSessionOrChargingWorkerAttempts() {
        memberWaiting();
        assertThat(op().getRecovery().getMemberWait().waitingSince()).isEqualTo(NOW);
        assertThat(op().getRecovery().getMemberWait().requestDigest()).isEqualTo(op().getPaidRequestDigest());
        for (int i=0;i<3;i++) { due(); recovery.runWorker(prepared.getCommandId()); }
        assertThat(op().getRecovery().getAttempts()).isZero();
        assertThat(op().getRecovery().getNextAt()).isBetween(NOW.plusSeconds(61),NOW.plusSeconds(65));
        assertThat(session("new")).isNull(); assertThat(session("old").isActive()).isTrue();
        verify(billing,times(1)).reservePaid(anyString(),any());
        verify(billing,times(3)).statusPaid(USER,prepared.getOperationId());
    }
    @Test void httpMemberResumePersistsMarkerAndUsesSameOperation() {
        memberWaiting();
        doAnswer(call->{
            assertThat(call.getArgument(0,String.class)).isEqualTo(prepared.getOperationId());
            assertThat(op().getRecovery().getMemberWait().phase()).isEqualTo(MemberWaitEvidence.Phase.RESUMING);
            return snapshot(ReservationStatus.RESERVED,false);
        }).when(billing).reservePaid(anyString(),any());
        when(billing.confirmPaid(anyString(),anyString(),anyString(),anyString(),any()))
                .thenThrow(new BillingClientException(BillingClientException.Category.TEMPORARILY_UNAVAILABLE,1));
        assertThatThrownBy(()->recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC)).isInstanceOf(BillingClientException.class);
        assertThat(op().getRecovery().getMemberWait().phase()).isEqualTo(MemberWaitEvidence.Phase.RESOLVED);
        assertThat(op().getState()).isEqualTo(ExamCreationState.SESSION_COMMITTED);
        assertThat(session("new").isActive()).isFalse();
    }
    @Test void auditedPureMemberExhaustionResumesOnlyOnceAndPreservesProof() {
        memberWaiting();
        var current=op(); var r=current.getRecovery(); var wait=r.getMemberWait();
        var proof=new MemberWaitEvidence.Exhaustion(current.getOperationId(),current.getPaidRequestDigest(),NOW,NOW,NOW,
                "MEMBER_WAIT_BUDGET_EXHAUSTED",false,200,NOW);
        r.setMemberWait(new MemberWaitEvidence(wait.operationId(),wait.requestDigest(),NOW,NOW,
                MemberWaitEvidence.Phase.WAITING,NOW,0,proof,null));
        r.setAttempts(200); r.setFirstAt(NOW); r.setStatus(ReservationRecovery.Status.NEEDS_REVIEW); r.setFailureCode("retry_exhausted");
        operations.save(current);
        assertThatThrownBy(()->recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC)).isInstanceOf(RuntimeException.class);
        assertThat(op().getRecovery().getMemberWait().resumedFromReviewAt()).isEqualTo(NOW);
        assertThat(op().getRecovery().getMemberWait().exhaustion()).isEqualTo(proof);
        assertThat(op().getRecovery().getAttempts()).isZero();
        verify(billing,times(2)).reservePaid(anyString(),any());
        current=op(); current.getRecovery().setStatus(ReservationRecovery.Status.NEEDS_REVIEW);
        operations.save(current); clearInvocations(billing);
        assertThatThrownBy(()->recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(billing);
    }
    @Test void safeCleanupPendingResetsIntentButStaleObservationCannotResume() {
        memberWaiting();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(prepared.getCommandId())),
                Update.update("recovery.intent",ReservationRecovery.Intent.CLEANUP_PRECOMMIT),ExamCreationOperation.class);
        leased(()-> {
            var observed=lifecycle.observeMemberWait(USER,prepared.getCommandId());
            lifecycle.observeMemberWait(USER,prepared.getCommandId());
            assertThatThrownBy(()->lifecycle.beforeMemberResume(USER,prepared.getCommandId(),observed.getVersion()))
                    .isInstanceOf(ReservationRecoveryException.class);
            return null;
        });
        assertThat(op().getRecovery().getIntent()).isEqualTo(ReservationRecovery.Intent.CONTINUE);
        verify(billing,times(1)).reservePaid(anyString(),any());
    }
    @Test void pureWaitPausesAgeBudgetButLaterFaultTimeStillExhaustsIt() {
        memberWaiting();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(prepared.getCommandId())),new Update()
                .set("recovery.firstAt",NOW.minus(Duration.ofHours(23))).set("recovery.attempts",1),ExamCreationOperation.class);
        when(recoveryClock.instant()).thenReturn(NOW.plus(Duration.ofDays(2)));
        recovery.runWorker(prepared.getCommandId());
        assertThat(op().getRecovery().getStatus()).isEqualTo(ReservationRecovery.Status.READY);
        assertThat(op().getRecovery().getAttempts()).isEqualTo(1);
        doThrow(new BillingClientException(BillingClientException.Category.TEMPORARILY_UNAVAILABLE,1)).when(billing).statusPaid(USER,prepared.getOperationId());
        due(); recovery.runWorker(prepared.getCommandId());
        assertThat(op().getRecovery().getAttempts()).isEqualTo(2);
        when(recoveryClock.instant()).thenReturn(NOW.plus(Duration.ofDays(2)).plus(Duration.ofHours(2)));
        recovery.runWorker(prepared.getCommandId());
        assertThat(op().getRecovery().getStatus()).isEqualTo(ReservationRecovery.Status.NEEDS_REVIEW);
        assertThat(op().getRecovery().getFailureCode()).isEqualTo("retry_exhausted");
        assertThat(op().getRecovery().getMemberWait().exhaustion()).isNull();
    }
    @Test void memberWaitCannotBypassAuthCircuitOrConcurrentLease() {
        memberWaiting(); clearInvocations(billing);
        mongo.insert(new Document("_id",ReservationAuthCircuit.ID).append("blocked",true),ReservationAuthCircuit.COLLECTION);
        assertThatThrownBy(()->recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC)).isInstanceOf(web.tosunsaeng.domain.exams.exception.ExamsException.class);
        verifyNoInteractions(billing);
        mongo.remove(new Query(),ReservationAuthCircuit.COLLECTION);
        var lease=execution.claimPaidWorker(prepared.getCommandId()); // Not due yet.
        assertThat(lease).isNull();
        due(); lease=execution.claimPaidWorker(prepared.getCommandId());
        assertThat(lease).isNotNull();
        assertThatThrownBy(()->recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(billing); execution.release(lease);
    }
    @Test void actualWorkerFailureChargesBudgetAndClosesWaitInterval() {
        memberWaiting(); due();
        doThrow(new BillingClientException(BillingClientException.Category.TEMPORARILY_UNAVAILABLE,1)).when(billing).statusPaid(USER,prepared.getOperationId());
        recovery.runWorker(prepared.getCommandId());
        assertThat(op().getRecovery().getAttempts()).isEqualTo(1);
        assertThat(op().getRecovery().getMemberWait().phase()).isEqualTo(MemberWaitEvidence.Phase.INTERRUPTED);
        assertThat(op().getRecovery().getFirstAt()).isEqualTo(NOW);
    }
    @Test void genericExhaustionCannotUseMemberPendingToEscapeReview() {
        memberWaiting(); clearInvocations(billing);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(prepared.getCommandId())),new Update()
                .set("recovery.status",ReservationRecovery.Status.NEEDS_REVIEW).set("recovery.failureCode","retry_exhausted"),ExamCreationOperation.class);
        assertThatThrownBy(()->recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(billing);
        assertThat(op().getRecovery().getStatus()).isEqualTo(ReservationRecovery.Status.NEEDS_REVIEW);
        assertThat(op().getActiveGuard()).isTrue();
    }
    @Test void memberPendingCannotEraseCancelDispatchEvidence() {
        memberWaiting(); clearInvocations(billing);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(prepared.getCommandId())),
                Update.update("recovery.cancelDispatch",ReservationRecovery.Dispatch.MAY_HAVE_BEEN_SENT),ExamCreationOperation.class);
        assertThatThrownBy(()->recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC)).isInstanceOf(RuntimeException.class);
        verify(billing,never()).reservePaid(anyString(),any());
        assertThat(op().getRecovery().getCancelDispatch()).isEqualTo(ReservationRecovery.Dispatch.MAY_HAVE_BEEN_SENT);
    }
    @Test void memberWindowExact120DaysKeepsGuardAndRequiresReview() {
        memberWaiting(); clearInvocations(billing);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(prepared.getCommandId())),
                Update.update("createdAt",NOW.minus(Duration.ofDays(120))),ExamCreationOperation.class);
        assertThatThrownBy(()->recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC)).isInstanceOf(RuntimeException.class);
        verify(billing,never()).reservePaid(anyString(),any());
        assertThat(op().getRecovery().getStatus()).isEqualTo(ReservationRecovery.Status.NEEDS_REVIEW);
        assertThat(op().getPurgeAt()).isNull(); assertThat(op().getActiveGuard()).isTrue();
    }
    @Test void status404InterruptsPureWaitWithoutManufacturingMemberProof() {
        memberWaiting();
        doThrow(new BillingClientException(BillingClientException.Category.OPERATION_NOT_FOUND,1)).when(billing).statusPaid(USER,prepared.getOperationId());
        assertThatThrownBy(()->recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC)).isInstanceOf(BillingClientException.class);
        assertThat(op().getRecovery().getMemberWait().phase()).isEqualTo(MemberWaitEvidence.Phase.INTERRUPTED);
        assertThat(op().getRecovery().getMemberWait().exhaustion()).isNull();
    }
    @Test void reserveDoesNotCloseOldAndConfirmAtomicallyActivatesNewWith120DayReceipt() {
        pending();
        assertThat(session("old").isActive()).isTrue(); assertThat(session("old").getSubmissionsClosedAt()).isNull();
        assertThat(session("new").isActive()).isFalse(); assertThat(session("new").isEntitlementConfirming()).isTrue();
        assertThat(op().getActiveGuard()).isTrue(); assertThat(op().getPurgeAt()).isNull();
        leased(()->lifecycle.applyStatus(USER,prepared.getCommandId(),snapshot(ReservationStatus.CONFIRMED,false)));
        assertThat(session("old").isInProgress()).isTrue(); // Existing grading is not ABANDONED.
        assertThat(session("old").getSubmissionsClosedAt()).isEqualTo(NOW);
        assertThat(session("new").isInProgress()).isTrue(); assertThat(session("new").isActive()).isTrue();
        assertThat(op().getState()).isEqualTo(ExamCreationState.SUCCEEDED);
        assertThat(op().getActiveGuard()).isFalse(); assertThat(op().getPurgeAt()).isEqualTo(NOW.plus(Duration.ofDays(120)));
        assertThat(op().getConfirmedAt()).isEqualTo(NOW.minusSeconds(1));
        var replay=lifecycle.replay(USER,prepared.getCommandId());
        assertThat(replay.getTerminalAt()).isEqualTo(NOW); assertThat(replay.getPurgeAt()).isEqualTo(op().getPurgeAt());
    }
    @Test void revokedStatusCompensatesOnlyPendingAndPreservesOldAndFailureReplay() {
        pending();
        leased(()->lifecycle.applyStatus(USER,prepared.getCommandId(),snapshot(ReservationStatus.CANCELED,true)));
        assertThat(op().getState()).isEqualTo(ExamCreationState.FAILED_TERMINAL);
        assertThat(op().getFailureCategory()).isEqualTo("ENTITLEMENT_REVOKED");
        assertThat(op().getPurgeAt()).isEqualTo(NOW.plus(Duration.ofDays(120)));
        assertThat(session("new").isAbandoned()).isTrue();
        assertThat(session("old").isActive()).isTrue(); assertThat(session("old").getSubmissionsClosedAt()).isNull();
        assertThat(lifecycle.replay(USER,prepared.getCommandId()).getFailureCategory()).isEqualTo("ENTITLEMENT_REVOKED");
    }
    @Test void wrongOwnerOrDeletedNewSessionCannotResurrectOrPartiallyCloseOld() {
        pending();
        mongo.updateFirst(Query.query(Criteria.where("_id").is("new")),Update.update("userId",UUID.randomUUID().toString()),ExamSession.class);
        assertThatThrownBy(()->leased(()->lifecycle.applyStatus(USER,prepared.getCommandId(),snapshot(ReservationStatus.CONFIRMED,false))))
                .isInstanceOf(RuntimeException.class);
        assertThat(op().getState()).isEqualTo(ExamCreationState.SESSION_COMMITTED);
        assertThat(op().getPaidReservation().snapshot().reservationStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(session("old").getSubmissionsClosedAt()).isNull();
        mongo.remove(Query.query(Criteria.where("_id").is("new")),ExamSession.class);
        assertThatThrownBy(()->leased(()->lifecycle.applyStatus(USER,prepared.getCommandId(),snapshot(ReservationStatus.CONFIRMED,false))))
                .isInstanceOf(RuntimeException.class);
        assertThat(session("new")).isNull(); assertThat(op().getActiveGuard()).isTrue();
    }
    @Test void terminalOldIsNeverRevived() {
        pending();
        mongo.updateFirst(Query.query(Criteria.where("_id").is("old")),new Update().set("status",ExamSessionStatus.COMPLETED)
                .set("active",false).set("completedAt",LocalDateTime.ofInstant(NOW,ZoneOffset.UTC)),ExamSession.class);
        leased(()->lifecycle.applyStatus(USER,prepared.getCommandId(),snapshot(ReservationStatus.CONFIRMED,false)));
        assertThat(session("old").isCompleted()).isTrue(); assertThat(session("old").isActive()).isFalse();
    }
    @Test void unrelatedCurrentSessionBlocksActivationInsteadOfClosingTheWrongTarget() {
        pending();
        mongo.insert(ExamSession.builder().examId("unrelated").userId(USER).status(ExamSessionStatus.IN_PROGRESS).active(true).build());
        assertThatThrownBy(()->leased(()->lifecycle.applyStatus(USER,prepared.getCommandId(),snapshot(ReservationStatus.CONFIRMED,false))))
                .isInstanceOf(RuntimeException.class);
        assertThat(session("unrelated").isActive()).isTrue(); assertThat(session("unrelated").getSubmissionsClosedAt()).isNull();
        assertThat(session("old").isActive()).isTrue(); assertThat(session("new").isActive()).isFalse();
        assertThat(op().getActiveGuard()).isTrue();
    }
    @Test void fullStatusCannotFinalizeWithoutCommittedSessionOrWithDifferentSource() {
        assertThatThrownBy(()->leased(()->lifecycle.applyStatus(USER,prepared.getCommandId(),snapshot(ReservationStatus.CONFIRMED,false))))
                .isInstanceOf(RuntimeException.class);
        assertThat(op().getState()).isEqualTo(ExamCreationState.PREPARED); assertThat(session("new")).isNull();
        pending();
        var s=snapshot(ReservationStatus.CONFIRMED,false);
        var wrong=new ReservationSnapshot(s.operationId(),s.reservationId(),s.reservationKind(),s.reservationStatus(),s.attemptGroupId(),
                s.attemptGroupStatus(),s.sessionId(),s.mockExamId(),null,null,s.expiresAt(),s.terminalAt(),PaidReservationProtocol.Source.EXAM_PASS,s.transition(),null);
        assertThatThrownBy(()->leased(()->lifecycle.applyStatus(USER,prepared.getCommandId(),wrong))).isInstanceOf(RuntimeException.class);
        assertThat(session("old").getSubmissionsClosedAt()).isNull(); assertThat(op().getActiveGuard()).isTrue();
    }
    @Test void lostConfirmResponseRecoversFromStatusWithoutAnotherReserveOrCancel() {
        when(billing.reservePaid(anyString(),any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(op().getRecovery().getDispatch()).isEqualTo(ReservationRecovery.Dispatch.MAY_HAVE_BEEN_SENT);
            return snapshot(ReservationStatus.RESERVED,false);
        });
        when(billing.confirmPaid(anyString(),anyString(),anyString(),anyString(),any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(op().getRecovery().getConfirmDispatch()).isEqualTo(ReservationRecovery.Dispatch.MAY_HAVE_BEEN_SENT);
            throw new BillingClientException(BillingClientException.Category.TEMPORARILY_UNAVAILABLE,1);
        });
        assertThatThrownBy(()->recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC)).isInstanceOf(BillingClientException.class);
        when(billing.statusPaid(USER,prepared.getOperationId())).thenReturn(snapshot(ReservationStatus.CONFIRMED,false));
        assertThat(recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC).getState()).isEqualTo(ExamCreationState.SUCCEEDED);
        assertThat(recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC).getState()).isEqualTo(ExamCreationState.SUCCEEDED);
        verify(billing,times(1)).reservePaid(anyString(),any()); verify(billing,times(1)).confirmPaid(anyString(),anyString(),anyString(),anyString(),any());
        verify(billing,times(1)).statusPaid(USER,prepared.getOperationId()); verifyNoMoreInteractions(billing);
    }
    @Test void workerStatusOnlyDoesNotCreateSessionReserveOrCancelAnd404KeepsGuard() {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(prepared.getCommandId())),Update.update("recovery.nextAt",NOW),ExamCreationOperation.class);
        when(billing.statusPaid(USER,prepared.getOperationId())).thenReturn(snapshot(ReservationStatus.RESERVED,false));
        recovery.runWorker(prepared.getCommandId());
        assertThat(op().getState()).isEqualTo(ExamCreationState.RESERVED); assertThat(session("new")).isNull();
        assertThat(op().getActiveGuard()).isTrue(); assertThat(op().getPurgeAt()).isNull();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(prepared.getCommandId())),Update.update("recovery.nextAt",NOW),ExamCreationOperation.class);
        when(billing.statusPaid(USER,prepared.getOperationId())).thenThrow(new BillingClientException(BillingClientException.Category.OPERATION_NOT_FOUND,null));
        recovery.runWorker(prepared.getCommandId());
        assertThat(op().getActiveGuard()).isTrue(); assertThat(op().getPurgeAt()).isNull();
        verify(billing,times(2)).statusPaid(USER,prepared.getOperationId()); verifyNoMoreInteractions(billing);
    }
    @Test void terminalReplayDoesNotRestoreDeletedSessionOrBypassActor() {
        pending(); leased(()->lifecycle.applyStatus(USER,prepared.getCommandId(),snapshot(ReservationStatus.CONFIRMED,false)));
        assertThatThrownBy(()->recovery.resumeHttp(UUID.randomUUID().toString(),prepared.getCommandId(),ZoneOffset.UTC)).isInstanceOf(RuntimeException.class);
        mongo.remove(Query.query(Criteria.where("_id").is("new")),ExamSession.class);
        assertThatThrownBy(()->recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC)).isInstanceOf(RuntimeException.class);
        assertThat(session("new")).isNull(); verifyNoInteractions(billing);
    }
    @Test void twoConcurrentFinalizersConvergeToOneResult() throws Exception {
        pending();
        when(billing.statusPaid(USER,prepared.getOperationId())).thenReturn(snapshot(ReservationStatus.CONFIRMED,false));
        var gate=new CountDownLatch(1);
        try (var pool=Executors.newFixedThreadPool(2)) {
            List<Future<?>> work=new ArrayList<>();
            for (int i=0;i<2;i++) work.add(pool.submit(()-> { gate.await();
                try { recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC); }
                catch (web.tosunsaeng.domain.exams.exception.ExamsException expectedBusy) { }
                return null;
            }));
            gate.countDown(); for (var future:work) future.get(20,TimeUnit.SECONDS);
        }
        assertThat(op().getState()).isEqualTo(ExamCreationState.SUCCEEDED);
        assertThat(mongo.count(Query.query(Criteria.where("creationOperationId").is(prepared.getOperationId())),ExamSession.class)).isEqualTo(1);
        assertThat(session("old").getSubmissionsClosedByOperationId()).isEqualTo(prepared.getOperationId());
    }

    @Test void staleLeaseCannotFinalizeButCurrentLeaseCan() {
        pending();
        var stale=execution.claim(prepared.getCommandId(),false);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(prepared.getCommandId())),Update.update("recovery.leaseUntil",NOW),ExamCreationOperation.class);
        var current=execution.claim(prepared.getCommandId(),false);
        assertThat(current).isNotNull();
        assertThatThrownBy(()->execution.within(stale,()->lifecycle.applyStatus(USER,prepared.getCommandId(),snapshot(ReservationStatus.CONFIRMED,false))))
                .isInstanceOf(ReservationRecoveryException.class);
        assertThat(session("old").getSubmissionsClosedAt()).isNull();
        execution.within(current,()->lifecycle.applyStatus(USER,prepared.getCommandId(),snapshot(ReservationStatus.CONFIRMED,false)));
        assertThat(op().getState()).isEqualTo(ExamCreationState.SUCCEEDED);
    }

    @Test void admittedJobsSurviveAndPostClosureReceiptTransactionRollsBack() {
        pending();
        var submissions=new ExamSubmissionEvidence(mongo);
        owners.execute(USER,()-> {
            mongo.insert(QuestionGradingJob.pending("old:1:0","old",1,0,"test-key",NOW).markUserSubmission());
            submissions.accepted("old",1,0,null,NOW); return null;
        });
        leased(()->lifecycle.applyStatus(USER,prepared.getCommandId(),snapshot(ReservationStatus.CONFIRMED,false)));
        assertThat(mongo.findById("old:1:0",QuestionGradingJob.class)).isNotNull();
        assertThat(session("old").getSubmissionCompletedAt()).isNull();
        assertThatThrownBy(()->owners.execute(USER,()-> {
            mongo.insert(QuestionGradingJob.pending("old:2:0","old",2,0,"test-key",NOW).markUserSubmission());
            submissions.accepted("old",2,0,null,NOW); return null;
        })).isInstanceOf(IllegalStateException.class);
        assertThat(mongo.findById("old:2:0",QuestionGradingJob.class)).isNull();
        assertThat(session("old").isCompleted()).isFalse();
    }

    @Test void preReserveRejectionNeedsNoReservationSessionOrConfirmEvidence() {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(prepared.getCommandId())),
                Update.update("recovery.dispatch",ReservationRecovery.Dispatch.MAY_HAVE_BEEN_SENT),ExamCreationOperation.class);
        when(billing.statusPaid(USER,prepared.getOperationId())).thenThrow(new BillingClientException(BillingClientException.Category.PRE_RESERVE_REJECTED,null));
        assertThat(recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC).getState()).isEqualTo(ExamCreationState.FAILED_TERMINAL);
        assertThat(op().getFailureCategory()).isEqualTo("RESERVE_REJECTED_BEFORE_CREATION");
        assertThat(op().getActiveGuard()).isFalse(); assertThat(session("old").isActive()).isTrue();
        assertThat(session("new")).isNull();
    }

    @Test void preReserveRejectionCannotClearCommittedGuard() {
        pending();
        when(billing.statusPaid(USER,prepared.getOperationId())).thenThrow(new BillingClientException(BillingClientException.Category.PRE_RESERVE_REJECTED,null));
        assertThatThrownBy(()->recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC)).isInstanceOf(RuntimeException.class);
        assertThat(op().getState()).isEqualTo(ExamCreationState.SESSION_COMMITTED);
        assertThat(op().getActiveGuard()).isTrue(); assertThat(session("new").isEntitlementConfirming()).isTrue();
    }

    @Test void confirmRejectionResponseDoesNotCompensateUntilDurableStatus() {
        pending();
        when(billing.statusPaid(USER,prepared.getOperationId())).thenReturn(snapshot(ReservationStatus.RESERVED,false),snapshot(ReservationStatus.CANCELED,true));
        when(billing.confirmPaid(anyString(),anyString(),anyString(),anyString(),any()))
                .thenThrow(new BillingClientException(BillingClientException.Category.ENTITLEMENT_REVOKED,null));
        assertThat(recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC).getState()).isEqualTo(ExamCreationState.SESSION_COMMITTED);
        assertThat(op().getActiveGuard()).isTrue(); assertThat(session("new").isEntitlementConfirming()).isTrue();
        assertThat(recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC).getFailureCategory()).isEqualTo("ENTITLEMENT_REVOKED");
        assertThat(session("new").isAbandoned()).isTrue(); assertThat(session("old").isActive()).isTrue();
    }

    @Test void workerConfirmedStatusFinalizesWithoutReserveConfirmOrCancel() {
        pending();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(prepared.getCommandId())),Update.update("recovery.nextAt",NOW),ExamCreationOperation.class);
        when(billing.statusPaid(USER,prepared.getOperationId())).thenReturn(snapshot(ReservationStatus.CONFIRMED,false));
        recovery.runWorker(prepared.getCommandId());
        assertThat(op().getState()).isEqualTo(ExamCreationState.SUCCEEDED);
        verify(billing).statusPaid(USER,prepared.getOperationId()); verifyNoMoreInteractions(billing);
    }

    @Test void staleReservedCannotClearOrReplaceConfirmedEvidence() {
        pending();
        leased(()->lifecycle.applyStatus(USER,prepared.getCommandId(),snapshot(ReservationStatus.CONFIRMED,false)));
        assertThatThrownBy(()->evidence.observe(USER,prepared.getCommandId(),snapshot(ReservationStatus.RESERVED,false)))
                .isInstanceOf(RuntimeException.class);
        assertThat(op().getState()).isEqualTo(ExamCreationState.SUCCEEDED);
        assertThat(session("new").getPaidReservation().snapshot().reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test void ordinaryExpiryBeforeSessionDoesNotTouchExistingExam() {
        leased(()->lifecycle.applyStatus(USER,prepared.getCommandId(),snapshot(ReservationStatus.EXPIRED,false)));
        assertThat(op().getState()).isEqualTo(ExamCreationState.EXPIRED);
        assertThat(op().getFailureCategory()).isNull(); assertThat(op().getActiveGuard()).isFalse();
        assertThat(session("new")).isNull(); assertThat(session("old").isActive()).isTrue();
    }

    @Test void postCommitUnknownSignalUsesDurableSuccessOnNextRequest() {
        pending();
        var failing=spy(lifecycle);
        doAnswer(invocation -> {
            invocation.callRealMethod(); // Real Mongo commit succeeded; inject loss of its acknowledgement.
            throw new UserOwnedCommitOutcomeUnknownException(new IllegalStateException("test acknowledgement lost"));
        }).when(failing).applyStatus(eq(USER),eq(prepared.getCommandId()),any());
        var recovering=new PaidExamRecoveryService(execution,failing,billing,recoveryStore,mock(ReservationAuthCircuit.class));
        when(billing.statusPaid(USER,prepared.getOperationId())).thenReturn(snapshot(ReservationStatus.CONFIRMED,false));
        assertThatThrownBy(()->recovering.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC))
                .isInstanceOf(web.tosunsaeng.domain.exams.exception.ExamsException.class);
        assertThat(op().getState()).isEqualTo(ExamCreationState.SUCCEEDED);
        Instant firstTerminal=op().getTerminalAt();
        assertThat(recovering.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC).getTerminalAt()).isEqualTo(firstTerminal);
        verify(billing).statusPaid(USER,prepared.getOperationId()); verifyNoMoreInteractions(billing);
    }

    @Test void requestDigestMutationFailsBeforeReserveOrSessionCreation() {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(prepared.getCommandId())),Update.update("paidRequestDigest","f".repeat(64)),ExamCreationOperation.class);
        assertThatThrownBy(()->recovery.resumeHttp(USER,prepared.getCommandId(),ZoneOffset.UTC)).isInstanceOf(RuntimeException.class);
        assertThat(op().getActiveGuard()).isTrue(); assertThat(session("new")).isNull(); verifyNoInteractions(billing);
    }
}
