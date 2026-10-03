package web.tosunsaeng.domain.challenge;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import java.time.Instant;
import java.util.List;
import static web.tosunsaeng.domain.challenge.ChallengeModels.*;

public class ChallengeStore {
    final MongoTemplate mongo;
    private web.tosunsaeng.domain.learningrecorddeletion.application.DeletionAccess deletionAccess;
    public void setDeletionAccess(web.tosunsaeng.domain.learningrecorddeletion.application.DeletionAccess access) { this.deletionAccess = access; }
    public void requirePublicWrite(String owner) { if (deletionAccess != null) deletionAccess.requirePublicWrite(owner); }
    public void setPersistenceCallbacks(org.springframework.data.mapping.callback.EntityCallback<?>... callbacks) {
        mongo.setEntityCallbacks(org.springframework.data.mapping.callback.EntityCallbacks.create(callbacks));
    }
    public boolean deleted(String id) { return deletionAccess != null && deletionAccess.sealed(
            web.tosunsaeng.domain.learningrecorddeletion.domain.DeletionTarget.Type.CHALLENGE, id); }
    private boolean hidden(Attempt a) { return deletionAccess != null && deletionAccess.hidden(
            web.tosunsaeng.domain.learningrecorddeletion.domain.DeletionTarget.Type.CHALLENGE, a.id, a.userId); }
    public ChallengeStore(MongoTemplate mongo) {
        this.mongo = new MongoTemplate(mongo.getMongoDatabaseFactory(), mongo.getConverter());
        this.mongo.setReadPreference(com.mongodb.ReadPreference.primary());
    }
    public Attempt attempt(String id) { return mongo.findById(id, Attempt.class); }
    public Job job(String id) { return mongo.findById(id, Job.class); }
    public Attempt owned(String id, String owner) {
        Attempt a = attempt(id);
        if (a == null) throw new ChallengeFailure(404, "CHALLENGE_ATTEMPT_NOT_FOUND");
        if (!owner.equals(a.userId)) throw ChallengeFailure.forbidden();
        if (hidden(a)) throw new ChallengeFailure(404, "CHALLENGE_ATTEMPT_NOT_FOUND");
        return a;
    }
    public List<Attempt> range(String owner, String from, String to) {
        return mongo.find(Query.query(Criteria.where("userId").is(owner).and("challengeDate").gte(from).lte(to)), Attempt.class)
                .stream().filter(a -> !hidden(a)).toList();
    }
    public Attempt question(String owner, String date, int q) {
        Attempt a = mongo.findOne(Query.query(Criteria.where("userId").is(owner).and("challengeDate").is(date)
                .and("questionNumber").is(q)), Attempt.class);
        return a != null && hidden(a) ? null : a;
    }
    public SubmitReceipt receipt(String owner, String key) {
        return receiptById(owner + ":" + key, SubmitReceipt.class);
    }
    public CallbackReceipt callback(String id) { return receiptById(id, CallbackReceipt.class); }
    private <T> T receiptById(String id, Class<T> type) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            return mongo.findById(id, type);
        // A lost commit acknowledgement is resolved only by majority-visible durable evidence.
        var document = mongo.getCollection(mongo.getCollectionName(type)).withReadConcern(com.mongodb.ReadConcern.MAJORITY)
                .withReadPreference(com.mongodb.ReadPreference.primary()).find(new org.bson.Document("_id", id)).first();
        return document == null ? null : mongo.getConverter().read(type, document);
    }
    public <T> T insert(T value) { return mongo.insert(value); }
    public <T> T save(T value) { return mongo.save(value); }
    public List<Attempt> expired(Instant now, int limit) {
        return mongo.find(Query.query(Criteria.where("state").is(State.CREATED).and("submissionDeadlineAt").lte(now))
                .with(org.springframework.data.domain.Sort.by("submissionDeadlineAt", "_id")).limit(limit), Attempt.class);
    }
    public List<Job> due(Instant now, int limit) {
        Criteria pending = Criteria.where("state").in(JobState.PENDING, JobState.RETRY_WAIT).and("nextAttemptAt").lte(now);
        Criteria abandoned = Criteria.where("state").is(JobState.DISPATCHING).and("leaseUntil").lte(now);
        Criteria timeout = Criteria.where("state").is(JobState.WAITING_CALLBACK).and("callbackDeadlineAt").lte(now);
        return mongo.find(Query.query(new Criteria().orOperator(pending, abandoned, timeout))
                .with(org.springframework.data.domain.Sort.by("nextAttemptAt", "_id")).limit(limit), Job.class);
    }
}
