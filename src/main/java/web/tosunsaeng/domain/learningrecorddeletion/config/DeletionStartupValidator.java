package web.tosunsaeng.domain.learningrecorddeletion.config;

import org.bson.Document;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;

/** Read-only startup gate. No index DDL, source backfill, remote calls, or implicit rollout approval. */
public final class DeletionStartupValidator implements InitializingBean {
    private final MongoTemplate mongo;
    private final Environment env;
    public DeletionStartupValidator(MongoTemplate mongo, Environment env) { this.mongo = mongo; this.env = env; }
    @Override public void afterPropertiesSet() {
        if (env.acceptsProfiles(Profiles.of("staging", "prod")) && !"jwt".equals(env.getProperty("app.auth.mode"))) fail();
        Document topology = mongo.executeCommand(new Document("hello", 1));
        if (topology.get("logicalSessionTimeoutMinutes") == null
                || (topology.get("setName") == null && !"isdbgrid".equals(topology.get("msg")))) fail();
        if (mongo.getCollection("learning_record_deletion_operations").find(new Document("activeGuard", true)
                .append("expiresAt", new Document("$ne", null))).projection(new Document("_id", 1)).first() != null) fail();
        for (String c : List.of("learning_record_billing_continuations", "learning_activity_daily_aggregates")) {
            if (mongo.collectionExists(c) && mongo.getCollection(c).listIndexes().into(new ArrayList<>()).stream()
                    .anyMatch(i -> i.get("expireAfterSeconds") != null)) fail();
        }
        Document manifest = mongo.findById("v1", Document.class, "learning_record_deletion_rollout");
        if (manifest == null || !Boolean.TRUE.equals(manifest.get("writersDrained"))
                || !Boolean.TRUE.equals(manifest.get("inventoryApproved"))
                || !Boolean.TRUE.equals(manifest.get("writerPathsVerified"))) fail();
        if (env.getProperty("app.learning-record-deletion.command-enabled", Boolean.class, false)
                && !Boolean.TRUE.equals(manifest.get("storageVerified"))) fail();
        index("learning_record_deletion_operations", new Document("userId", 1), true, new Document("activeGuard", true), false);
        index("learning_record_deletion_operations", new Document("activeGuard", 1).append("nextAttemptAt", 1).append("leaseUntil", 1), false, null, false);
        index("learning_record_deletion_operations", new Document("userId", 1).append("requestedAt", -1), false, null, false);
        index("learning_record_deletion_targets", new Document("targetType", 1).append("aggregateId", 1), false, null, false);
        index("learning_record_deletion_targets", new Document("deletionId", 1).append("targetType", 1).append("callbackFence", 1), false, null, false);
        for (String suffix : List.of("operations", "commands", "targets"))
            index("learning_record_deletion_" + suffix, new Document("expiresAt", 1), false, null, true);
        index("learning_record_billing_continuations", new Document("userId", 1).append("transferState", 1), false, null, false);
        for (String root : List.of("exam_sessions", "challenge_10s_attempts"))
            index(root, new Document("userId", 1).append("_id", 1), false, null, false);
        if (env.getProperty("app.learning-record-deletion.aggregate-enabled", Boolean.class, false)) {
            index("learning_activity_daily_aggregates", new Document("bucketDate", 1).append("metric", 1).append("examType", 1).append("outcome", 1), true, null, false);
            Document coverage = mongo.findById("v1", Document.class, "learning_activity_collection_coverage");
            if (coverage == null || coverage.get("liveStartedAt") == null || !"LEGACY_FIRST_EVENTS_UNCOVERED".equals(coverage.get("legacyPolicy"))) fail();
        }
    }
    private void index(String collection, Document keys, boolean unique, Document partial, boolean ttl) {
        boolean found = mongo.getCollection(collection).listIndexes().into(new ArrayList<>()).stream().anyMatch(i ->
                keys.equals(i.get("key")) && unique == Boolean.TRUE.equals(i.get("unique"))
                && Objects.equals(partial, i.get("partialFilterExpression"))
                && !Boolean.TRUE.equals(i.get("sparse")) && !Boolean.TRUE.equals(i.get("hidden")) && i.get("collation") == null
                && (ttl ? i.get("expireAfterSeconds") instanceof Number n && n.longValue() == 0 : i.get("expireAfterSeconds") == null));
        if (!found) fail();
    }
    private static void fail() { throw new IllegalStateException("Deletion startup prerequisites are not verified; keep deletion flags OFF"); }
}
