package web.tosunsaeng.domain.notification;

import org.springframework.data.mongodb.core.MongoTemplate;
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
        new web.tosunsaeng.domain.exams.application.ExamSubmissionEvidence(mongo)
                .accepted(examId, question, retry, required, now);
    }
}
