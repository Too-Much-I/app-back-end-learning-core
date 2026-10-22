package web.tosunsaeng.domain.usermerge.application;

import org.bson.Document;
import org.springframework.data.mongodb.MongoDatabaseUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.function.Supplier;

/** Separate write-conflict lanes; lifecycle writers must touch every lane before changing access. */
public final class CallbackWriteFence {
    public static final String COLLECTION = "exam_callback_write_fences";
    public enum Lane { CORE, AZURE }
    private record Scope(String owner, String examId, Lane lane) { }
    private final MongoTemplate mongo;
    private final boolean isolated;
    private final ThreadLocal<Scope> scope = new ThreadLocal<>();

    public CallbackWriteFence(MongoTemplate mongo, boolean isolated) {
        this.mongo = mongo;
        this.isolated = isolated;
    }

    public <T> T within(String owner, String examId, Lane lane, Supplier<T> command) {
        if (!isolated) return command.get();
        Scope next = new Scope(owner, examId, lane);
        Scope previous = scope.get();
        if (previous != null && !previous.equals(next)) throw new IllegalStateException("Cross-callback scope");
        scope.set(next);
        try { return command.get(); }
        finally { if (previous == null) scope.remove(); else scope.set(previous); }
    }

    public boolean touchScoped(String owner, Instant now) {
        Scope current = scope.get();
        if (current == null) return false;
        if (!current.owner().equals(owner)) throw new IllegalStateException("Callback owner changed");
        touch(current.examId(), current.lane(), now);
        return true;
    }

    public boolean scoped() { return scope.get() != null; }

    public void touchExam(String examId, Instant now) {
        for (Lane lane : Lane.values()) touch(examId, lane, now);
    }

    public void touchOwner(String owner, Instant now) {
        requireTransaction();
        // Serialize the inventory with Session creation, including withdrawal when notifications are OFF.
        // Never create/reopen a missing or MERGED owner here.
        mongo.updateFirst(Query.query(Criteria.where("_id").is(owner)),
                new Update().inc("revision", 1L), "user_ownership_guards");
        Query query = Query.query(Criteria.where("userId").is(owner));
        query.fields().include("_id");
        query.with(org.springframework.data.domain.Sort.by("_id"));
        var exams = mongo.find(query, Document.class, "exam_sessions");
        if (exams.isEmpty()) return;
        var bulk = mongo.bulkOps(org.springframework.data.mongodb.core.BulkOperations.BulkMode.ORDERED, COLLECTION);
        for (Document exam : exams) {
            String examId = exam.getString("_id");
            if (examId == null || examId.isBlank()) throw new IllegalStateException("Missing lifecycle exam");
            for (Lane lane : Lane.values()) {
                bulk.upsert(Query.query(Criteria.where("_id").is(lane.name() + ":" + examId)),
                        new Update().inc("revision", 1L).set("updatedAt", now));
            }
        }
        bulk.execute();
    }

    private void touch(String examId, Lane lane, Instant now) {
        requireTransaction();
        if (examId == null || examId.isBlank()) throw new IllegalArgumentException("Missing callback exam");
        // No owner or result data: deterministic per-exam lanes also fence first insertion races.
        mongo.upsert(Query.query(Criteria.where("_id").is(lane.name() + ":" + examId)),
                new Update().inc("revision", 1L).set("updatedAt", now), COLLECTION);
    }

    private void requireTransaction() {
        if (!MongoDatabaseUtils.isTransactionActive(mongo.getMongoDatabaseFactory()))
            throw new IllegalStateException("Callback fence requires Mongo transaction");
    }
}
