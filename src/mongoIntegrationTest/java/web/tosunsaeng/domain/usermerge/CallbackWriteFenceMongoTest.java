package web.tosunsaeng.domain.usermerge;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.mapping.callback.EntityCallbacks;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import web.tosunsaeng.domain.learningrecorddeletion.application.DeletionAccess;
import web.tosunsaeng.domain.learningrecorddeletion.infrastructure.DeletionPersistenceFence;
import web.tosunsaeng.domain.usermerge.application.*;
import web.tosunsaeng.domain.usermerge.config.UserMergedProperties;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;

import java.time.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class CallbackWriteFenceMongoTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");
    static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");
    MongoClient client;
    MongoTemplate mongo;
    TransactionTemplate tx;
    CallbackWriteFence fence;
    UserOwnershipGuardService guards;
    UserOwnedTransactionExecutor writers;

    @BeforeEach void setup() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        mongo = new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, "callback-fence"));
        mongo.getDb().drop();
        for (String c : new String[]{CallbackWriteFence.COLLECTION, "user_ownership_guards", "exam_sessions",
                "azure_results", "exam_results", "withdrawn_user_access_denies", "learning_record_deletion_targets"})
            mongo.createCollection(c);
        tx = new TransactionTemplate(new MongoTransactionManager(mongo.getMongoDatabaseFactory()));
        fence = new CallbackWriteFence(mongo, true);
        guards = new UserOwnershipGuardService(mongo,
                new MongoRepositoryFactory(mongo).getRepository(UserOwnershipGuardRepository.class));
        ReflectionTestUtils.setField(guards, "callbackWriteFence", fence);
        var beans = new DefaultListableBeanFactory(); beans.registerSingleton("tx", tx);
        var props = new UserMergedProperties(); props.setWriterEnabled(true);
        writers = new UserOwnedTransactionExecutor(props, guards,
                beans.getBeanProvider(org.springframework.transaction.support.TransactionOperations.class), Clock.fixed(NOW, ZoneOffset.UTC));
        var access = new DeletionAccess(mongo);
        ReflectionTestUtils.setField(writers, "deletionAccess", access);
        tx.executeWithoutResult(s -> guards.touchActive("owner", NOW));
        mongo.insert(ExamSession.builder().examId("exam").userId("owner").build());
        tx.executeWithoutResult(s -> fence.touchExam("exam", NOW));
        mongo.setEntityCallbacks(EntityCallbacks.create(new DeletionPersistenceFence(mongo, access, guards, Clock.fixed(NOW, ZoneOffset.UTC))));
    }
    @AfterEach void close() { client.close(); }

    @Test void asyncSummaryClaimAndFailureUseReadChecksWithoutCallerScope() throws Exception {
        verifyAsyncSummary(false);
    }

    @Test void asyncSummaryDispatchFailureAlsoLeavesProtectionDocumentsUnchanged() throws Exception {
        verifyAsyncSummary(true);
    }

    private void verifyAsyncSummary(boolean failDispatch) throws Exception {
        var factory = new MongoRepositoryFactory(mongo);
        var jobs = factory.getRepository(web.tosunsaeng.domain.exams.domain.repository.SummaryGradingJobRepository.class);
        var sessions = factory.getRepository(web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository.class);
        mongo.createCollection("summary_grading_jobs");
        mongo.updateFirst(Query.query(Criteria.where("_id").is("exam")),
                Update.update("status", "IN_PROGRESS").set("mockExamId", "mock_exam_001"), "exam_sessions");
        writers.executeGrading("owner", "exam", () -> jobs.insert(
                web.tosunsaeng.domain.exams.domain.entity.SummaryGradingJob.pending(
                        "summary:exam:v1", "exam", "mock_exam_001", NOW)));
        var dispatch = org.mockito.Mockito.mock(web.tosunsaeng.domain.exams.application.GradingDispatchService.class);
        if (failDispatch) org.mockito.Mockito.doThrow(new IllegalStateException("test dispatch failure"))
                .when(dispatch).dispatchSummary(org.mockito.ArgumentMatchers.any());
        var before = mongo.findById("owner", Document.class, "user_ownership_guards");
        var lanes = mongo.find(new Query(), Document.class, CallbackWriteFence.COLLECTION);
        var future = new java.util.concurrent.atomic.AtomicReference<Future<?>>();
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            var scheduler = new web.tosunsaeng.domain.exams.application.SummaryDispatchScheduler(
                    task -> future.set(worker.submit(task)), jobs, sessions, dispatch,
                    new web.tosunsaeng.global.config.GradingProperties(Duration.ofMinutes(1), Duration.ofMinutes(3),
                            3, java.net.URI.create("http://test-ai:8000"), Duration.ofSeconds(3),
                            Duration.ofSeconds(30), 2, 100), Clock.fixed(NOW, ZoneOffset.UTC));
            ReflectionTestUtils.setField(scheduler, "userOwnedTransactionExecutor", writers);
            assertThat(scheduler.schedulePending("summary:exam:v1", 1)).isTrue();
            future.get().get(15, TimeUnit.SECONDS);
        }
        var saved = jobs.findById("summary:exam:v1").orElseThrow();
        assertThat(saved.getDispatchAttempt()).isEqualTo(1);
        assertThat(saved.getStatus().name()).isEqualTo(failDispatch ? "FAILED" : "PROCESSING");
        org.mockito.Mockito.verify(dispatch).dispatchSummary(org.mockito.ArgumentMatchers.any());
        assertThat(mongo.findById("owner", Document.class, "user_ownership_guards")).isEqualTo(before);
        assertThat(mongo.find(new Query(), Document.class, CallbackWriteFence.COLLECTION))
                .containsExactlyInAnyOrderElementsOf(lanes);
    }

    @Test void gradingEntryAndPersistenceWriteNeitherOwnerNorExamFences() {
        Document owner = mongo.findById("owner", Document.class, "user_ownership_guards");
        var lanes = mongo.find(new Query(), Document.class, CallbackWriteFence.COLLECTION);
        writers.executeGrading("owner", "exam", () -> {
            mongo.insert(new Document("_id", "read-checked").append("examId", "exam")
                    .append("userId", "owner"), "exam_results");
            return null;
        });
        assertThat(mongo.findById("owner", Document.class, "user_ownership_guards")).isEqualTo(owner);
        assertThat(mongo.find(new Query(), Document.class, CallbackWriteFence.COLLECTION)).containsExactlyInAnyOrderElementsOf(lanes);
        assertThat(mongo.findById("read-checked", Document.class, "exam_results")).isNotNull();
    }

    @Test void gradingDoesNotConflictWithAnUnrelatedOwnerTouch() throws Exception {
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (ExecutorService threads = Executors.newSingleThreadExecutor()) {
            Future<?> writer = threads.submit(() -> tx.executeWithoutResult(s -> {
                guards.touchActive("owner", NOW);
                locked.countDown(); await(release);
            }));
            await(locked);
            try {
                writers.executeGrading("owner", "exam", () -> {
                    mongo.insert(new Document("_id", "concurrent").append("examId", "exam")
                            .append("userId", "owner"), "exam_results");
                    return null;
                });
            } finally { release.countDown(); }
            writer.get(15, TimeUnit.SECONDS);
            assertThat(mongo.findById("concurrent", Document.class, "exam_results")).isNotNull();
        } finally { release.countDown(); }
    }

    @Test void readCheckedGradingStillRejectsSealedExamAndWithdrawnOrMergedOwner() {
        mongo.insert(new Document("_id", "sealed").append("targetType", "EXAM")
                .append("aggregateId", "exam").append("callbackFence", "SEALED"), "learning_record_deletion_targets");
        assertThatThrownBy(() -> writers.executeGrading("owner", "exam", () -> null))
                .isInstanceOf(web.tosunsaeng.domain.learningrecorddeletion.application.DeletionFailure.class);
        mongo.remove(new Query(), "learning_record_deletion_targets");
        mongo.insert(new Document("_id", "owner").append("blockedUntil", java.util.Date.from(NOW.plusSeconds(60))), "withdrawn_user_access_denies");
        assertThatThrownBy(() -> writers.executeGrading("owner", "exam", () -> null))
                .isInstanceOf(UserOwnershipGuardException.class);
        mongo.remove(new Query(), "withdrawn_user_access_denies");
        tx.executeWithoutResult(s -> guards.markMerged("owner", "target", "event", NOW, NOW));
        assertThatThrownBy(() -> writers.executeGrading("owner", "exam", () -> null))
                .isInstanceOf(UserOwnershipGuardException.class);
    }

    @Test void gradingScopeIsRestoredAfterNestedFailure() {
        assertThatThrownBy(() -> guards.gradingReadChecks(() -> guards.gradingReadChecks(() -> {
            throw new IllegalArgumentException("synthetic");
        }))).isInstanceOf(IllegalArgumentException.class);
        tx.executeWithoutResult(s -> guards.touchActive("owner", NOW));
        assertThat(mongo.findById("owner", Document.class, "user_ownership_guards").get("revision")).isEqualTo(1L);
    }

    @Test void acceptedTradeoffAllowsLateInsertAfterConcurrentDeletion() throws Exception {
        var checked = new CountDownLatch(1); var deleted = new CountDownLatch(1);
        try (ExecutorService threads = Executors.newSingleThreadExecutor()) {
            Future<?> grading = threads.submit(() -> writers.executeGrading("owner", "exam", () -> {
                assertThat(mongo.findById("exam", ExamSession.class)).isNotNull();
                checked.countDown(); await(deleted);
                mongo.insert(new Document("_id", "accepted-late").append("examId", "exam")
                        .append("userId", "owner"), "exam_results");
                return null;
            }));
            await(checked);
            try {
                tx.executeWithoutResult(s -> {
                    fence.touchOwner("owner", NOW);
                    mongo.remove(Query.query(Criteria.where("_id").is("exam")), "exam_sessions");
                });
            } finally { deleted.countDown(); }
            grading.get(15, TimeUnit.SECONDS);
            assertThat(mongo.findById("accepted-late", Document.class, "exam_results")).isNotNull();
        } finally { deleted.countDown(); }
    }

    @Test void callbackEntryAndPersistenceDoNotUpdateOwnerGuard() {
        Document before = mongo.findById("owner", Document.class, "user_ownership_guards");
        writers.executeCallback("owner", "exam", CallbackWriteFence.Lane.AZURE, () -> {
            // Persistence fence calls touchActive again; it must remain in the Azure lane.
            mongo.insert(new Document("_id", "azure").append("exam_id", "exam"), "azure_results");
            return null;
        });
        assertThat(mongo.findById("owner", Document.class, "user_ownership_guards")).isEqualTo(before);
        assertThat(mongo.findById("AZURE:exam", Document.class, CallbackWriteFence.COLLECTION).get("revision")).isEqualTo(3L);
        assertThat(fence.scoped()).isFalse();
    }

    @Test void azureAndFeedbackCanHoldDifferentLanesConcurrently() throws Exception {
        try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
            var both = new CountDownLatch(2);
            Future<?> azure = threads.submit(() -> writers.executeCallback("owner", "exam", CallbackWriteFence.Lane.AZURE, () -> {
                both.countDown(); await(both);
                mongo.insert(new Document("_id", "azure").append("exam_id", "exam"), "azure_results"); return null;
            }));
            Future<?> feedback = threads.submit(() -> writers.executeCallback("owner", "exam", CallbackWriteFence.Lane.CORE, () -> {
                both.countDown(); await(both);
                mongo.insert(new Document("_id", "feedback").append("examId", "exam").append("userId", "owner"), "exam_results"); return null;
            }));
            azure.get(15, TimeUnit.SECONDS); feedback.get(15, TimeUnit.SECONDS);
            assertThat(mongo.count(new Query(), "azure_results")).isEqualTo(1);
            assertThat(mongo.count(new Query(), "exam_results")).isEqualTo(1);
        }
    }

    @Test void staleCallbackSnapshotCannotCommitAfterDeletion() throws Exception {
        var snapshot = new CountDownLatch(1); var changed = new CountDownLatch(1);
        try (ExecutorService threads = Executors.newSingleThreadExecutor()) {
            Future<?> callback = threads.submit(() -> tx.executeWithoutResult(s -> {
                assertThat(mongo.findById("exam", ExamSession.class)).isNotNull();
                snapshot.countDown(); await(changed);
                guards.callbackScope("owner", "exam", CallbackWriteFence.Lane.AZURE, () -> {
                    guards.touchActive("owner", NOW);
                    mongo.insert(new Document("_id", "late").append("exam_id", "exam"), "azure_results"); return null;
                });
            }));
            await(snapshot);
            tx.executeWithoutResult(s -> {
                guards.fenceCallbacksForExam("exam", NOW);
                mongo.remove(Query.query(Criteria.where("_id").is("exam")), "exam_sessions");
            });
            changed.countDown();
            assertThatThrownBy(() -> callback.get(15, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .satisfies(CallbackWriteFenceMongoTest::assertWriteConflict);
            assertThat(mongo.count(new Query(), "azure_results")).isZero();
        } finally { changed.countDown(); }
    }

    @Test void lifecycleOwnerBarrierTouchesEveryLaneAndRejectsMergedOwner() {
        tx.executeWithoutResult(s -> {
            guards.fenceCallbacksForOwner("owner", NOW);
            guards.markMerged("owner", "target", "event", NOW, NOW);
        });
        for (CallbackWriteFence.Lane lane : CallbackWriteFence.Lane.values()) {
            assertThat(mongo.findById(lane.name() + ":exam", Document.class, CallbackWriteFence.COLLECTION).get("revision")).isEqualTo(2L);
            assertThatThrownBy(() -> writers.executeCallback("owner", "exam", lane, () -> null))
                    .isInstanceOf(UserOwnershipGuardException.class);
        }
    }

    @Test void firstFenceInsertionAlsoRejectsStaleCallback() throws Exception {
        mongo.remove(new Query(), CallbackWriteFence.COLLECTION);
        staleCallbackSnapshotCannotCommitAfterDeletion();
    }

    @Test void lifecycleCannotCommitAcrossAnInFlightCallback() throws Exception {
        var writing = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (ExecutorService threads = Executors.newSingleThreadExecutor()) {
            Future<?> callback = threads.submit(() -> writers.executeCallback("owner", "exam", CallbackWriteFence.Lane.AZURE, () -> {
                writing.countDown(); await(release);
                mongo.insert(new Document("_id", "winner").append("exam_id", "exam"), "azure_results");
                return null;
            }));
            await(writing);
            try {
                assertThatThrownBy(() -> tx.executeWithoutResult(s -> fence.touchOwner("owner", NOW)))
                        .satisfies(CallbackWriteFenceMongoTest::assertWriteConflict);
            } finally { release.countDown(); }
            callback.get(15, TimeUnit.SECONDS);
            tx.executeWithoutResult(s -> fence.touchOwner("owner", NOW));
            assertThat(mongo.count(new Query(), "azure_results")).isEqualTo(1);
        } finally { release.countDown(); }
    }

    private static void assertWriteConflict(Throwable failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof com.mongodb.MongoException mongoFailure) {
                assertThat(mongoFailure.getCode()).isEqualTo(112);
                return;
            }
            cause = cause.getCause();
        }
        throw new AssertionError("Expected Mongo write conflict", failure);
    }

    @Test void activeWithdrawalRejectsCallbackWithoutOwnerWrite() {
        mongo.insert(new Document("_id", "owner").append("blockedUntil", java.util.Date.from(NOW.plusSeconds(60))), "withdrawn_user_access_denies");
        assertThatThrownBy(() -> writers.executeCallback("owner", "exam", CallbackWriteFence.Lane.AZURE, () -> null))
                .isInstanceOf(UserOwnershipGuardException.class);
    }

    @Test void scopeClearsAfterFailureAndOrdinaryWriterStillTouchesOwner() {
        assertThatThrownBy(() -> guards.callbackScope("owner", "exam", CallbackWriteFence.Lane.AZURE,
                () -> { throw new IllegalArgumentException("synthetic"); })).isInstanceOf(IllegalArgumentException.class);
        assertThat(fence.scoped()).isFalse();
        tx.executeWithoutResult(s -> guards.touchActive("owner", NOW));
        assertThat(mongo.findById("owner", Document.class, "user_ownership_guards").get("revision")).isEqualTo(1L);
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for test barrier"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
}
