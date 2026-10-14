package web.tosunsaeng.domain.answerlearning;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import web.tosunsaeng.domain.usermerge.application.*;
import web.tosunsaeng.domain.usermerge.config.UserMergedProperties;
import web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static web.tosunsaeng.domain.answerlearning.LearningModels.*;

/** Uses the real production owner fence and replica-set transactions, not an unguarded store. */
@Testcontainers
class LearningQuotaTransactionTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");
    static MongoClient client;
    static final Instant NOW = Instant.parse("2026-10-13T05:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final String OWNER = "20000000-0000-4000-8000-000000000002";
    MongoTemplate mongo;
    LearningStore store;

    @BeforeAll static void connect() { client = MongoClients.create(MONGO.getReplicaSetUrl()); }
    @AfterAll static void close() { client.close(); }
    @BeforeEach void setup() {
        var factory = new SimpleMongoClientDatabaseFactory(client, "isolated-learning-quota");
        mongo = new MongoTemplate(factory);
        for (String c : List.of("learning_submissions", "learning_completions", "learning_submission_limits", "user_ownership_guards")) mongo.dropCollection(c);
        var transactions = new TransactionTemplate(new MongoTransactionManager(factory));
        var beans = new StaticListableBeanFactory(); beans.addBean("transactions", transactions);
        var props = new UserMergedProperties(); props.setWriterEnabled(true);
        var repository = new MongoRepositoryFactory(mongo).getRepository(UserOwnershipGuardRepository.class);
        var guards = new UserOwnedTransactionExecutor(props, new UserOwnershipGuardService(mongo, repository),
                beans.getBeanProvider(TransactionOperations.class), CLOCK);
        store = new LearningStore(mongo, guards); store.indexes();
        mongo.insert(UserOwnershipGuard.active(OWNER, NOW));
    }
    Submission fixture() {
        var s = new Submission(); s.id = UUID.randomUUID().toString(); s.userId = OWNER;
        s.lessonId = "lesson"; s.level = "IH"; s.contentVersion = "v1";
        s.createdAt = NOW; s.updatedAt = NOW; s.queuedAt = NOW;
        return s;
    }
    int count() { return mongo.findAll(Document.class, "learning_submission_limits").getFirst().getInteger("count"); }
    <T> List<T> race(Supplier<T> action) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var a = pool.submit(() -> { gate.await(); return action.get(); });
            var b = pool.submit(() -> { gate.await(); return action.get(); });
            gate.countDown();
            return Arrays.asList(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }
    @Test void concurrentServiceReplayChargesOneSubmissionEvenAtTheDailyLimit() throws Exception {
        var gate = new CyclicBarrier(2);
        var catalog = mock(LearningCatalog.class);
        doAnswer(i -> { gate.await(10, TimeUnit.SECONDS); return null; }).when(catalog).version("v1");
        when(catalog.lesson("lesson")).thenReturn(new Document("part", 2));
        when(catalog.checklist(any())).thenReturn(new Document("version", "check-v1"));
        var config = new LearningProperties(); config.setGradingEnabled(true); config.setDailySubmissions(1);
        var service = new LearningService(catalog, store, mock(LearningStorage.class), config, CLOCK);
        var request = new Create(UUID.randomUUID().toString(), "lesson", "IH", "v1");
        var replies = race(() -> service.create(OWNER, request));
        assertThat(replies).allMatch(r -> r.id().equals(request.submissionId()));
        assertThat(mongo.count(new Query(), Submission.class)).isEqualTo(1);
        assertThat(count()).isEqualTo(1);
        assertThat(service.create(OWNER, request).id()).isEqualTo(request.submissionId());
        assertThat(count()).isEqualTo(1);
    }
    @Test void quotaRejectionRollsBackNewSubmission() {
        store.create(fixture(), 1); var denied = fixture();
        assertThatThrownBy(() -> store.create(denied, 1)).hasMessage("LEARNING_RATE_LIMITED");
        assertThat(store.find(denied.id)).isNull();
        assertThat(count()).isEqualTo(1);
    }
    @Test void concurrentDifferentSubmissionsCannotTakeTheSameLastSlot() throws Exception {
        store.create(fixture(), 2);
        var accepted = race(() -> {
            try { store.create(fixture(), 2); return true; }
            catch (LearningFailure e) { assertThat(e.status).isEqualTo(429); return false; }
        });
        assertThat(accepted).containsExactlyInAnyOrder(true, false);
        assertThat(count()).isEqualTo(2);
        assertThat(mongo.count(new Query(), Submission.class)).isEqualTo(2);
    }
    @Test void concurrentRetryChargesOnlyTheWinningAttemptTransition() throws Exception {
        var s = fixture(); s.status = "failed"; s.error = new LearningModels.Error("MODEL_FAILED", "fixture", true);
        store.create(s, 2);
        var replies = race(() -> store.retry(s, NOW, 2));
        assertThat(replies.stream().filter(Objects::nonNull).count()).isEqualTo(1);
        assertThat(store.find(s.id).attempt).isEqualTo(2);
        assertThat(store.find(s.id).status).isEqualTo("queued");
        assertThat(count()).isEqualTo(2);
        assertThat(store.retry(s, NOW, 2)).isNull();
        assertThat(count()).isEqualTo(2);
    }
    @Test void quotaRejectionRollsBackAttemptAndKeepsRetryableFailure() {
        var s = fixture(); s.status = "failed"; s.error = new LearningModels.Error("MODEL_FAILED", "fixture", true);
        store.create(s, 1);
        assertThatThrownBy(() -> store.retry(s, NOW, 1)).hasMessage("LEARNING_RATE_LIMITED");
        var kept = store.find(s.id);
        assertThat(kept.attempt).isEqualTo(1);
        assertThat(kept.status).isEqualTo("failed");
        assertThat(kept.error.retryable()).isTrue();
        assertThat(count()).isEqualTo(1);
    }
}
