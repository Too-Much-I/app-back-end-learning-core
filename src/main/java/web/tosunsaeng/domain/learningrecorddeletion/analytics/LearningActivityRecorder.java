package web.tosunsaeng.domain.learningrecorddeletion.analytics;

import org.bson.Document;
import org.springframework.data.mongodb.MongoDatabaseUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import java.time.*;
import java.util.*;
import static web.tosunsaeng.domain.learningrecorddeletion.analytics.LearningActivityMetric.*;

/** Source metadata and anonymous counters are committed in the SAME Mongo transaction. */
public final class LearningActivityRecorder {
    public static final String COLLECTION = "learning_activity_daily_aggregates";
    public static final String MARKERS = "learningActivity";
    public static final Set<String> SOURCES = Set.of("exam_sessions", "question_grading_jobs", "summary_grading_jobs",
            "challenge_10s_attempts", "challenge_10s_grading_jobs");
    private final MongoTemplate mongo;
    private final Clock clock;
    public LearningActivityRecorder(MongoTemplate mongo, Clock clock) { this.mongo = mongo; this.clock = clock; }

    public Document source(String collection, Object id) {
        return mongo.findById(id, Document.class, collection);
    }

    /** Returns metadata to persist on the source; never stores an identifier in the final aggregate. */
    public Document transition(String collection, Document before, Document after) {
        if (!SOURCES.contains(collection)) throw new IllegalArgumentException("Unknown analytics source");
        if (!MongoDatabaseUtils.isTransactionActive(mongo.getMongoDatabaseFactory()))
            throw new IllegalStateException("Learning activity requires the domain transaction");
        Document markers = before == null ? new Document() : new Document(before.get(MARKERS, Document.class) == null
                ? Map.of("legacyUncovered", true) : before.get(MARKERS, Document.class));
        if (before == null && ((collection.equals("question_grading_jobs") && !Boolean.TRUE.equals(after.get("activityUserSubmission")))
                || (collection.equals("summary_grading_jobs") && "COMPLETED".equals(after.getString("status")))))
            markers.put("legacyUncovered", true);
        // No historical reconstruction from overwritten timestamps. Legacy first events remain uncovered,
        // including after reopen. New durable retries are still counted from the observed transition.
        boolean firstEventsCovered = !Boolean.TRUE.equals(markers.get("legacyUncovered"));
        String status = after.getString("status");
        switch (collection) {
            case "exam_sessions" -> {
                first(markers, firstEventsCovered && before == null, exam_started_total,
                        "REPLACEMENT".equals(after.getString("billingReservationKind")) ? "REPLACEMENT" : "INITIAL");
                first(markers, firstEventsCovered && "COMPLETED".equals(status), exam_completed_total, "ALL");
                first(markers, firstEventsCovered && "RETAKE_AVAILABLE".equals(status), exam_retake_available_total, "ALL");
            }
            case "question_grading_jobs" -> {
                first(markers, firstEventsCovered && before == null, question_submitted_total,
                        number(after, "retryCount") == 0 ? "INITIAL" : "USER_RETRY");
                first(markers, firstEventsCovered && "COMPLETED".equals(status), question_grading_completed_total, "ALL");
                first(markers, firstEventsCovered && "FAILED".equals(status)
                        && "MAX_DISPATCH_ATTEMPTS".equals(after.getString("failureReason")), question_grading_failed_total, "ALL");
                // Reopen is itself the durable retry decision; its following first dispatch is not a second retry.
                // The monotonically increasing marker is independent of resettable dispatch/recovery counters.
                boolean retry = before != null && (("PENDING".equals(status) && "COMPLETED".equals(before.getString("status")))
                        || ("PROCESSING".equals(status) && number(before, "dispatchAttempt") > 0
                            && number(after, "dispatchAttempt") > number(before, "dispatchAttempt")));
                retry(markers, retry, question_grading_retry_total);
            }
            case "summary_grading_jobs" -> {
                first(markers, firstEventsCovered && "COMPLETED".equals(status), summary_grading_completed_total, "ALL");
                first(markers, firstEventsCovered && "FAILED".equals(status)
                        && Set.of("MAX_DISPATCH_ATTEMPTS", "FEEDBACK_GENERATION_FAILED").contains(after.getString("failureReason") == null
                            ? "" : after.getString("failureReason")), summary_grading_failed_total, "ALL");
            }
            case "challenge_10s_attempts" -> {
                first(markers, firstEventsCovered && before == null, challenge_started_total, "ALL");
                first(markers, firstEventsCovered && "SUBMITTED".equals(after.getString("state")), challenge_submitted_total, "ALL");
                first(markers, firstEventsCovered && "EXPIRED".equals(after.getString("state")), challenge_expired_total, "ALL");
                Document result = after.get("result", Document.class);
                first(markers, firstEventsCovered && "completed".equals(after.getString("gradingStatus")), challenge_completed_total,
                        result == null || result.get("transcript") == null ? "NO_SPEECH" : "SCORED");
                first(markers, firstEventsCovered && "failed".equals(after.getString("gradingStatus")), challenge_grading_failed_total, "ALL");
                retry(markers, before != null && number(before, "generation") > 0
                        && number(after, "generation") > number(before, "generation"), challenge_grading_retry_total);
            }
            case "challenge_10s_grading_jobs" -> retry(markers, before != null
                    && !"RETRY_WAIT".equals(before.getString("state")) && "RETRY_WAIT".equals(after.getString("state")), challenge_grading_retry_total);
            default -> throw new IllegalArgumentException("Unknown analytics source");
        }
        after.put(MARKERS, markers);
        return markers;
    }

    public void recordRawTransition(String collection, Document before, Document after) {
        if (after == null) throw new IllegalStateException("Updated analytics source disappeared");
        Document markers = transition(collection, before, after);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(after.get("_id"))), new Update().set(MARKERS, markers), collection);
    }

    private void first(Document markers, boolean occurred, LearningActivityMetric metric, String outcome) {
        if (!occurred || markers.containsKey(metric.name())) return;
        String bucket = increment(metric, outcome);
        markers.put(metric.name(), bucket);
    }
    private void retry(Document markers, boolean occurred, LearningActivityMetric metric) {
        if (!occurred) return;
        long sequence = ((Number) markers.getOrDefault("retrySequence", 0L)).longValue() + 1;
        increment(metric, "ALL");
        markers.put("retrySequence", sequence);
        markers.put("lastCountedRetrySequence", sequence);
    }
    private String increment(LearningActivityMetric metric, String outcome) {
        if (!metric.outcomes.contains(outcome)) throw new IllegalArgumentException("Invalid aggregate dimension");
        Instant now = clock.instant();
        String date = now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate().toString();
        String id = date + ":" + metric.name() + ":" + metric.examType + ":" + outcome;
        mongo.upsert(Query.query(Criteria.where("_id").is(id)), new Update()
                .setOnInsert("bucketDate", date).setOnInsert("metric", metric.name())
                .setOnInsert("examType", metric.examType).setOnInsert("outcome", outcome)
                .setOnInsert("createdAt", now).set("updatedAt", now).inc("count", 1L).inc("version", 1L), COLLECTION);
        return date;
    }
    private static long number(Document doc, String field) {
        Object value = doc.get(field); return value instanceof Number n ? n.longValue() : 0;
    }
}
