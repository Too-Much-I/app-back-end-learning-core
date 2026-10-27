package web.tosunsaeng.domain.answerlearning;

import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.*;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import web.tosunsaeng.domain.learningrecorddeletion.application.DeletionAccess;
import web.tosunsaeng.domain.learningrecorddeletion.domain.DeletionTarget;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static web.tosunsaeng.domain.answerlearning.LearningModels.*;

public class LearningStore {
    static final int DUE_BATCH_SIZE = 10;
    public record DueCursor(Instant nextRunAt, String id) {}
    private final MongoTemplate mongo;
    private final UserOwnedTransactionExecutor guards;
    private final DeletionAccess deletion;
    public LearningStore(MongoTemplate mongo, UserOwnedTransactionExecutor guards) { this(mongo, guards, null); }
    public LearningStore(MongoTemplate mongo, UserOwnedTransactionExecutor guards, DeletionAccess deletion) {
        this.mongo=mongo; this.guards=guards; this.deletion=deletion;
    }
    private <T> T write(String owner, Supplier<T> action) { return guards == null ? action.get() : guards.execute(owner, action); }
    public void indexes() {
        mongo.indexOps(Submission.class).ensureIndex(new Index().on("userId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        mongo.indexOps(Submission.class).ensureIndex(new Index().on("userId", Sort.Direction.ASC).on("_id", Sort.Direction.ASC));
        mongo.indexOps("learning_completions").ensureIndex(new Index().on("userId", Sort.Direction.ASC).on("_id", Sort.Direction.ASC));
        mongo.indexOps(Submission.class).ensureIndex(new Index().on("nextRunAt", Sort.Direction.ASC).on("leaseUntil", Sort.Direction.ASC));
        mongo.indexOps("learning_completions").ensureIndex(new Index().on("userId", Sort.Direction.ASC).on("lessonId", Sort.Direction.ASC)
                .on("level", Sort.Direction.ASC).on("contentVersion", Sort.Direction.ASC).unique());
        mongo.indexOps("learning_submission_limits").ensureIndex(new Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO));
    }
    public Submission find(String id) { return mongo.findById(id, Submission.class); }
    public Submission owned(String owner, String id) {
        Submission s = find(id);
        if (s == null || !owner.equals(s.userId) || (deletion != null && deletion.hidden(DeletionTarget.Type.LEARNING_SUBMISSION, id, owner))) throw LearningFailure.missing();
        return s;
    }
    public Submission create(Submission s, int dailyLimit) {
        try { return write(s.userId, () -> {
            // Recheck after acquiring the owner guard: a concurrent request may have committed.
            if (find(s.id) != null) return owned(s.userId, s.id);
            if (deletion != null && deletion.sealed(DeletionTarget.Type.LEARNING_SUBMISSION, s.id)) throw LearningFailure.missing();
            Submission saved = mongo.insert(s);
            quota(s.userId, dailyLimit, s.createdAt);
            return saved;
        }); }
        catch (DuplicateKeyException e) { return owned(s.userId, s.id); }
    }
    /** Called only after an insert/CAS succeeds, within its existing owner transaction. */
    private void quota(String owner, int limit, Instant now) {
        String id = LearningStorage.sha((owner + ":" + now.atZone(ZoneOffset.UTC).toLocalDate()).getBytes(StandardCharsets.UTF_8));
        try {
            mongo.findAndModify(Query.query(Criteria.where("_id").is(id).and("count").lt(limit)),
                    new Update().inc("count", 1).setOnInsert("expiresAt", Date.from(now.plus(Duration.ofDays(2)))),
                    FindAndModifyOptions.options().upsert(true).returnNew(true), Document.class, "learning_submission_limits");
        } catch (DuplicateKeyException e) { throw new LearningFailure(429, "LEARNING_RATE_LIMITED"); }
    }
    public Submission uploadSize(Submission s, long bytes, Instant now) {
        return write(s.userId, () -> mongo.findAndModify(Query.query(Criteria.where("_id").is(s.id).and("userId").is(s.userId)
                .and("status").is("awaiting_audio")), new Update().set("uploadSize", bytes).set("updatedAt", now),
                FindAndModifyOptions.options().returnNew(true), Submission.class));
    }
    public Submission beginFreeze(Submission s, Instant now) {
        return write(s.userId, () -> mongo.findAndModify(Query.query(Criteria.where("_id").is(s.id).and("userId").is(s.userId)
                .and("status").is("awaiting_audio").and("uploadSize").is(s.uploadSize)),
                new Update().set("externalWriteUntil", now.plusSeconds(60)), FindAndModifyOptions.options().returnNew(true), Submission.class));
    }
    public Submission queue(Submission s, Audio audio, Instant now) {
        return write(s.userId, () -> mongo.findAndModify(Query.query(Criteria.where("_id").is(s.id).and("userId").is(s.userId).and("status").is("awaiting_audio")
                .and("uploadSize").is(s.uploadSize)), new Update().set("audio", audio).set("status", "queued").set("queuedAt", now)
                .set("updatedAt", now).set("nextRunAt", now).set("leaseUntil", Instant.EPOCH),
                FindAndModifyOptions.options().returnNew(true), Submission.class));
    }
    public Submission retry(Submission s, Instant now, int dailyLimit) {
        return write(s.userId, () -> {
            Submission next = mongo.findAndModify(Query.query(Criteria.where("_id").is(s.id).and("userId").is(s.userId).and("attempt").is(s.attempt)
                .and("status").is("failed").and("error.retryable").is(true)), new Update().inc("attempt", 1).set("status", "queued")
                .set("error", null).set("dispatched", false).set("transportFailures", 0).set("nextRunAt", now)
                .set("updatedAt", now).set("leaseUntil", Instant.EPOCH).unset("leaseToken"),
                FindAndModifyOptions.options().returnNew(true), Submission.class);
            // Recovery of an existing failed job is not a new daily submission.
            return next;
        });
    }
    public List<Submission> due(Instant now, DueCursor after) {
        Criteria due = new Criteria().andOperator(Criteria.where("nextRunAt").lte(now),
                Criteria.where("leaseUntil").lte(now), new Criteria().orOperator(Criteria.where("status").in("queued", "transcribing", "grading"),
                        Criteria.where("completionPending").is(true)));
        if (after != null) due = new Criteria().andOperator(due, new Criteria().orOperator(
                Criteria.where("nextRunAt").gt(after.nextRunAt()),
                Criteria.where("nextRunAt").is(after.nextRunAt()).and("_id").gt(after.id())));
        return mongo.find(Query.query(due).with(Sort.by("nextRunAt", "_id")).limit(DUE_BATCH_SIZE), Submission.class);
    }
    public Submission claim(Submission s, Instant now) {
        return write(s.userId, () -> mongo.findAndModify(Query.query(Criteria.where("_id").is(s.id).and("userId").is(s.userId).and("attempt").is(s.attempt)
                .and("nextRunAt").lte(now).and("leaseUntil").lte(now).orOperator(
                        Criteria.where("status").in("queued", "transcribing", "grading"), Criteria.where("completionPending").is(true))),
                new Update().set("leaseToken", UUID.randomUUID().toString()).set("leaseUntil", now.plusSeconds(90)),
                FindAndModifyOptions.options().returnNew(true), Submission.class));
    }
    public void finish(Submission s, Update update, Instant now, long delay) {
        // A retry of completion bookkeeping must not reorder already persisted results.
        if (!"completed".equals(s.status)) update.set("updatedAt", now);
        write(s.userId, () -> mongo.updateFirst(Query.query(Criteria.where("_id").is(s.id).and("userId").is(s.userId).and("attempt").is(s.attempt)
                .and("leaseToken").is(s.leaseToken).and("leaseUntil").gt(now)), update.set("leaseUntil", Instant.EPOCH)
                .set("nextRunAt", now.plusSeconds(delay)).unset("leaseToken"), Submission.class));
    }
    /** A live submission and the completion row commit together under the same owner guard. */
    public void complete(String owner, String lesson, String level, String version, String submissionId, Instant now) {
        write(owner, () -> {
            if (submissionId != null) {
                Submission live = mongo.findAndModify(Query.query(Criteria.where("_id").is(submissionId).and("userId").is(owner)
                        .and("lessonId").is(lesson).and("level").is(level).and("contentVersion").is(version)
                        .and("status").is("completed").and("result").ne(null)),
                        new Update().set("completionPending", false), FindAndModifyOptions.options().returnNew(true), Submission.class);
                if (live == null) throw new LearningFailure(409, "COMPLETION_NOT_READY");
            }
            insertCompletion(owner, lesson, level, version, submissionId, now);
            // Part 1 has no submission. Only an explicit completion request records another practice;
            // grading workers and replayed spoken completions must never change the CTA ordering.
            if (submissionId == null) mongo.updateFirst(completionKey(owner, lesson, level, version),
                    new Update().max("lastPracticedAt", Date.from(now)), "learning_completions");
            return null;
        });
    }
    public void completeClaim(Submission s, Instant now) {
        write(s.userId, () -> {
            var changed = mongo.updateFirst(Query.query(Criteria.where("_id").is(s.id).and("userId").is(s.userId)
                    .and("attempt").is(s.attempt).and("leaseToken").is(s.leaseToken).and("leaseUntil").gt(now)
                    .and("status").is("completed").and("completionPending").is(true).and("result").ne(null)),
                    // Preserve the result persistence time used by the recent-resume projection.
                    new Update().set("completionPending", false).unset("leaseToken").set("leaseUntil", Instant.EPOCH), Submission.class);
            if (changed.getMatchedCount() == 1) insertCompletion(s.userId, s.lessonId, s.level, s.contentVersion, s.id, now);
            return null;
        });
    }
    private void insertCompletion(String owner, String lesson, String level, String version, String submissionId, Instant now) {
        // Unique business key gives idempotency; a fresh ID after deletion keeps old manifests away from new learning.
        mongo.upsert(completionKey(owner, lesson, level, version), new Update().setOnInsert("_id", UUID.randomUUID().toString())
                .setOnInsert("userId", owner).setOnInsert("lessonId", lesson).setOnInsert("level", level)
                .setOnInsert("contentVersion", version).setOnInsert("completedAt", now).setOnInsert("submissionId", submissionId),
                "learning_completions");
    }
    static Query completionKey(String owner, String lesson, String level, String version) {
        return Query.query(Criteria.where("userId").is(owner).and("lessonId").is(lesson).and("level").is(level).and("contentVersion").is(version));
    }
}
