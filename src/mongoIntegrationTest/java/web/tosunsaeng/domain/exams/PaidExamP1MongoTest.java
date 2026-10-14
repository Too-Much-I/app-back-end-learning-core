package web.tosunsaeng.domain.exams;

import com.mongodb.client.*;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import web.tosunsaeng.domain.exams.application.*;
import web.tosunsaeng.domain.exams.domain.entity.*;
import web.tosunsaeng.domain.exams.domain.enums.*;
import web.tosunsaeng.domain.exams.domain.repository.*;
import web.tosunsaeng.domain.usermerge.application.*;
import web.tosunsaeng.domain.usermerge.config.UserMergedProperties;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static web.tosunsaeng.domain.exams.domain.entity.PaidExamSelection.StartReason.*;

@Testcontainers
class PaidExamP1MongoTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");
    static MongoClient client;
    static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
    static final String OWNER = "30000000-0000-4000-8000-000000000001";
    MongoTemplate mongo;
    ExamSubmissionEvidence evidence;
    PaidExamPreparationService preparation;
    UserOwnedTransactionExecutor owners;
    ExamCreationOperationRepository operations;
    PaidReservationEvidenceStore paidStore;
    @BeforeAll static void connect() { client = MongoClients.create(MONGO.getReplicaSetUrl()); }
    @AfterAll static void close() { client.close(); }
    @BeforeEach void setup() {
        var factory = new SimpleMongoClientDatabaseFactory(client, "isolated-paid-p1");
        mongo = new MongoTemplate(factory);
        for (String c : List.of("exam_sessions", "exam_creation_operations", "exam_submission_receipts", "mock_exams",
                "user_ownership_guards", "withdrawn_user_access_denies", "learning_record_billing_continuations", "question_grading_jobs")) {
            if (!mongo.collectionExists(c)) mongo.createCollection(c);
            mongo.remove(new Query(), c);
        }
        mongo.getCollection("exam_creation_operations").createIndex(new Document("userId",1).append("operationId",1), new IndexOptions().unique(true));
        mongo.getCollection("exam_creation_operations").createIndex(new Document("userId",1).append("activeGuard",1),
                new IndexOptions().unique(true).partialFilterExpression(new Document("activeGuard",true)));
        mongo.getCollection("exam_submission_receipts").createIndex(new Document("examId",1).append("questionNumber",1), new IndexOptions().unique(true));
        var repositories = new MongoRepositoryFactory(mongo);
        operations = repositories.getRepository(ExamCreationOperationRepository.class);
        var tx = new TransactionTemplate(new MongoTransactionManager(factory));
        var beans = new StaticListableBeanFactory(); beans.addBean("tx",tx);
        var properties = new UserMergedProperties(); properties.setWriterEnabled(true);
        owners = new UserOwnedTransactionExecutor(properties, new UserOwnershipGuardService(mongo,
                repositories.getRepository(UserOwnershipGuardRepository.class)), beans.getBeanProvider(TransactionOperations.class), Clock.fixed(NOW,ZoneOffset.UTC));
        beans.addBean("owners",owners);
        paidStore = new PaidReservationEvidenceStore(operations, mongo, beans.getBeanProvider(UserOwnedTransactionExecutor.class), Clock.fixed(NOW,ZoneOffset.UTC));
        evidence = new ExamSubmissionEvidence(mongo);
        preparation = new PaidExamPreparationService(mongo, operations,
                new MockExamCatalogService(repositories.getRepository(MockExamRepository.class)),
                new ExamSessionCompletionQuery(mongo), evidence, beans.getBeanProvider(UserOwnedTransactionExecutor.class), Clock.fixed(NOW,ZoneOffset.UTC));
        mock("mock_1",1); mock("mock_2",2);
    }
    void mock(String id,int sequence) { mongo.insert(MockExam.builder().mockExamId(id).sequence(sequence).active(true)
            .questions(List.of(Question.builder().questionNumber(1).build(),Question.builder().questionNumber(2).build())).build()); }
    void session(boolean covered) { mongo.insert(ExamSession.builder().examId("old").userId(OWNER).mockExamId("mock_1")
            .attemptGroupId("group").cycleNumber(1).createdAt(LocalDateTime.ofInstant(NOW,ZoneOffset.UTC))
            .status(ExamSessionStatus.IN_PROGRESS).active(true).submissionEvidenceVersion(covered ? 1 : null)
            .requiredQuestionNumbers(covered ? List.of(1,2) : null).build()); }
    String key() { return UUID.randomUUID().toString(); }
    void submit(int q,int retry) { owners.execute(OWNER,()->{
        String id=GradingKeys.questionJobId("old",q,retry);
        if (!mongo.exists(Query.query(Criteria.where("_id").is(id)),QuestionGradingJob.class)) {
            mongo.insert(QuestionGradingJob.pending(id,"old",q,retry,"test-key",NOW).markUserSubmission());
            evidence.accepted("old",q,retry,null,NOW);
        }
        return null;
    }); }
    @Test void trackingAbsentPartialRetainsSameMockAndReceiptHasNoTtl() {
        session(true); submit(1,0); submit(2,1);
        var op=preparation.prepare(OWNER,key());
        assertThat(op.getMockExamId()).isEqualTo("mock_1");
        assertThat(op.getPaidSelection().startReason()).isEqualTo(RESTART_INCOMPLETE);
        assertThat(mongo.findById("old:1",Document.class,"exam_submission_receipts")).doesNotContainKey("expiresAt");
        assertThat(mongo.findById("old",ExamSession.class).getActive()).isTrue();
        assertThat(mongo.findById(op.getSessionId(),ExamSession.class)).isNull();
    }
    @Test void completeSubmissionSelectsNextWithoutGradingAndSurvivesReceiptRemoval() {
        session(true); submit(1,0); submit(2,0);
        assertThat(mongo.findById("old:1",Document.class,"exam_submission_receipts").getDate("expiresAt").toInstant())
                .isEqualTo(NOW.plus(Duration.ofDays(3)));
        mongo.remove(new Query(),"exam_submission_receipts");
        var op=preparation.prepare(OWNER,key());
        assertThat(op.getMockExamId()).isEqualTo("mock_2");
        assertThat(op.getPaidSelection().startReason()).isEqualTo(NEW_AFTER_SUBMISSION);
        assertThat(op.getReplacementSourceSessionId()).isEqualTo("old");
        assertThat(op.getExpectedMockExamId()).isEqualTo("mock_1");
    }
    @Test void replayDoesNotReselectAfterLateSubmissionCatalogChangeOrServiceRestart() {
        session(true); String key=key(); var first=preparation.prepare(OWNER,key);
        submit(1,0); submit(2,0); mongo.remove(new Query(),MockExam.class);
        var repositories = new MongoRepositoryFactory(mongo);
        var beans = new StaticListableBeanFactory(); beans.addBean("owners", owners);
        preparation = new PaidExamPreparationService(mongo, operations,
                new MockExamCatalogService(repositories.getRepository(MockExamRepository.class)),
                new ExamSessionCompletionQuery(mongo), new ExamSubmissionEvidence(mongo),
                beans.getBeanProvider(UserOwnedTransactionExecutor.class), Clock.fixed(NOW, ZoneOffset.UTC));
        var replay=preparation.prepare(OWNER,key);
        assertThat(replay.getCommandId()).isEqualTo(first.getCommandId());
        assertThat(replay.getPaidSelection()).isEqualTo(first.getPaidSelection());
        assertThat(replay.getMockExamId()).isEqualTo("mock_1");
        assertThat(mongo.count(new Query(),ExamCreationOperation.class)).isEqualTo(1);
    }
    @Test void actualSingleCandidateAllowsSameButBrokenCatalogDoesNot() {
        session(true); submit(1,0); submit(2,0);
        mongo.remove(Query.query(Criteria.where("mock_exam_id").is("mock_2")),MockExam.class);
        assertThat(preparation.prepare(OWNER,key()).getMockExamId()).isEqualTo("mock_1");
        mongo.remove(new Query(),ExamCreationOperation.class); mongo.remove(new Query(),MockExam.class);
        assertThatThrownBy(()->preparation.prepare(OWNER,key())).isInstanceOf(RuntimeException.class);
        assertThat(mongo.count(new Query(),ExamCreationOperation.class)).isZero();
    }
    @Test void unknownLegacyCoverageFailsAndDoesNotBackfill() {
        session(false);
        assertThatThrownBy(()->preparation.prepare(OWNER,key())).isInstanceOf(IllegalStateException.class);
        assertThat(mongo.findById("old",ExamSession.class).getSubmissionEvidenceVersion()).isNull();
        assertThat(mongo.count(new Query(),ExamCreationOperation.class)).isZero();
    }
    @Test void newAndCompletedUseExistingCatalogPolicy() {
        assertThat(preparation.prepare(OWNER,key()).getPaidSelection().startReason()).isNull();
        mongo.remove(new Query(),ExamCreationOperation.class); session(true); submit(1,0); submit(2,0);
        mongo.updateFirst(Query.query(Criteria.where("_id").is("old")),new Update().set("status",ExamSessionStatus.COMPLETED)
                .set("active",false).set("completedAt",LocalDateTime.ofInstant(NOW,ZoneOffset.UTC)),ExamSession.class);
        var op=preparation.prepare(OWNER,key());
        assertThat(op.getPaidSelection().startReason()).isEqualTo(NEW_AFTER_COMPLETION);
        assertThat(op.getMockExamId()).isEqualTo("mock_2");
    }
    @Test void invalidQuestionRollsBackActualUserJobAndReceipt() {
        session(true);
        assertThatThrownBy(()->submit(3,0)).isInstanceOf(IllegalStateException.class);
        assertThat(mongo.count(new Query(),QuestionGradingJob.class)).isZero();
        assertThat(mongo.count(new Query(),"exam_submission_receipts")).isZero();
    }
    @Test void duplicateSubmissionsDoNotExtendCompletionOrExpiry() {
        session(true); submit(1,0); submit(2,0);
        owners.execute(OWNER,()->{ evidence.accepted("old",2,0,null,NOW.plusSeconds(999)); return null; });
        assertThat(mongo.findById("old",ExamSession.class).getSubmissionCompletedAt()).isEqualTo(NOW);
        assertThat(mongo.findById("old:2",Document.class,"exam_submission_receipts").getDate("expiresAt").toInstant()).isEqualTo(NOW.plus(Duration.ofDays(3)));
    }
    @Test void withdrawnOwnerCannotReplay() {
        String key=key(); preparation.prepare(OWNER,key);
        mongo.insert(new Document("_id",OWNER).append("blockedUntil",Date.from(NOW.plusSeconds(60))),"withdrawn_user_access_denies");
        assertThatThrownBy(()->preparation.prepare(OWNER,key)).isInstanceOf(IllegalStateException.class);
    }
    @Test void legacyRecoveryQueryDoesNotPickPaidPreparation() {
        preparation.prepare(OWNER,key());
        var legacy = ExamCreationOperation.prepared("another-owner", key(), "legacy", "mock_1", 1, NOW);
        operations.insert(legacy);
        var execution = org.mockito.Mockito.mock(web.tosunsaeng.domain.exams.billing.reconciliation.ReservationOperationExecution.class);
        org.mockito.Mockito.when(execution.now()).thenReturn(NOW.plusSeconds(600));
        var store = new web.tosunsaeng.domain.exams.billing.reconciliation.ReservationRecoveryStore(mongo, execution,
                new web.tosunsaeng.domain.exams.billing.reconciliation.ReservationReconciliationProperties());
        assertThat(store.due()).extracting(ExamCreationOperation::getCommandId).containsExactly(legacy.getCommandId());
        assertThat(store.dueCount()).isEqualTo(1);
    }
    private web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationSnapshot paidSnapshot(
            ExamCreationOperation op, web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus status,
            web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.Source source) {
        return new web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationSnapshot(op.getOperationId(),
                "00000006-1111-4111-8111-111111111111", BillingReservationKind.INITIAL, status,
                "00000007-1111-4111-8111-111111111111",
                status == web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus.CONFIRMED
                        ? web.tosunsaeng.domain.exams.billing.BillingReservationClient.AttemptGroupStatus.OPEN : null,
                op.getSessionId(),op.getMockExamId(),null,null,NOW.plusSeconds(300),
                status == web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus.RESERVED ? null : NOW,
                source,null,status == web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus.CANCELED
                ? new web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.ConfirmRejection("ENTITLEMENT_REVOKED") : null);
    }
    @Test void paidRequestAndEvidenceRoundTripWithoutChangingLifecycleOrCreatingSession() {
        var op=preparation.prepare(OWNER,key());
        var request=paidStore.prepareRequest(OWNER,op.getCommandId());
        String digest=operations.findById(op.getCommandId()).orElseThrow().getPaidRequestDigest();
        assertThat(digest).matches("[0-9a-f]{64}");
        assertThat(paidStore.prepareRequest(OWNER,op.getCommandId())).isEqualTo(request);
        var snapshot=paidSnapshot(op,web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus.RESERVED,
                web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.Source.SUBSCRIPTION);
        paidStore.observe(OWNER,op.getCommandId(),snapshot);
        var stored=operations.findById(op.getCommandId()).orElseThrow();
        assertThat(stored.getPaidReservation().snapshot()).isEqualTo(snapshot);
        assertThat(stored.getState()).isEqualTo(ExamCreationState.PREPARED);
        assertThat(stored.getActiveGuard()).isTrue(); assertThat(stored.getPurgeAt()).isNull();
        assertThat(mongo.findById(op.getSessionId(),ExamSession.class)).isNull();
        var changed=paidSnapshot(op, snapshot.reservationStatus(),web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.Source.EXAM_PASS);
        assertThatThrownBy(()->paidStore.observe(OWNER,op.getCommandId(),changed)).isInstanceOf(RuntimeException.class);
        assertThat(operations.findById(op.getCommandId()).orElseThrow().getPaidReservation().snapshot()).isEqualTo(snapshot);
    }
    @Test void phonePaidNewGroupAndFreeOldGroupPersistWithoutOverwritingDiscovery() {
        for (boolean paid : List.of(true, false)) {
            String user = UUID.randomUUID().toString(), oldGroup = key(), continuation = key();
            var op = ExamCreationOperation.prepared(user,key(),"phone-"+paid,"mock_1",1,null,oldGroup,"mock_1",
                    BillingContinuationReason.PHONE_REJOIN,continuation,NOW);
            op.fixPaidSelection(new PaidExamSelection(null,List.of(1,2)));
            operations.insert(op);
            var request = paidStore.preparePhoneRequest(user,op.getCommandId());
            assertThat(paidStore.preparePhoneRequest(user,op.getCommandId())).isEqualTo(request);
            String actualGroup = paid ? key() : oldGroup;
            var snapshot = new web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationSnapshot(
                    op.getOperationId(),key(),paid ? BillingReservationKind.INITIAL : BillingReservationKind.REPLACEMENT,
                    web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus.RESERVED,
                    actualGroup,null,op.getSessionId(),op.getMockExamId(),BillingContinuationReason.PHONE_REJOIN,continuation,
                    NOW.plusSeconds(300),null,paid ? web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.Source.SUBSCRIPTION
                    : web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.Source.EXAM_PASS,null,null);
            paidStore.observe(user,op.getCommandId(),snapshot);
            paidStore.observe(user,op.getCommandId(),snapshot);
            var stored = operations.findById(op.getCommandId()).orElseThrow();
            assertThat(stored.getExpectedAttemptGroupId()).isEqualTo(oldGroup);
            assertThat(stored.getPaidReservation().snapshot()).isEqualTo(snapshot);
            assertThat(stored.getState()).isEqualTo(op.getState());
            assertThat(stored.getActiveGuard()).isTrue();
            assertThat(mongo.findById(op.getSessionId(),ExamSession.class)).isNull();
            // An owner mismatch must roll back any subsequent observation.
            mongo.insert(ExamSession.builder().examId(op.getSessionId()).userId(OWNER).build());
            assertThatThrownBy(() -> paidStore.observe(user,op.getCommandId(),snapshot)).isInstanceOf(RuntimeException.class);
            assertThat(operations.findById(op.getCommandId()).orElseThrow().getPaidReservation().snapshot()).isEqualTo(snapshot);
        }
    }
    @Test void paidRejectionPersistsToExactSessionAndTerminalCannotRegress() {
        var op=preparation.prepare(OWNER,key()); paidStore.prepareRequest(OWNER,op.getCommandId());
        var snapshot=paidSnapshot(op,web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus.CANCELED,
                web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.Source.SUBSCRIPTION);
        mongo.insert(ExamSession.builder().examId(op.getSessionId()).userId(OWNER).mockExamId(op.getMockExamId())
                .creationOperationId(op.getOperationId()).billingReservationId(snapshot.reservationId())
                .attemptGroupId(snapshot.attemptGroupId()).billingReservationKind(snapshot.reservationKind()).active(false).build());
        paidStore.observe(OWNER,op.getCommandId(),snapshot);
        assertThat(mongo.findById(op.getSessionId(),ExamSession.class).getPaidReservation().snapshot()).isEqualTo(snapshot);
        assertThat(mongo.findById(op.getSessionId(),ExamSession.class).getActive()).isFalse();
        var confirmed=paidSnapshot(op,web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus.CONFIRMED,snapshot.entitlementSource());
        assertThatThrownBy(()->paidStore.observe(OWNER,op.getCommandId(),confirmed)).isInstanceOf(RuntimeException.class);
        assertThat(operations.findById(op.getCommandId()).orElseThrow().getPaidReservation().snapshot()).isEqualTo(snapshot);
    }
    @Test void abbreviatedConfirmCannotEraseFullPaidEvidence() {
        var op=preparation.prepare(OWNER,key()); paidStore.prepareRequest(OWNER,op.getCommandId());
        var snapshot=paidSnapshot(op,web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus.RESERVED,
                web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.Source.SUBSCRIPTION);
        paidStore.observe(OWNER,op.getCommandId(),snapshot);
        var abbreviated=new web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationSnapshot(
                op.getOperationId(),snapshot.reservationId(),null,
                web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus.CONFIRMED,
                snapshot.attemptGroupId(),web.tosunsaeng.domain.exams.billing.BillingReservationClient.AttemptGroupStatus.OPEN,
                op.getSessionId(),null,null,NOW);
        assertThatThrownBy(()->paidStore.observe(OWNER,op.getCommandId(),abbreviated)).isInstanceOf(RuntimeException.class);
        assertThat(operations.findById(op.getCommandId()).orElseThrow().getPaidReservation().snapshot()).isEqualTo(snapshot);
    }
    @Test void paidEvidenceWrongSessionOwnerRollsBackBothDocuments() {
        var op=preparation.prepare(OWNER,key()); paidStore.prepareRequest(OWNER,op.getCommandId());
        var snapshot=paidSnapshot(op,web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus.RESERVED,
                web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.Source.SUBSCRIPTION);
        mongo.insert(ExamSession.builder().examId(op.getSessionId()).userId("other").build());
        assertThatThrownBy(()->paidStore.observe(OWNER,op.getCommandId(),snapshot)).isInstanceOf(RuntimeException.class);
        assertThat(operations.findById(op.getCommandId()).orElseThrow().getPaidReservation()).isNull();
        assertThat(mongo.findById(op.getSessionId(),ExamSession.class).getPaidReservation()).isNull();
    }
    @Test void simultaneousLastSubmissionAndPreparationConvergeToOneFrozenDecision() throws Exception {
        session(true); submit(1,0); String key=key(); var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var accepted=pool.submit(()->{ start.await(); return transientConflictOrResult(()->{ submit(2,0); return true; }); });
            var prepared=pool.submit(()->{ start.await(); return transientConflictOrResult(()->preparation.prepare(OWNER,key)); });
            start.countDown(); var admitted=accepted.get(20,TimeUnit.SECONDS); var op=prepared.get(20,TimeUnit.SECONDS);
            // The existing bounded owner executor may exhaust three immediate attempts.
            // A known aborted write conflict is retried as the same caller request after contention ends.
            if (admitted == null) submit(2,0);
            if (op == null) op=preparation.prepare(OWNER,key);
            assertThat(op.getPaidSelection().startReason()).isIn(RESTART_INCOMPLETE,NEW_AFTER_SUBMISSION);
            assertThat(op.getMockExamId()).isEqualTo(op.getPaidSelection().startReason()==RESTART_INCOMPLETE ? "mock_1":"mock_2");
            assertThat(preparation.prepare(OWNER,key).getPaidSelection()).isEqualTo(op.getPaidSelection());
        }
    }
    @Test void simultaneousSameKeyHasOneOperation() throws Exception {
        session(true); String key=key(); var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{ start.await(); return transientConflictOrResult(()->preparation.prepare(OWNER,key)); });
            var b=pool.submit(()->{ start.await(); return transientConflictOrResult(()->preparation.prepare(OWNER,key)); });
            start.countDown(); var first=a.get(20,TimeUnit.SECONDS); var second=b.get(20,TimeUnit.SECONDS);
            if (first == null) first=preparation.prepare(OWNER,key);
            if (second == null) second=preparation.prepare(OWNER,key);
            assertThat(first.getCommandId()).isEqualTo(second.getCommandId());
        }
        assertThat(mongo.count(new Query(),ExamCreationOperation.class)).isEqualTo(1);
    }

    private <T> T transientConflictOrResult(java.util.function.Supplier<T> work) {
        try { return work.get(); }
        catch (RuntimeException failure) {
            for (Throwable cause=failure; cause!=null; cause=cause.getCause()) {
                if (cause instanceof com.mongodb.MongoException mongoFailure
                        && mongoFailure.hasErrorLabel(com.mongodb.MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                        && !mongoFailure.hasErrorLabel(com.mongodb.MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) return null;
            }
            throw failure;
        }
    }
}
