package web.tosunsaeng.domain.exams.rollout;

import com.mongodb.client.*;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class PaidExamCutoverMongoTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");
    static MongoClient client;
    MongoTemplate mongo;
    PaidExamCutoverInspector inspector;
    @BeforeAll static void connect() { client = MongoClients.create(MONGO.getReplicaSetUrl()); }
    @AfterAll static void close() { client.close(); }
    @BeforeEach void setup() {
        mongo = new MongoTemplate(client, "isolated_paid_cutover");
        for (String name : List.of("exam_sessions", "exam_creation_operations", PaidExamCutoverInspector.ENTRIES, PaidExamCutoverInspector.STATE)) {
            if (mongo.collectionExists(name)) mongo.dropCollection(name);
            mongo.createCollection(name);
        }
        mongo.insert(new Document("_id", "legacy").append("active", true), "exam_sessions");
        inspector = new PaidExamCutoverInspector(mongo);
    }
    LegacyExamManifest.Summary seal() {
        var summary = inspector.inspectCandidate("test", "r1", true);
        mongo.insert(new Document("_id", "legacy").append("revision", "r1"), PaidExamCutoverInspector.ENTRIES);
        mongo.insert(new Document("_id", PaidExamCutoverInspector.STATE_ID).append("status", "SEALED")
                .append("environment", summary.environment()).append("database", summary.database()).append("revision", "r1")
                .append("sha256", summary.sha256()).append("count", summary.count()).append("sealedAt", new Date())
                .append("writersDrained", true).append("readerCoverageVerified", true), PaidExamCutoverInspector.STATE);
        return summary;
    }
    @Test void inspectIsReadOnlyAndDoesNotChangeStatusMissingLegacy() {
        var before = mongo.findById("legacy", Document.class, "exam_sessions");
        assertThat(inspector.inspectCandidate("test", "r1", true).count()).isEqualTo(1);
        assertThat(mongo.findById("legacy", Document.class, "exam_sessions")).isEqualTo(before);
        assertThat(mongo.count(new Query(), PaidExamCutoverInspector.ENTRIES)).isZero();
        assertThat(mongo.count(new Query(), PaidExamCutoverInspector.STATE)).isZero();
    }
    @Test void drainAndUnresolvedOperationAreRequired() {
        assertThatThrownBy(() -> inspector.inspectCandidate("test", "r1", false)).isInstanceOf(IllegalStateException.class);
        mongo.insert(new Document("_id", "pending").append("state", "PREPARED"), "exam_creation_operations");
        assertThatThrownBy(() -> inspector.inspectCandidate("test", "r1", true)).isInstanceOf(IllegalStateException.class);
    }
    @Test void absentSourceCollectionIsNotAnEmptyApprovedInventory() {
        mongo.dropCollection("exam_sessions");
        assertThatThrownBy(() -> inspector.inspectCandidate("test", "r1", true)).hasMessage("Legacy manifest verification failed");
        assertThat(mongo.collectionExists("exam_sessions")).isFalse();
    }
    @Test void wrongDatabaseOrRevisionCannotReuseApprovedHash() {
        var expected = seal();
        var wrong = new LegacyExamManifest.Summary("test", "other_database", "r1", expected.sha256(), 1);
        assertThatThrownBy(() -> inspector.verifySealed(wrong)).hasMessage("Legacy manifest verification failed");
        mongo.updateFirst(new Query(), Update.update("revision", "r2"), PaidExamCutoverInspector.ENTRIES);
        assertThatThrownBy(() -> inspector.verifySealed(expected)).hasMessage("Legacy manifest verification failed");
    }
    @Test void billingLinkedCandidatesCannotBeSilentlyBackfilled() {
        for (String field : List.of("paidReservation", "creationOperationId", "billingReservationId", "attemptGroupId", "paidSchemaVersion", "entitlementSource")) {
            mongo.updateFirst(new Query(), Update.update(field, "present"), "exam_sessions");
            assertThatThrownBy(() -> inspector.inspectCandidate("test", "r1", true)).hasMessage("Legacy manifest verification failed");
            mongo.updateFirst(new Query(), new Update().unset(field), "exam_sessions");
        }
    }
    @Test void sealedReadExcludesLateWriterAndDoesNotRecreateDeletedSession() {
        var expected = seal();
        mongo.insert(new Document("_id", "late-no-source"), "exam_sessions");
        var manifest = inspector.verifySealed(expected);
        assertThat(manifest.containsLegacy(mongo.findById("legacy", ExamSession.class))).isTrue();
        assertThat(manifest.containsLegacy(mongo.findById("late-no-source", ExamSession.class))).isFalse();
        mongo.remove(Query.query(Criteria.where("_id").is("legacy")), "exam_sessions");
        assertThat(inspector.verifySealed(expected).containsLegacy(mongo.findById("legacy", ExamSession.class))).isFalse();
        assertThat(mongo.count(new Query(), "exam_sessions")).isEqualTo(1);
    }
    @Test void changedMissingAndExtraEntriesFailClosed() {
        var expected = seal();
        mongo.insert(new Document("_id", "injected").append("revision", "r1"), PaidExamCutoverInspector.ENTRIES);
        assertThatThrownBy(() -> inspector.verifySealed(expected)).isInstanceOf(IllegalStateException.class);
        mongo.remove(new Query(), PaidExamCutoverInspector.ENTRIES);
        assertThatThrownBy(() -> inspector.verifySealed(expected)).isInstanceOf(IllegalStateException.class);
    }
    @Test void unsealedOrUnapprovedStateFailsClosed() {
        var expected = seal();
        for (String field : List.of("writersDrained", "readerCoverageVerified", "sealedAt", "sha256", "revision", "count")) {
            var state = mongo.findById(PaidExamCutoverInspector.STATE_ID, Document.class, PaidExamCutoverInspector.STATE);
            mongo.updateFirst(new Query(), new Update().unset(field), PaidExamCutoverInspector.STATE);
            assertThatThrownBy(() -> inspector.verifySealed(expected)).hasMessage("Legacy manifest verification failed").hasNoCause();
            mongo.save(state, PaidExamCutoverInspector.STATE);
        }
    }
    @Test void paidSessionCannotReuseLegacyIdentity() {
        var expected = seal();
        mongo.updateFirst(new Query(), Update.update("paidSchemaVersion", 1), "exam_sessions");
        assertThatThrownBy(() -> inspector.verifySealed(expected)).isInstanceOf(IllegalStateException.class);
    }
    @Test void ttlAndMissingCollectionFailWithoutInstallingIndexes() {
        var expected = seal();
        mongo.getCollection(PaidExamCutoverInspector.ENTRIES).createIndex(new Document("expiresAt", 1), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));
        assertThatThrownBy(() -> inspector.verifySealed(expected)).isInstanceOf(IllegalStateException.class);
        mongo.dropCollection(PaidExamCutoverInspector.ENTRIES);
        assertThatThrownBy(() -> inspector.verifySealed(expected)).isInstanceOf(IllegalStateException.class);
        assertThat(mongo.collectionExists(PaidExamCutoverInspector.ENTRIES)).isFalse();
    }
}
