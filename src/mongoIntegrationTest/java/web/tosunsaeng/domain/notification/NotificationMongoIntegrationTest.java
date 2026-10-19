package web.tosunsaeng.domain.notification;

import com.mongodb.client.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardService;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class NotificationMongoIntegrationTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");
    static MongoClient client;
    static SimpleMongoClientDatabaseFactory factory;
    static final String OWNER = "30000000-0000-4000-8000-000000000001";
    static final String TARGET = "30000000-0000-4000-8000-000000000002";
    static final String PROOF = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    MongoTemplate mongo;
    NotificationStore store;
    NotificationTransactions tx;
    NotificationProperties p;
    NotificationSubmissionTracker tracker;
    MutableClock clock;
    FakeGateway gateway;
    DailyReminderWorker worker;
    @BeforeAll static void connect() { client = MongoClients.create(MONGO.getReplicaSetUrl()); factory = new SimpleMongoClientDatabaseFactory(client, "isolated-notification-it"); }
    @AfterAll static void close() { client.close(); }
    @BeforeEach void setup() {
        mongo = new MongoTemplate(factory);
        for (String c : List.of("notification_devices", "exam_submission_receipts", "daily_exam_reminder_deliveries", "daily_reminder_suppressions",
                "notification_control", "user_ownership_guards", "exam_sessions", "withdrawn_user_access_denies", "learning_record_deletion_operations")) {
            if (!mongo.collectionExists(c)) mongo.createCollection(c); mongo.remove(new Query(), c);
        }
        for (var spec : NotificationStartupValidator.INDEXES) {
            var options = new com.mongodb.client.model.IndexOptions().name(spec.name()).unique(spec.unique()).sparse(spec.sparse());
            if (spec.ttl() != null) options.expireAfter(spec.ttl(), TimeUnit.SECONDS);
            mongo.getCollection(spec.collection()).createIndex(spec.keys(), options);
        }
        clock = new MutableClock(NOW); tx = new NotificationTransactions(factory);
        var guards = new UserOwnershipGuardService(mongo, new MongoRepositoryFactory(mongo).getRepository(UserOwnershipGuardRepository.class));
        store = new NotificationStore(mongo, guards, clock); p = new NotificationProperties();
        p.setTrackingEnabled(true); p.setSendingEnabled(true); p.setDryRun(false); p.setTrackingReadyAt(NOW.minusSeconds(3 * 86400));
        tracker = new NotificationSubmissionTracker(mongo, tx, p, store);
        gateway = new FakeGateway(); worker = new DailyReminderWorker(store, tx, p, gateway, clock, new SimpleMeterRegistry());
    }
    String register(String owner) { return register(owner, "MEMBER", "AUTHORIZED"); }
    String register(String owner, String type, String permission) {
        String id = UUID.randomUUID().toString();
        tx.run(() -> { store.register(owner, type, id, PROOF, "IOS", "fixture-token-" + id, permission, clock.instant()); return null; }); return id;
    }
    void session(String id) {
        mongo.insert(ExamSession.builder().examId(id).userId(OWNER).createdAt(LocalDateTime.ofInstant(NOW.minusSeconds(86400), ZoneOffset.UTC))
                .active(true).build());
    }
    void accepted(String id, int q, int retry) { tx.run(() -> { tracker.accepted(id, q, retry, List.of(1, 2), clock.instant()); return null; }); }
    Document delivery(String device) { return mongo.findById(NotificationStore.deliveryId(OWNER, ReminderPolicy.day(NOW), device), Document.class, NotificationStore.DELIVERIES); }

    @Test void neverStartedAndPartialIncludedButAllInitialSubmissionsExcludedWithoutGrading() {
        register(OWNER); worker.tick(); assertThat(gateway.calls.get()).isEqualTo(1);
        session("exam"); accepted("exam", 1, 0); String second = register(OWNER); worker.tick(); worker.tick(); assertThat(gateway.calls.get()).isEqualTo(2);
        accepted("exam", 2, 1); assertThat(mongo.findById("exam", ExamSession.class).getSubmissionCompletedAt()).isNull();
        accepted("exam", 2, 0); register(OWNER); worker.tick(); worker.tick(); assertThat(gateway.calls.get()).isEqualTo(2);
        assertThat(mongo.findById("exam", ExamSession.class).getSubmissionCompletedAt()).isEqualTo(NOW);
        assertThat(delivery(second).getString("status")).isEqualTo("ACCEPTED");
    }
    @Test void allDevicesNoResendAfterFailureUnknownTokenRotationAndRestart() {
        String a = register(OWNER), b = register(OWNER), c = register(OWNER);
        gateway.result = new PushGateway.Result(PushGateway.Status.FAILED, false, false, 0); worker.process(a);
        gateway.result = PushGateway.Result.unknown(); worker.process(b);
        gateway.result = new PushGateway.Result(PushGateway.Status.ACCEPTED, false, false, 0); worker.process(c);
        tx.run(() -> { store.register(OWNER, "MEMBER", a, PROOF, "IOS", "rotated-fixture-token-123456", "AUTHORIZED", NOW); return null; });
        var restarted = new DailyReminderWorker(store, tx, p, gateway, clock, new SimpleMeterRegistry());
        restarted.tick(); restarted.tick(); assertThat(gateway.calls.get()).isEqualTo(3);
        assertThat(delivery(a).getString("status")).isEqualTo("FAILED"); assertThat(delivery(b).getString("status")).isEqualTo("UNKNOWN");
        assertThat(delivery(c).keySet()).doesNotContain("pushToken", "tokenHash");
        register(OWNER); restarted.tick(); restarted.tick(); assertThat(gateway.calls.get()).isEqualTo(4);
    }
    @Test void crashAfterMarkerNeverResendsAndRetainsThirtyDays() {
        String id = register(OWNER); tx.run(() -> store.claim(id, NOW, p.getTrackingReadyAt()));
        clock.at(NOW.plusSeconds(61)); worker.tick();
        assertThat(gateway.calls.get()).isZero(); assertThat(delivery(id).getString("status")).isEqualTo("UNKNOWN");
        assertThat(delivery(id).getDate("expiresAt").toInstant()).isEqualTo(ReminderPolicy.expiry(ReminderPolicy.day(NOW)));
    }
    @Test void concurrentWorkersReserveOnlyOneAttempt() throws Exception {
        String id = register(OWNER);
        var secondWorker = new DailyReminderWorker(store, tx, p, gateway, clock, new SimpleMeterRegistry());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(worker::tick); var b = pool.submit(secondWorker::tick);
            a.get(); b.get();
        }
        worker.process(id);
        assertThat(gateway.calls.get()).isEqualTo(1);
        assertThat(mongo.count(new Query(), NotificationStore.DELIVERIES)).isEqualTo(1);
    }
    @Test void unknownCommitOfMarkerAndPostSendDatabaseFailureNeverResend() {
        String id = register(OWNER);
        NotificationTransactions uncertain = new NotificationTransactions(factory) {
            @Override public <T> T run(java.util.function.Supplier<T> command) {
                super.run(command);
                var unknown = new com.mongodb.MongoException(91, "fixture acknowledgement lost");
                unknown.addLabel(com.mongodb.MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL); throw unknown;
            }
        };
        var uncertainWorker = new DailyReminderWorker(store, uncertain, p, gateway, clock, new SimpleMeterRegistry());
        uncertainWorker.tick(); worker.process(id); assertThat(gateway.calls.get()).isZero();
        clock.at(NOW.plusSeconds(61)); worker.tick(); assertThat(delivery(id).getString("status")).isEqualTo("UNKNOWN");
        String second = register(OWNER);
        var failingStore = org.mockito.Mockito.spy(store);
        org.mockito.Mockito.doThrow(new IllegalStateException("fixture storage failure")).when(failingStore)
                .finish(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        var failingWorker = new DailyReminderWorker(failingStore, tx, p, gateway, clock, new SimpleMeterRegistry());
        failingWorker.tick(); worker.process(second); assertThat(gateway.calls.get()).isEqualTo(1);
        clock.at(NOW.plusSeconds(122)); worker.tick(); assertThat(delivery(second).getString("status")).isEqualTo("UNKNOWN");
    }
    @Test void concurrentFinalSubmissionsAndReceiptRollback() throws Exception {
        session("exam");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> accepted("exam", 1, 0)); var b = pool.submit(() -> accepted("exam", 2, 0)); a.get(); b.get();
        }
        assertThat(mongo.findById("exam", ExamSession.class).getSubmissionCompletedAt()).isEqualTo(NOW);
        clock.at(NOW.plusSeconds(86400)); accepted("exam", 1, 0);
        assertThat(mongo.findById("exam", ExamSession.class).getSubmissionCompletedAt()).isEqualTo(NOW);
        session("rollback");
        assertThatThrownBy(() -> tx.run(() -> { tracker.accepted("rollback", 1, 0, List.of(1), NOW); throw new IllegalStateException("fixture rollback"); })).isInstanceOf(IllegalStateException.class);
        assertThat(mongo.exists(Query.query(Criteria.where("examId").is("rollback")), "exam_submission_receipts")).isFalse();
        assertThat(mongo.findById("rollback", ExamSession.class).getSubmissionCompletedAt()).isNull();
    }
    @Test void kstLastSubmissionDateAndPriorCompletionDoesNotSuppressToday() {
        session("exam"); clock.at(Instant.parse("2026-10-06T14:59:59Z")); accepted("exam", 1, 0);
        clock.at(Instant.parse("2026-10-06T15:00:00Z")); accepted("exam", 2, 0);
        assertThat(store.completed(OWNER, LocalDate.of(2026, 10, 7))).isTrue();
        assertThat(store.completed(OWNER, LocalDate.of(2026, 10, 6))).isFalse();
        assertThat(store.completed(OWNER, LocalDate.of(2026, 10, 8))).isFalse();
    }

    @Test void receiptsExpireSeventyTwoHoursAfterCompletionOnlyAndNeverReappear() {
        session("retention");
        accepted("retention", 1, 0);
        clock.at(NOW.plus(Duration.ofDays(10)));
        var first = mongo.findById("retention:1", Document.class, "exam_submission_receipts");
        assertThat(first.containsKey("expiresAt")).isFalse();
        accepted("retention", 2, 1);
        assertThat(mongo.findById("retention:1", Document.class, "exam_submission_receipts").containsKey("expiresAt")).isFalse();
        accepted("retention", 2, 0);
        Instant completedAt = clock.instant();
        var query = Query.query(Criteria.where("examId").is("retention"));
        assertThat(mongo.find(query, Document.class, "exam_submission_receipts")).hasSize(2).allSatisfy(d ->
                assertThat(d.getDate("expiresAt").toInstant()).isEqualTo(completedAt.plus(Duration.ofHours(72))));
        clock.at(completedAt.plus(Duration.ofDays(1)));
        accepted("retention", 1, 0);
        assertThat(mongo.findById("retention:1", Document.class, "exam_submission_receipts").getDate("expiresAt").toInstant())
                .isEqualTo(completedAt.plus(Duration.ofHours(72)));
        // Model the TTL monitor without a real 72-hour wait; Session must survive and replay must be a no-op.
        mongo.remove(query, "exam_submission_receipts");
        accepted("retention", 1, 0);
        assertThat(mongo.count(query, "exam_submission_receipts")).isZero();
        assertThat(mongo.findById("retention", ExamSession.class).getSubmissionCompletedAt()).isEqualTo(completedAt);
        assertThat(store.completed(OWNER, ReminderPolicy.day(completedAt))).isTrue();
    }
    @Test void guestDeniedUnknownAndOutsideWindowNeverSend() {
        register(OWNER, "GUEST", "AUTHORIZED"); register(OWNER, "MEMBER", "DENIED"); register(OWNER, "MEMBER", "UNKNOWN");
        worker.tick(); assertThat(gateway.calls.get()).isZero();
        register(OWNER); clock.at(NOW.plusSeconds(900)); worker.tick(); assertThat(gateway.calls.get()).isZero();
        clock.at(NOW.minusSeconds(1)); worker.tick(); assertThat(gateway.calls.get()).isZero();
    }
    @Test void deletionMergeWithdrawalAndRevocationSuppressClaims() {
        String id = register(OWNER);
        var claim = tx.run(() -> store.claim(id, NOW, p.getTrackingReadyAt()));
        tx.run(() -> { store.unregister(OWNER, id, PROOF); store.unregister(OWNER, id, PROOF); return null; });
        assertThat(store.stillEligible(claim, NOW, p.getTrackingReadyAt())).isFalse();
        register(OWNER); tx.run(() -> { store.suppress(OWNER, NOW, "LEARNING_RECORD_DELETION"); return null; }); worker.tick(); assertThat(gateway.calls.get()).isZero();
        String target = register(TARGET);
        tx.run(() -> { store.merged(OWNER, TARGET, NOW); return null; }); assertThat(store.eligible(store.device(target), NOW, p.getTrackingReadyAt())).isFalse();
        tx.run(() -> { store.withdrawn(TARGET, NOW); return null; });
        assertThat(store.device(target).containsKey("pushToken")).isFalse();
    }
    @Test void wrongInstallationProofAndTokenCollisionCannotStealDevice() {
        String id = register(OWNER);
        String wrong = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]).replace('A', 'B');
        assertThatThrownBy(() -> tx.run(() -> { store.register(TARGET, "MEMBER", id, wrong, "IOS", "another-fixture-token-12345", "AUTHORIZED", NOW); return null; }))
                .isInstanceOf(NotificationFailure.class);
        assertThatThrownBy(() -> tx.run(() -> { store.register(TARGET, "MEMBER", UUID.randomUUID().toString(), PROOF, "IOS", "fixture-token-" + id, "AUTHORIZED", NOW); return null; }))
                .isInstanceOf(NotificationFailure.class);
        assertThat(store.device(id).getString("userId")).isEqualTo(OWNER);
    }
    @Test void unknownLegacyExamDoesNotSuppressReminder() {
        String id = register(OWNER); session("legacy");
        mongo.updateFirst(Query.query(Criteria.where("_id").is("legacy")), Update.update("createdAt", Date.from(p.getTrackingReadyAt().minusSeconds(1))), "exam_sessions");
        worker.tick(); assertThat(gateway.calls.get()).isEqualTo(1);
    }
    @Test void activeDeletionStillFailsClosed() {
        String id = register(OWNER);
        mongo.insert(new Document("_id", "delete").append("userId", OWNER).append("activeGuard", true), "learning_record_deletion_operations");
        worker.process(id); assertThat(gateway.calls.get()).isZero();
    }
    @Test void permissionChangeAndTokenChangeAfterClaimCancelOrProtectNewToken() {
        String id = register(OWNER); var claim = tx.run(() -> store.claim(id, NOW, p.getTrackingReadyAt()));
        tx.run(() -> { store.register(OWNER, "MEMBER", id, PROOF, "IOS", "new-fixture-token-123456", "DENIED", NOW); return null; });
        assertThat(store.stillEligible(claim, NOW, p.getTrackingReadyAt())).isFalse();
        store.finish(claim, new PushGateway.Result(PushGateway.Status.FAILED, true, false, 0), NOW);
        assertThat(store.device(id).getString("pushToken")).isEqualTo("new-fixture-token-123456");
    }
    @Test void quotaAndAuthStopOtherDevicesButNeverRetryAttemptedOne() {
        String first = register(OWNER), second = register(OWNER);
        gateway.result = new PushGateway.Result(PushGateway.Status.FAILED, false, false, 120); worker.process(first);
        worker.tick(); assertThat(gateway.calls.get()).isEqualTo(1);
        clock.at(NOW.plusSeconds(121)); gateway.result = new PushGateway.Result(PushGateway.Status.FAILED, false, true, 0); worker.process(second);
        register(OWNER); worker.tick(); assertThat(gateway.calls.get()).isEqualTo(2); assertThat(store.paused(clock.instant())).isTrue();
    }
    static class FakeGateway implements PushGateway {
        AtomicInteger calls = new AtomicInteger();
        Result result = new Result(Status.ACCEPTED, false, false, 0);
        public void prepare() {}
        public Result send(String token, String id, Instant expiry) { calls.incrementAndGet(); return result; }
    }
    static class MutableClock extends Clock {
        private volatile Instant value; MutableClock(Instant value) { this.value = value; }
        void at(Instant value) { this.value = value; }
        public ZoneId getZone() { return ZoneOffset.UTC; } public Clock withZone(ZoneId zone) { return this; } public Instant instant() { return value; }
    }
}
