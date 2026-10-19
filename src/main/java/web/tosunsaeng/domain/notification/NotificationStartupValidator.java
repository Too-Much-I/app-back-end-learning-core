package web.tosunsaeng.domain.notification;

import org.bson.Document;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;

public class NotificationStartupValidator {
    public record Spec(String collection, String name, Document keys, boolean unique, boolean sparse, Long ttl) {}
    public static final List<String> COLLECTIONS = List.of("notification_devices", "exam_submission_receipts",
            "daily_exam_reminder_deliveries", "daily_reminder_suppressions", "notification_control", "user_ownership_guards");
    private static Spec index(String c, String n, boolean unique, boolean sparse, Long ttl, String... keys) {
        Document d = new Document(); for (String key : keys) d.append(key, 1); return new Spec(c, n, d, unique, sparse, ttl);
    }
    public static final List<Spec> INDEXES = List.of(
            index("notification_devices", "notification_token_unique", true, true, null, "tokenHash"),
            index("notification_devices", "notification_candidates", false, false, null, "active", "accountType", "permission", "_id"),
            index("notification_devices", "notification_owner", false, false, null, "userId"),
            index("notification_devices", "notification_stale", false, false, null, "active", "lastSeenAt"),
            index("exam_submission_receipts", "submission_question_unique", true, false, null, "examId", "questionNumber"),
            index("exam_submission_receipts", "submission_receipt_ttl", false, false, 0L, "expiresAt"),
            index("exam_sessions", "notification_submission_date", false, false, null, "userId", "submissionCompletedAt", "createdAt"),
            index("daily_exam_reminder_deliveries", "reminder_daily_device_unique", true, false, null, "userId", "dateKst", "type", "installationId"),
            index("daily_exam_reminder_deliveries", "reminder_stale_sending", false, false, null, "status", "attemptedAt"),
            index("daily_exam_reminder_deliveries", "reminder_history_ttl", false, false, 0L, "expiresAt"),
            index("daily_reminder_suppressions", "reminder_suppression_ttl", false, false, 0L, "expiresAt"),
            index("notification_control", "notification_control_ttl", false, false, 0L, "expiresAt")
    );
    private final MongoTemplate mongo;
    private final NotificationTransactions tx;
    private final NotificationProperties properties;
    private final Environment environment;
    public NotificationStartupValidator(MongoTemplate mongo, NotificationTransactions tx, NotificationProperties properties, Environment environment) {
        this.mongo = mongo; this.tx = tx; this.properties = properties; this.environment = environment;
    }
    public void validate() {
        validateSettings(properties, environment);
        for (String name : COLLECTIONS) if (!mongo.collectionExists(name)) throw new IllegalStateException("Run notification index preparation first");
        for (Spec spec : INDEXES) {
            List<Document> actual = mongo.getCollection(spec.collection()).listIndexes().into(new ArrayList<>());
            if (actual.stream().noneMatch(d -> compatible(d, spec))) throw new IllegalStateException("Missing or incompatible notification index: " + spec.name());
        }
        try {
            tx.run(() -> { mongo.insert(new Document("_id", "probe:" + UUID.randomUUID()), NotificationStore.CONTROL); throw new ProbeRollback(); });
        } catch (ProbeRollback expected) { /* All writes rolled back; no feature-specific manager nesting. */ }
    }
    static void validateSettings(NotificationProperties p, Environment env) {
        if (p.getBatchSize() < 1 || p.getBatchSize() > 100) throw new IllegalStateException("notification batchSize must be 1..100");
        if ((p.isApiEnabled() || p.isSendingEnabled()) && !"jwt".equals(env.getProperty("app.auth.mode")))
            throw new IllegalStateException("Notification API/sender requires JWT mode");
        if (p.isSendingEnabled()) {
            if (!p.isApiEnabled() || !p.isTrackingEnabled() || p.getTrackingReadyAt() == null)
                throw new IllegalStateException("Notification sender requires API and new-server submission tracking readiness");
            for (String flag : List.of("app.user-merged.writer-enabled", "app.user-merged.consumer-enabled", "app.user-merged.source-deny-enabled",
                    "app.user-withdrawn.consumer-enabled", "app.user-withdrawn.deny-gate-enabled"))
                if (!env.getProperty(flag, Boolean.class, false)) throw new IllegalStateException("Notification sender requires lifecycle gates: " + flag);
        }
    }
    static boolean compatible(Document d, Spec s) {
        Object ttl = d.get("expireAfterSeconds");
        return s.name().equals(d.getString("name")) && s.keys().equals(d.get("key"))
                && s.unique() == Boolean.TRUE.equals(d.getBoolean("unique")) && s.sparse() == Boolean.TRUE.equals(d.getBoolean("sparse"))
                && !Boolean.TRUE.equals(d.getBoolean("hidden")) && !d.containsKey("partialFilterExpression") && !d.containsKey("collation")
                && (s.ttl() == null ? ttl == null : ttl instanceof Number n && n.longValue() == s.ttl());
    }
    private static class ProbeRollback extends RuntimeException {}
}
