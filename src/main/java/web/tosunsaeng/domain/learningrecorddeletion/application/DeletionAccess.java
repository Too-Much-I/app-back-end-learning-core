package web.tosunsaeng.domain.learningrecorddeletion.application;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupProjectionStatus;
import web.tosunsaeng.domain.learningrecorddeletion.domain.*;
import java.util.function.Supplier;

/** Reads do not mutate guards. Writer checks must run after touching the shared owner in the same transaction. */
public class DeletionAccess {
    private final MongoTemplate mongo;
    private final ThreadLocal<String> drainingExam = new ThreadLocal<>();
    public DeletionAccess(MongoTemplate mongo) { this.mongo = mongo; }

    public boolean sealed(DeletionTarget.Type type, String id) {
        if (id == null) return false;
        return present(Query.query(Criteria.where("targetType").is(type.name()).and("aggregateId").is(id)
                .and("callbackFence").is("SEALED")), DeletionTarget.class);
    }
    public boolean freshSealedExam(String id) {
        // A separate non-session read, not the caller's possibly pre-deletion transaction snapshot.
        return mongo.getMongoDatabaseFactory().getMongoDatabase().getCollection("learning_record_deletion_targets")
                .withReadConcern(com.mongodb.ReadConcern.MAJORITY).withReadPreference(com.mongodb.ReadPreference.primary())
                .find(new org.bson.Document("targetType", "EXAM").append("aggregateId", id).append("callbackFence", "SEALED"))
                .projection(new org.bson.Document("_id", 1)).first() != null;
    }
    public boolean hidden(DeletionTarget.Type type, String id, String owner) {
        if (present(Query.query(Criteria.where("targetType").is(type.name()).and("aggregateId").is(id)
                .and("userId").is(owner)), DeletionTarget.class)) return true;
        var active = active(owner);
        return active != null && active.getSafeToStartLearningAt() == null;
    }
    public void requirePublicWrite(String owner) {
        var active = active(owner);
        if (active != null && active.isWriteBlocked()) throw DeletionFailure.blocked();
    }
    public void requireWriter(String owner) {
        String examId = drainingExam.get();
        if (examId != null && sealed(DeletionTarget.Type.EXAM, examId)) throw DeletionFailure.blocked();
        var active = active(owner);
        if (active == null || !active.isWriteBlocked()) return;
        if (examId != null) {
            ExamSession source = mongo.findById(examId, ExamSession.class);
            if (source != null && owner.equals(source.getUserId())
                    && source.getAttemptGroupProjectionStatus() == AttemptGroupProjectionStatus.GRADING) return;
        }
        throw DeletionFailure.blocked();
    }
    public void requireNoActiveDeletion(String owner) {
        if (active(owner) != null) throw DeletionFailure.temporary();
    }
    public <T> T examCoordination(String examId, Supplier<T> command) {
        String previous = drainingExam.get();
        if (previous != null && !previous.equals(examId)) throw new IllegalStateException("Cross-exam coordination is forbidden");
        drainingExam.set(examId);
        try { return command.get(); }
        finally { if (previous == null) drainingExam.remove(); else drainingExam.set(previous); }
    }
    private DeletionOperation active(String owner) {
        Query query = Query.query(Criteria.where("userId").is(owner).and("activeGuard").is(true));
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            return mongo.findOne(query, DeletionOperation.class);
        var found = majority(DeletionOperation.class).find(query.getQueryObject()).first();
        return found == null ? null : mongo.getConverter().read(DeletionOperation.class, found);
    }
    private boolean present(Query query, Class<?> type) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) return mongo.exists(query, type);
        return majority(type).find(query.getQueryObject()).projection(new org.bson.Document("_id", 1)).first() != null;
    }
    private com.mongodb.client.MongoCollection<org.bson.Document> majority(Class<?> type) {
        return mongo.getCollection(mongo.getCollectionName(type)).withReadConcern(com.mongodb.ReadConcern.MAJORITY)
                .withReadPreference(com.mongodb.ReadPreference.primary());
    }
}
