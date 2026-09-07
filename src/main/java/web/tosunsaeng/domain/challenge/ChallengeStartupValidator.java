package web.tosunsaeng.domain.challenge;

import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import java.util.*;

/** Startup verifies DDL, never creates it. Apply the reviewed migration separately before enabling. */
public class ChallengeStartupValidator {
    public record RequiredIndex(String collection, String name, Document keys, boolean unique) {}
    public static final List<RequiredIndex> INDEXES = List.of(
            index("questions", "challenge_day_unique", true, "dayNumber"),
            index("questions", "challenge_question_id_unique", true, "questions.questionId"),
            index("attempts", "challenge_owner_date_question_unique", true, "userId", "challengeDate", "questionNumber"),
            index("attempts", "challenge_expiry", false, "state", "submissionDeadlineAt", "_id"),
            index("submit_receipts", "challenge_submit_key_unique", true, "userId", "idempotencyKey"),
            index("grading_jobs", "challenge_generation_unique", true, "attemptId", "generation"),
            index("grading_jobs", "challenge_dispatch_due", false, "state", "nextAttemptAt", "_id"),
            index("grading_jobs", "challenge_lease_due", false, "state", "leaseUntil"),
            index("grading_jobs", "challenge_callback_due", false, "state", "callbackDeadlineAt")
    );
    public static final List<String> COLLECTIONS = List.of("questions", "catalog_state", "attempts", "submit_receipts", "grading_jobs", "callback_receipts")
            .stream().map(s -> "challenge_10s_" + s).toList();
    private final ChallengeStore store;
    private final ChallengeTransactions tx;
    private final ChallengeCatalog catalog;
    public ChallengeStartupValidator(ChallengeStore store, ChallengeTransactions tx, ChallengeCatalog catalog) { this.store = store; this.tx = tx; this.catalog = catalog; }
    public void validate() {
        try {
            for (String collection : COLLECTIONS) if (!store.mongo.collectionExists(collection)) throw new IllegalStateException("Challenge collection missing");
            for (RequiredIndex expected : INDEXES) {
                List<Document> indexes = store.mongo.getCollection(expected.collection()).listIndexes().into(new ArrayList<>());
                boolean found = indexes.stream().anyMatch(i -> expected.name().equals(i.getString("name"))
                        && i.get("key") instanceof Document keys && new ArrayList<>(expected.keys().entrySet()).equals(new ArrayList<>(keys.entrySet()))
                        && expected.unique() == Boolean.TRUE.equals(i.get("unique")) && !Boolean.TRUE.equals(i.get("hidden"))
                        && !i.containsKey("partialFilterExpression") && !Boolean.TRUE.equals(i.get("sparse"))
                        && !i.containsKey("expireAfterSeconds") && !i.containsKey("collation"));
                if (!found) throw new IllegalStateException("Challenge required index missing or incompatible");
            }
            // No TTL is permitted to delete attempts, receipts, pending jobs or the one-time base date.
            for (String collection : COLLECTIONS) for (Document index : store.mongo.getCollection(collection).listIndexes())
                if (index.containsKey("expireAfterSeconds")) throw new IllegalStateException("Unexpected Challenge TTL index");
            String probeId = "probe:" + UUID.randomUUID();
            try {
                tx.run(null, () -> { store.mongo.insert(new Document("_id", probeId), ChallengeCatalog.STATE); throw new ProbeRollback(); });
            } catch (ProbeRollback expected) { /* Verify rollback outside the ended transaction. */ }
            if (store.mongo.exists(Query.query(Criteria.where("_id").is(probeId)), ChallengeCatalog.STATE)) throw new IllegalStateException("Challenge transaction rollback failed");
            catalog.initialize();
        } catch (RuntimeException failure) {
            // Do not leak Mongo connection strings or catalog text through an exception cause.
            throw new IllegalStateException("Challenge startup validation failed; verify configuration, migration, replica-set and catalog");
        }
    }
    private static RequiredIndex index(String suffix, String name, boolean unique, String... keys) {
        Document document = new Document(); for (String key : keys) document.append(key, 1);
        return new RequiredIndex("challenge_10s_" + suffix, name, document, unique);
    }
    private static class ProbeRollback extends RuntimeException {}
}
