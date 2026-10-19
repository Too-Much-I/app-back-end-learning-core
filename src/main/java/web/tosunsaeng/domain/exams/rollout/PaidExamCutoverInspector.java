package web.tosunsaeng.domain.exams.rollout;

import com.mongodb.*;
import org.bson.Document;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Explicitly invoked, read-only preparation utility. No Spring bean, CLI, writer, sealing or activation. */
public final class PaidExamCutoverInspector {
    public static final String ENTRIES = "exam_legacy_manifest_entries";
    public static final String STATE = "exam_paid_rollout_state";
    public static final String STATE_ID = "legacy-v1";
    private static final List<String> BILLING_FIELDS = List.of("paidReservation", "creationOperationId",
            "billingReservationId", "attemptGroupId", "paidSchemaVersion", "entitlementSource");
    private final MongoTemplate mongo;
    private final TransactionTemplate transaction;

    public PaidExamCutoverInspector(MongoTemplate mongo) {
        this.mongo = mongo;
        transaction = new TransactionTemplate(new MongoTransactionManager(mongo.getMongoDatabaseFactory(),
                TransactionOptions.builder().readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY)
                        .readPreference(ReadPreference.primary()).build()));
        transaction.setTimeout(10);
    }

    /** Caller must separately prove writer drain; this boolean is an attestation, not an automated fence. */
    public LegacyExamManifest.Summary inspectCandidate(String environment, String revision, boolean writersDrained) {
        if (!writersDrained) throw LegacyExamManifest.invalid();
        try {
            if (!mongo.collectionExists("exam_sessions")) throw LegacyExamManifest.invalid();
            return transaction.execute(status -> {
                if (mongo.exists(Query.query(new Criteria().orOperator(Criteria.where("activeGuard").is(true),
                        Criteria.where("state").nin("SUCCEEDED", "CANCELED", "EXPIRED", "FAILED_TERMINAL"))),
                        "exam_creation_operations")) throw LegacyExamManifest.invalid();
                var sessions = mongo.find(new Query().limit(LegacyExamManifest.MAX_ENTRIES + 1), Document.class, "exam_sessions");
                sessions.forEach(PaidExamCutoverInspector::requireUnlinked);
                return LegacyExamManifest.build(environment, mongo.getDb().getName(), revision,
                        sessions.stream().map(d -> d.getString("_id")).toList()).summary();
            });
        } catch (RuntimeException failure) { throw LegacyExamManifest.invalid(); }
    }

    /** Expected summary must be a separately approved server artifact, never a client parameter. */
    public LegacyExamManifest verifySealed(LegacyExamManifest.Summary expected) {
        try {
            if (expected == null || !mongo.getDb().getName().equals(expected.database())) throw LegacyExamManifest.invalid();
            validateManifestIndexes();
            return transaction.execute(status -> {
                Document state = mongo.findById(STATE_ID, Document.class, STATE);
                if (state == null || !"SEALED".equals(state.getString("status"))
                        || !Boolean.TRUE.equals(state.get("writersDrained"))
                        || !Boolean.TRUE.equals(state.get("readerCoverageVerified"))
                        || !(state.get("sealedAt") instanceof Date)
                        || !expected.environment().equals(state.get("environment"))
                        || !expected.database().equals(state.get("database"))
                        || !expected.revision().equals(state.get("revision"))
                        || !expected.sha256().equals(state.get("sha256"))
                        || !Integer.valueOf(expected.count()).equals(state.get("count"))) throw LegacyExamManifest.invalid();
                var entries = mongo.find(new Query().limit(LegacyExamManifest.MAX_ENTRIES + 1), Document.class, ENTRIES);
                for (Document entry : entries) {
                    if (!entry.keySet().equals(Set.of("_id", "revision"))
                            || !expected.revision().equals(entry.get("revision"))) throw LegacyExamManifest.invalid();
                }
                var manifest = LegacyExamManifest.build(expected.environment(), expected.database(), expected.revision(),
                        entries.stream().map(d -> d.getString("_id")).toList());
                if (!expected.equals(manifest.summary())) throw LegacyExamManifest.invalid();
                // Deleted Sessions may remain in the inventory, but never recreate them or grant permission.
                for (Document session : mongo.find(Query.query(Criteria.where("_id").in(
                        entries.stream().map(d -> d.getString("_id")).toList())), Document.class, "exam_sessions"))
                    requireUnlinked(session);
                return manifest;
            });
        } catch (RuntimeException failure) { throw LegacyExamManifest.invalid(); }
    }

    public void validateManifestIndexes() {
        try {
            for (String collection : List.of(ENTRIES, STATE)) {
                var indexes = mongo.getCollection(collection).listIndexes().into(new ArrayList<>());
                var id = indexes.stream().filter(i -> "_id_".equals(i.getString("name"))).findFirst().orElseThrow();
                if (!new Document("_id", 1).equals(id.get("key")) || id.containsKey("partialFilterExpression")
                        || id.containsKey("sparse") || id.containsKey("collation") || Boolean.TRUE.equals(id.get("hidden"))
                        || indexes.stream().anyMatch(i -> i.containsKey("expireAfterSeconds"))) throw LegacyExamManifest.invalid();
            }
        } catch (RuntimeException failure) { throw LegacyExamManifest.invalid(); }
    }

    private static void requireUnlinked(Document session) {
        for (String field : BILLING_FIELDS) if (session.get(field) != null) throw LegacyExamManifest.invalid();
    }
}
