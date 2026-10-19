package web.tosunsaeng.domain.learningrecorddeletion;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import web.tosunsaeng.domain.exams.application.*;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupProjectionStatus;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventProperties;
import web.tosunsaeng.domain.exams.domain.entity.*;
import web.tosunsaeng.domain.exams.domain.enums.*;
import web.tosunsaeng.domain.exams.domain.repository.*;
import web.tosunsaeng.domain.learningrecorddeletion.domain.*;
import web.tosunsaeng.domain.learningrecorddeletion.infrastructure.DeletedExamContinuationStore;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardService;
import web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class DeletedExamContinuationMongoTest {
    @Container private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");
    private static final String OWNER = "00000000-0000-0000-0000-000000000001";
    private static final String DELETION = "139f345b-9be8-43a3-a63d-9108df972741";
    private static final String GROUP = "d5d1a1f4-bd12-4240-9b05-47a08ced480a";
    private static final Instant NOW = Instant.parse("2026-10-03T01:00:00Z");
    private MongoClient client;
    private MongoTemplate mongo;
    private TransactionTemplate tx;
    private DeletedExamContinuationStore store;
    private BillingExamCreationTransactionService billingTx;

    @BeforeEach void setup() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        var factory = new SimpleMongoClientDatabaseFactory(client, "deletion-continuation-fixture");
        mongo = new MongoTemplate(factory);
        for (String collection : mongo.getCollectionNames()) mongo.dropCollection(collection);
        tx = new TransactionTemplate(new MongoTransactionManager(factory));
        var repositories = new MongoRepositoryFactory(mongo);
        var guards = new UserOwnershipGuardService(mongo, repositories.getRepository(UserOwnershipGuardRepository.class));
        store = new DeletedExamContinuationStore(mongo, guards, Clock.fixed(NOW, ZoneOffset.UTC));
        var provider = new StaticListableBeanFactory();
        provider.addBean("tx", tx);
        billingTx = new BillingExamCreationTransactionService(repositories.getRepository(ExamCreationOperationRepository.class),
                repositories.getRepository(ExamSessionRepository.class), mock(ExamSessionManager.class),
                provider.getBeanProvider(TransactionOperations.class), mock(AttemptGroupEventProperties.class));
        ReflectionTestUtils.setField(billingTx, "deletedExamContinuations", store);
        mongo.indexOps(ExamCreationOperation.class).ensureIndex(new Index().on("userId", Sort.Direction.ASC)
                .unique().partial(PartialIndexFilter.of(Criteria.where("activeGuard").is(true))));
        mongo.indexOps(ExamCreationOperation.class).ensureIndex(new Index().on("userId", Sort.Direction.ASC)
                .on("operationId", Sort.Direction.ASC).unique());
        for (Class<?> type : List.of(DeletedExamContinuation.class, ExamSession.class, DeletionOperation.class,
                DeletionTarget.class, UserOwnershipGuard.class)) {
            if (!mongo.collectionExists(type)) mongo.createCollection(type);
        }
        mongo.insert(UserOwnershipGuard.active(OWNER, NOW));
    }

    @AfterEach void close() { client.close(); }

    @Test void sourceDeletionAndEvidenceInsertCommitAtomicallyAndReplay() {
        seed();
        tx.execute(s -> store.retainAndDeleteSession(DELETION, "old"));
        assertThat(mongo.findById("old", ExamSession.class)).isNull();
        assertThat(mongo.findById("old", DeletedExamContinuation.class).getAttemptGroupId()).isEqualTo(GROUP);
        tx.execute(s -> store.retainAndDeleteSession(DELETION, "old"));
        assertThat(mongo.count(new Query(), DeletedExamContinuation.class)).isEqualTo(1);
    }

    @Test void rollbackKeepsOriginalAndDoesNotLeaveTombstone() {
        seed();
        assertThatThrownBy(() -> tx.execute(s -> {
            store.retainAndDeleteSession(DELETION, "old");
            throw new IllegalStateException("fixture rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(mongo.findById("old", ExamSession.class)).isNotNull();
        assertThat(mongo.findById("old", DeletedExamContinuation.class)).isNull();
    }

    @Test void noTransactionOrUnsealedTargetCannotDeleteSession() {
        seed();
        assertThatThrownBy(() -> store.retainAndDeleteSession(DELETION, "old")).isInstanceOf(IllegalStateException.class);
        mongo.remove(new Query(), DeletionTarget.class);
        mongo.insert(DeletionTarget.draining(DELETION, OWNER, DeletionTarget.Type.EXAM, "old"));
        assertThatThrownBy(() -> tx.execute(s -> store.retainAndDeleteSession(DELETION, "old")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mongo.findById("old", ExamSession.class)).isNotNull();
    }

    @Test void nonTerminalReservationPreventsDeletion() {
        seed();
        mongo.insert(prepared("139f345b-9be8-43a3-a63d-9108df972742", "new"));
        assertThatThrownBy(() -> tx.execute(s -> store.retainAndDeleteSession(DELETION, "old")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mongo.findById("old", ExamSession.class)).isNotNull();
    }

    @Test void needsReviewCannotDeleteEvenIfStageAndTargetWerePreviouslyPrepared() {
        seed();
        var deletion = mongo.findById(DELETION, DeletionOperation.class);
        deletion.requireIntervention(true);
        mongo.save(deletion);
        assertThatThrownBy(() -> tx.execute(s -> store.retainAndDeleteSession(DELETION, "old")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mongo.findById("old", ExamSession.class)).isNotNull();
        assertThat(mongo.findById("old", DeletedExamContinuation.class)).isNull();
    }

    @Test void existingBillingTransactionClaimsAndTransfersSameGroupWithoutRemoteServer() {
        retainedAndSafe();
        var operation = prepared(DELETION, "new");
        billingTx.insertPrepared(operation);
        assertThatThrownBy(() -> store.findAvailable(OWNER)).isInstanceOf(IllegalStateException.class);
        operation.markReserved("reservation-new", BillingReservationKind.REPLACEMENT, GROUP, NOW.plusSeconds(300), NOW);
        mongo.save(operation);
        billingTx.commitReservedSession(operation.getCommandId(), NOW, ZoneOffset.UTC);
        billingTx.commitReservedSession(operation.getCommandId(), NOW, ZoneOffset.UTC);
        var source = mongo.findById("old", DeletedExamContinuation.class);
        assertThat(source.getTransferState()).isEqualTo(DeletedExamContinuation.TransferState.TRANSFERRED);
        assertThat(source.getReplacementExamId()).isEqualTo("new");
        assertThat(source.getExpiresAt()).isNull();
        assertThat(mongo.findById("old", ExamSession.class)).isNull();
        assertThat(mongo.findById("new", ExamSession.class).getAttemptGroupId()).isEqualTo(GROUP);
        assertThat(store.findAvailable(OWNER)).isEmpty();
        assertThatThrownBy(() -> billingTx.insertPrepared(prepared("139f345b-9be8-43a3-a63d-9108df972742", "new-2")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test void operationInsertFailureRollsBackClaim() {
        retainedAndSafe();
        mongo.insert(prepared(DELETION, "existing"));
        assertThatThrownBy(() -> billingTx.insertPrepared(prepared(DELETION, "new")))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(store.findAvailable(OWNER)).isPresent();
    }

    @Test void initialFallbackCannotBypassUntransferredSource() {
        retainedAndSafe();
        var initial = ExamCreationOperation.prepared(OWNER, DELETION, "new", "mock_exam_001", 2, NOW);
        assertThatThrownBy(() -> billingTx.insertPrepared(initial)).isInstanceOf(IllegalStateException.class);
        assertThat(mongo.count(new Query(), ExamCreationOperation.class)).isZero();
    }

    @Test void concurrentClaimsHaveOnlyOneWinner() throws Exception {
        retainedAndSafe();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> claimAfter(start, DELETION, "new-1"));
            var second = pool.submit(() -> claimAfter(start, "139f345b-9be8-43a3-a63d-9108df972742", "new-2"));
            start.countDown();
            assertThat((first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(1);
        }
        assertThat(mongo.count(new Query(), ExamCreationOperation.class)).isEqualTo(1);
        assertThat(mongo.findById("old", DeletedExamContinuation.class).getTransferState())
                .isEqualTo(DeletedExamContinuation.TransferState.CLAIMED);
    }

    @Test void canceledPrecommitReleasesClaim() {
        retainedAndSafe();
        var operation = billingTx.insertPrepared(prepared(DELETION, "new"));
        operation.markReserved("reservation-new", BillingReservationKind.REPLACEMENT, GROUP, NOW.plusSeconds(300), NOW);
        mongo.save(operation);
        billingTx.markCanceled(operation.getCommandId(), NOW);
        assertThat(store.findAvailable(OWNER)).isPresent();
    }

    @Test void transferFailureRollsBackNewSessionAndLeavesReservedOperationAndClaim() {
        retainedAndSafe();
        var operation = billingTx.insertPrepared(prepared(DELETION, "new"));
        // A contradictory remote snapshot must not cause a newly charged INITIAL Session to commit.
        operation.markReserved("reservation-new", BillingReservationKind.INITIAL, GROUP, NOW.plusSeconds(300), NOW);
        mongo.save(operation);
        assertThatThrownBy(() -> billingTx.commitReservedSession(operation.getCommandId(), NOW, ZoneOffset.UTC))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mongo.findById("new", ExamSession.class)).isNull();
        assertThat(mongo.findById(operation.getCommandId(), ExamCreationOperation.class).getState()).isEqualTo(ExamCreationState.RESERVED);
        assertThat(mongo.findById("old", DeletedExamContinuation.class).getTransferState())
                .isEqualTo(DeletedExamContinuation.TransferState.CLAIMED);
    }

    @Test void sessionEvidencePreventsClaimReleaseEvenWhenOperationSaysCanceled() {
        retainedAndSafe();
        var operation = billingTx.insertPrepared(prepared(DELETION, "new"));
        operation.markReserved("reservation-new", BillingReservationKind.REPLACEMENT, GROUP, NOW.plusSeconds(300), NOW);
        operation.markCanceled(NOW, NOW.plusSeconds(600));
        mongo.insert(ExamSession.builder().examId("new").userId(OWNER).creationOperationId(DELETION).build());
        assertThatThrownBy(() -> tx.execute(s -> { store.releaseCanceledClaim(operation); return null; }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mongo.findById("old", DeletedExamContinuation.class).getTransferState())
                .isEqualTo(DeletedExamContinuation.TransferState.CLAIMED);
    }

    @Test void deletionWriteBlockPreventsClaimBeforeRestartCheckpoint() {
        seed();
        tx.execute(s -> store.retainAndDeleteSession(DELETION, "old"));
        assertThatThrownBy(() -> billingTx.insertPrepared(prepared(DELETION, "new"))).isInstanceOf(IllegalStateException.class);
        assertThat(mongo.count(new Query(), ExamCreationOperation.class)).isZero();
        assertThat(store.findAvailable(OWNER)).isPresent();
    }

    private boolean claimAfter(CountDownLatch start, String id, String session) throws InterruptedException {
        start.await();
        try { billingTx.insertPrepared(prepared(id, session)); return true; }
        catch (RuntimeException expectedConflict) { return false; }
    }

    private void seed() {
        var deletion = DeletionOperation.requested(DELETION, OWNER, NOW);
        deletion.advance(DeletionOperation.Stage.FENCED);
        deletion.advance(DeletionOperation.Stage.INVENTORYING);
        deletion.sealInventory(1, "a".repeat(64));
        deletion.advance(DeletionOperation.Stage.WAITING_COORDINATION);
        deletion.advance(DeletionOperation.Stage.PREPARING_RESTART);
        mongo.insert(deletion);
        var target = DeletionTarget.draining(DELETION, OWNER, DeletionTarget.Type.EXAM, "old");
        target.sealCallbacks(true, true, NOW);
        mongo.insert(target);
        mongo.insert(ExamSession.builder().examId("old").userId(OWNER).mockExamId("mock_exam_001").cycleNumber(2)
                .attemptGroupId(GROUP).billingReservationId("reservation-old").creationOperationId("operation-old")
                .entitlementState(ExamEntitlementState.CONFIRMED).status(ExamSessionStatus.IN_PROGRESS)
                .attemptGroupProjectionStatus(AttemptGroupProjectionStatus.OPEN).build());
    }

    private void retainedAndSafe() {
        seed();
        tx.execute(s -> store.retainAndDeleteSession(DELETION, "old"));
        var deletion = mongo.findById(DELETION, DeletionOperation.class);
        deletion.allowNewLearning(new DeletionOperation.RestartEvidence("a".repeat(64), 1, 1, true, true, true, true), NOW);
        mongo.save(deletion);
    }

    private static ExamCreationOperation prepared(String operationId, String sessionId) {
        return ExamCreationOperation.prepared(OWNER, operationId, sessionId, "mock_exam_001", 2,
                "old", GROUP, "mock_exam_001", NOW);
    }
}
