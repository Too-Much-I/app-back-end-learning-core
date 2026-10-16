package web.tosunsaeng.domain.exams.rollout;

import com.mongodb.client.*;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import web.tosunsaeng.domain.exams.application.PaidExamActivationGate;
import web.tosunsaeng.domain.exams.access.AccessEventRolloutGate;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Mongo manifest/index verification; not full HTTP/TLS or a joint Billing E2E. */
@Testcontainers
class PaidExamTestActivationMongoTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");
    static MongoClient client;
    MongoTemplate mongo;
    @BeforeAll static void connect() { client = MongoClients.create(MONGO.getReplicaSetUrl()); }
    @AfterAll static void close() { client.close(); }
    @BeforeEach void setup() {
        mongo = new MongoTemplate(client, "isolated_test_activation");
        mongo.getDb().drop();
        for (String collection : List.of("exam_sessions", "exam_creation_operations", "mock_exams",
                "exam_results", "exam_summaries", "user_ownership_guards", "user_merged_inbox_events",
                PaidExamCutoverInspector.STATE, PaidExamCutoverInspector.ENTRIES)) mongo.createCollection(collection);
        mongo.insert(new Document("_id", "legacy").append("active", true), "exam_sessions");
    }

    @Test void missingTestIndexesFailClosedWithoutInstallingAnything() {
        var before = mongo.getCollection("exam_creation_operations").listIndexes().into(new ArrayList<>());
        assertThatThrownBy(() -> new PaidExamTestIndexCheck(mongo, new MockEnvironment()).verify())
                .hasMessage("Paid exam test required index validation failed");
        assertThat(mongo.getCollection("exam_creation_operations").listIndexes().into(new ArrayList<>())).isEqualTo(before);
    }

    @Test void actualSealedManifestAndIndexesAllowTestGatesButDoNotWriteOrActivateSessions() {
        requiredIndexes(); var summary = seal();
        var before = mongo.findById("legacy", Document.class, "exam_sessions");
        context(summary).run(c -> assertThat(c).hasNotFailed().hasSingleBean(LegacyExamRuntime.class));
        assertThat(mongo.findById("legacy", Document.class, "exam_sessions")).isEqualTo(before);
        assertThat(mongo.getCollection("exam_creation_operations").countDocuments()).isZero();
    }

    @Test void approvalDoesNotBypassWrongHashOrMissingOwnerIndex() {
        requiredIndexes(); var summary = seal();
        context(summary).withPropertyValues("app.billing.paid-exam.legacy.sha256=" + "0".repeat(64))
                .run(c -> assertThat(c).hasFailed());
        mongo.getCollection("exam_results").dropIndex("idx_exam_results_user");
        context(summary).run(c -> assertThat(c).hasFailed());
    }

    private ApplicationContextRunner context(LegacyExamManifest.Summary summary) {
        var owner = mock(UserOwnedTransactionExecutor.class); when(owner.enabled()).thenReturn(true);
        return new ApplicationContextRunner().withUserConfiguration(LegacyExamRuntimeConfiguration.class,
                PaidExamTestActivation.class, PaidExamTestIndexCheck.class,
                PaidExamActivationGate.class, AccessEventRolloutGate.class)
                .withBean(MongoTemplate.class, () -> mongo).withBean(UserOwnedTransactionExecutor.class, () -> owner)
                .withInitializer(c -> c.getEnvironment().setActiveProfiles("test"))
                .withPropertyValues("app.billing.paid-exam.enabled=true", "app.billing.paid-exam.test-activation-approved=true",
                        "app.billing.creation-saga-enabled=true", "app.billing.workload-jwt.sender-enabled=true",
                        "app.billing.access-events.receiver-enabled=true", "app.user-merged.writer-enabled=true",
                        "app.billing.workload-jwt.environment=test", "app.billing.access-events.environment=test",
                        "app.billing.paid-exam.legacy.environment=test", "app.billing.paid-exam.legacy.database=" + summary.database(),
                        "app.billing.paid-exam.legacy.revision=" + summary.revision(),
                        "app.billing.paid-exam.legacy.sha256=" + summary.sha256(),
                        "app.billing.paid-exam.legacy.count=" + summary.count());
    }

    private LegacyExamManifest.Summary seal() {
        var summary = new PaidExamCutoverInspector(mongo).inspectCandidate("test", "gate-1", true);
        mongo.insert(new Document("_id", "legacy").append("revision", summary.revision()), PaidExamCutoverInspector.ENTRIES);
        mongo.insert(new Document("_id", PaidExamCutoverInspector.STATE_ID).append("status", "SEALED")
                .append("environment", "test").append("database", summary.database()).append("revision", summary.revision())
                .append("sha256", summary.sha256()).append("count", summary.count()).append("sealedAt", new Date())
                .append("writersDrained", true).append("readerCoverageVerified", true), PaidExamCutoverInspector.STATE);
        return summary;
    }

    private void index(String collection, String name, Document keys, boolean unique, Document partial, boolean ttl) {
        var options = new IndexOptions().name(name).unique(unique);
        if (partial != null) options.partialFilterExpression(partial);
        if (ttl) options.expireAfter(0L, TimeUnit.SECONDS);
        mongo.getCollection(collection).createIndex(keys, options);
    }

    private void requiredIndexes() {
        index("exam_creation_operations", "uniq_exam_creation_user_operation", new Document("userId", 1).append("operationId", 1), true, null, false);
        index("exam_creation_operations", "uniq_exam_creation_active_user", new Document("userId", 1).append("activeGuard", 1), true, new Document("activeGuard", true), false);
        index("exam_creation_operations", "idx_exam_creation_state_updated", new Document("state", 1).append("updatedAt", 1), false, null, false);
        index("exam_creation_operations", "ttl_exam_creation_purge", new Document("purgeAt", 1), false, new Document("purgeAt", new Document("$type", "date")), true);
        index("exam_sessions", "uniq_exam_sessions_creation_operation", new Document("userId", 1).append("creationOperationId", 1), true, new Document("creationOperationId", new Document("$type", "string")), false);
        index("exam_sessions", "uniq_exam_sessions_billing_reservation", new Document("billingReservationId", 1), true, new Document("billingReservationId", new Document("$type", "string")), false);
        index("exam_sessions", "idx_exam_sessions_attempt_group_created", new Document("attemptGroupId", 1).append("createdAt", 1), false, null, false);
        index("exam_sessions", "uniq_exam_sessions_active_user", new Document("userId", 1), true, new Document("active", true), false);
        index("mock_exams", "uniq_mock_exams_mock_exam_id", new Document("mock_exam_id", 1), true, null, false);
        index("exam_results", "idx_exam_results_user", new Document("userId", 1), false, null, false);
        index("exam_summaries", "idx_exam_summaries_user", new Document("userId", 1), false, null, false);
    }
}
