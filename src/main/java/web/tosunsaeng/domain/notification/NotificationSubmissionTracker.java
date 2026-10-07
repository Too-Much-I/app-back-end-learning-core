package web.tosunsaeng.domain.notification;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

public class NotificationSubmissionTracker {
    private final MongoTemplate mongo;
    private final NotificationTransactions tx;
    private final NotificationProperties properties;
    private final NotificationStore store;
    public NotificationSubmissionTracker(MongoTemplate mongo, NotificationTransactions tx, NotificationProperties properties, NotificationStore store) {
        this.mongo = mongo; this.tx = tx; this.properties = properties; this.store = store;
    }
    public boolean enabled() { return properties.isTrackingEnabled(); }
    public <T> T transaction(Supplier<T> command) { return tx.run(command); }
    /** Called only immediately after a new user-submitted Job insert, never recovery or replay. */
    public void accepted(String examId, int question, int retry, List<Integer> required, Instant now) {
        if (!enabled() || retry != 0) return;
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Submission receipt requires transaction");
        if (required.isEmpty() || !required.contains(question)) throw new NotificationFailure(503);
        ExamSession session = mongo.findById(examId, ExamSession.class);
        if (session == null) throw new NotificationFailure(503);
        store.touchOwner(session.getUserId());
        // Completion is durable even after Mongo TTL removes the supporting receipts.
        // Replays must neither recreate receipts nor extend their retention.
        if (session.getSubmissionCompletedAt() != null) return;
        // Serializes parallel last-question submissions even without the optional owner writer.
        mongo.updateFirst(Query.query(Criteria.where("_id").is(examId)), new Update().inc("version", 1L), ExamSession.class);
        mongo.upsert(Query.query(Criteria.where("_id").is(examId + ":" + question)), new Update()
                .setOnInsert("examId", examId).setOnInsert("questionNumber", question)
                .setOnInsert("acceptedAt", Date.from(now)), "exam_submission_receipts");
        var receipts = mongo.find(Query.query(Criteria.where("examId").is(examId).and("questionNumber").in(required)),
                Document.class, "exam_submission_receipts");
        if (receipts.stream().map(d -> d.getInteger("questionNumber")).distinct().count() != required.stream().distinct().count()) return;
        Instant last = receipts.stream().map(d -> d.getDate("acceptedAt").toInstant()).max(Comparator.naturalOrder()).orElseThrow();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(examId).and("submissionCompletedAt").is(null)),
                new Update().set("submissionCompletedAt", last).inc("version", 1L), ExamSession.class);
        // The same transaction commits completion and expiry for every receipt of this exam.
        // Incomplete exams never receive expiresAt, regardless of when they started.
        mongo.updateMulti(Query.query(Criteria.where("examId").is(examId)),
                Update.update("expiresAt", Date.from(last.plus(Duration.ofDays(3)))), "exam_submission_receipts");
    }
}
