package web.tosunsaeng.domain.learningrecorddeletion.application;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupProjectionStatus;
import web.tosunsaeng.domain.learningrecorddeletion.domain.*;
import web.tosunsaeng.domain.learningrecorddeletion.infrastructure.*;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardService;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Bounded passes; external storage work is outside the transaction, progress is token/version fenced. */
public class DeletionWorker {
    private final MongoTemplate mongo;
    private final DeletionTransactions tx;
    private final UserOwnershipGuardService guards;
    private final DeletedExamContinuationStore continuations;
    private final DeletionStorage storage;
    private final Clock clock;
    private static final int BATCH = 100;
    public DeletionWorker(MongoTemplate mongo, DeletionTransactions tx, UserOwnershipGuardService guards,
                          DeletedExamContinuationStore continuations, DeletionStorage storage, Clock clock) {
        this.mongo = mongo; this.tx = tx; this.guards = guards; this.continuations = continuations; this.storage = storage; this.clock = clock;
    }
    public boolean tick() {
        Instant now = clock.instant(); String token = UUID.randomUUID().toString();
        Criteria free = new Criteria().orOperator(Criteria.where("leaseUntil").is(null), Criteria.where("leaseUntil").lte(now));
        Query due = Query.query(new Criteria().andOperator(Criteria.where("activeGuard").is(true)
                .and("status").ne(DeletionOperation.Status.NEEDS_REVIEW).and("nextAttemptAt").lte(now), free))
                .with(Sort.by("nextAttemptAt", "_id"));
        DeletionOperation claimed = mongo.findAndModify(due, new Update().set("leaseToken", token).set("leaseUntil", now.plusSeconds(30))
                .inc("attemptCount", 1).inc("version", 1), FindAndModifyOptions.options().returnNew(true), DeletionOperation.class);
        if (claimed == null) { retainCompletedTargets(); return false; }
        try {
            DeletionTarget external = externalTarget(claimed);
            boolean clean = false;
            if (external != null) clean = switch (claimed.getStage()) {
                case DELETING_S3 -> storage.sweep(external);
                case VERIFYING -> storage.sweep(external) && storage.clearCache(external);
                default -> storage.clearCache(external);
            };
            final boolean result = clean;
            tx.run(() -> {
                DeletionOperation operation = ownedClaim(claimed.getDeletionId(), token);
                if (operation == null) return null;
                guards.touchActive(operation.getUserId(), clock.instant());
                if (operation.getStatus() == DeletionOperation.Status.CLEANUP_DELAYED) operation.resumeCleanup();
                step(operation, external, result);
                operation.releaseLeaseUntil(clock.instant().plusSeconds(1));
                mongo.save(operation);
                return null;
            });
        } catch (RuntimeException failure) {
            // A fresh transaction reloads progress. Unknown commit never replays the old mutable operation.
            tx.run(() -> {
                DeletionOperation operation = ownedClaim(claimed.getDeletionId(), token);
                if (operation == null) return null;
                guards.touchActive(operation.getUserId(), clock.instant());
                operation.recordRetry(failure instanceof UnsafeEvidence ? DeletionOperation.FailureCategory.TARGET_EVIDENCE_UNCERTAIN
                        : operation.getSafeToStartLearningAt() == null ? DeletionOperation.FailureCategory.TRANSACTION_UNCERTAIN
                        : DeletionOperation.FailureCategory.CLEANUP_UNAVAILABLE);
                if (failure instanceof UnsafeEvidence || operation.getRetryCount() >= 200
                        || !clock.instant().isBefore(operation.getTargetCompletionAt()))
                    operation.requireIntervention(failure instanceof UnsafeEvidence || operation.getSafeToStartLearningAt() == null);
                int[] backoff = {5, 15, 60, 300, 900};
                int delay = backoff[Math.min(operation.getRetryCount() - 1, backoff.length - 1)];
                operation.releaseLeaseUntil(clock.instant().plusSeconds(delay + ThreadLocalRandom.current().nextInt(1, 6)));
                mongo.save(operation); return null;
            });
        }
        return true;
    }
    private void step(DeletionOperation op, DeletionTarget external, boolean clean) {
        switch (op.getStage()) {
            case REQUESTED -> op.advance(DeletionOperation.Stage.FENCED);
            case FENCED -> op.advance(DeletionOperation.Stage.INVENTORYING);
            case INVENTORYING -> inventory(op);
            case WAITING_COORDINATION -> { if (!pendingCreation(op)) op.advance(DeletionOperation.Stage.PREPARING_RESTART); else waitForCoordination(op); }
            case PREPARING_RESTART -> prepare(op);
            case SAFE_TO_START_LEARNING -> op.advance(DeletionOperation.Stage.DELETING_S3);
            case DELETING_S3 -> {
                if (external == null) op.advance(DeletionOperation.Stage.DELETING_MONGO);
                else if (clean && !clock.instant().isBefore(op.getPresignedCapabilityExpiresAt())) {
                    external.markStorageSwept(clock.instant()); mongo.save(external);
                }
            }
            case DELETING_MONGO -> cleanMongo(op);
            case CLEARING_CACHE -> {
                if (external == null) op.advance(DeletionOperation.Stage.VERIFYING);
                else if (clean) { external.markCacheCleared(); mongo.save(external); }
            }
            case VERIFYING -> verify(op, external, clean);
            default -> throw new UnsafeEvidence();
        }
        // Waiting on coordination is not exempt from the bounded pre-checkpoint budget.
        if (op.isActiveGuard() && !clock.instant().isBefore(op.getTargetCompletionAt())) op.requireIntervention(false);
    }
    private void inventory(DeletionOperation op) {
        if (pendingCreation(op)) { waitForCoordination(op); return; }
        var type = op.getInventoryType(); String collection = type == DeletionTarget.Type.EXAM ? "exam_sessions" : "challenge_10s_attempts";
        Criteria criteria = Criteria.where("userId").is(op.getUserId());
        if (op.getInventoryCursor() != null) criteria.and("_id").gt(op.getInventoryCursor());
        List<Document> roots = mongo.find(Query.query(criteria).with(Sort.by("_id")).limit(BATCH), Document.class, collection);
        long count = op.getInventoriedCount(); String digest = op.getRunningInventoryDigest(); String cursor = op.getInventoryCursor();
        for (Document root : roots) {
            if (!(root.get("_id") instanceof String id)) throw new UnsafeEvidence();
            var target = DeletionTarget.draining(op.getDeletionId(), op.getUserId(), type, id);
            mongo.insert(target); count++; cursor = id;
            digest = DeletionIdentifiers.sha256(digest + ":" + type + ":" + id);
        }
        if (!roots.isEmpty()) { op.inventoryProgress(type, cursor, count, digest); return; }
        if (type == DeletionTarget.Type.EXAM) { op.inventoryProgress(DeletionTarget.Type.CHALLENGE, null, count, digest); return; }
        for (String child : List.of("exam_results", "exam_summaries")) {
            var orphan = mongo.getCollection(child).aggregate(List.of(new Document("$match", new Document("userId", op.getUserId())),
                    new Document("$lookup", new Document("from", "exam_sessions").append("localField", "examId").append("foreignField", "_id").append("as", "parent")),
                    new Document("$match", new Document("parent.0", new Document("$exists", false))), new Document("$limit", 1))).first();
            if (orphan != null) throw new UnsafeEvidence();
        }
        op.sealInventory(count, digest);
        op.advance(DeletionOperation.Stage.WAITING_COORDINATION);
    }
    private void prepare(DeletionOperation op) {
        if (pendingCreation(op)) { waitForCoordination(op); return; }
        var target = target(op, Criteria.where("prepared").ne(true));
        if (target == null) {
            long total = mongo.count(Query.query(Criteria.where("deletionId").is(op.getDeletionId())), DeletionTarget.class);
            long sealed = mongo.count(Query.query(Criteria.where("deletionId").is(op.getDeletionId())
                    .and("prepared").is(true).and("referencesInventoried").is(true)
                    .and("callbackFence").is(DeletionTarget.CallbackFence.SEALED)), DeletionTarget.class);
            if (total != op.getSealedTargetCount() || sealed != total) throw new UnsafeEvidence();
            op.allowNewLearning(new DeletionOperation.RestartEvidence(op.getInventoryDigest(), total, total, true, true, true, true), clock.instant());
            return;
        }
        ExamSession session = target.getTargetType() == DeletionTarget.Type.EXAM ? mongo.findById(target.getAggregateId(), ExamSession.class) : null;
        String root = rootCollection(target);
        if (!mongo.exists(Query.query(Criteria.where("_id").is(target.getAggregateId()).and("userId").is(op.getUserId())), root))
            throw new UnsafeEvidence();
        if (session != null && session.getAttemptGroupId() == null
                && (session.getBillingReservationId() != null || session.getCreationOperationId() != null || session.getEntitlementState() != null))
            throw new UnsafeEvidence(); // Partial Billing metadata is not an unlinked/free Session.
        if (session != null && session.getAttemptGroupProjectionStatus() == AttemptGroupProjectionStatus.GRADING) { waitForCoordination(op); return; }
        target.sealCallbacks(true, true, clock.instant()); mongo.save(target);
        if (session != null && session.getAttemptGroupId() != null) continuations.retainAndDeleteSession(op.getDeletionId(), session.getExamId());
        else {
            if (mongo.remove(Query.query(Criteria.where("_id").is(target.getAggregateId()).and("userId").is(op.getUserId())), root)
                    .getDeletedCount() != 1) throw new UnsafeEvidence();
        }
        target.markPrepared(); mongo.save(target);
    }
    private void cleanMongo(DeletionOperation op) {
        var target = target(op, Criteria.where("mongoCleared").ne(true));
        if (target == null) { op.advance(DeletionOperation.Stage.CLEARING_CACHE); return; }
        String field = target.getTargetType() == DeletionTarget.Type.EXAM ? "examId" : "attemptId";
        for (String collection : children(target)) {
            Query query = Query.query(Criteria.where(field).is(target.getAggregateId())).limit(BATCH);
            query.fields().include("_id");
            List<Document> batch = mongo.find(query, Document.class, collection);
            if (!batch.isEmpty()) {
                mongo.remove(Query.query(Criteria.where("_id").in(batch.stream().map(d -> d.get("_id")).toList())
                        .and(field).is(target.getAggregateId())), collection);
                return;
            }
        }
        target.markMongoCleared(); mongo.save(target);
    }
    private void verify(DeletionOperation op, DeletionTarget external, boolean storageEmpty) {
        if (external != null) {
            if (!storageEmpty) return;
            if (mongo.exists(Query.query(Criteria.where("_id").is(external.getAggregateId())), rootCollection(external)))
                throw new UnsafeEvidence();
            String field = external.getTargetType() == DeletionTarget.Type.EXAM ? "examId" : "attemptId";
            for (String collection : children(external)) {
                if (mongo.exists(Query.query(Criteria.where(field).is(external.getAggregateId())), collection)) throw new UnsafeEvidence();
            }
            external.markVerified(clock.instant()); mongo.save(external); return;
        }
        long unfinished = mongo.count(Query.query(new Criteria().andOperator(Criteria.where("deletionId").is(op.getDeletionId()),
                new Criteria().orOperator(Criteria.where("prepared").ne(true), Criteria.where("mongoCleared").ne(true),
                        Criteria.where("cacheCleared").ne(true), Criteria.where("storageSweptAt").lt(op.getPresignedCapabilityExpiresAt()),
                        Criteria.where("storageSweptAt").is(null), Criteria.where("verifiedAt").is(null)))), DeletionTarget.class);
        long total = mongo.count(Query.query(Criteria.where("deletionId").is(op.getDeletionId())), DeletionTarget.class);
        if (unfinished != 0 || total != op.getSealedTargetCount()) throw new UnsafeEvidence();
        // New learning may have created a NEW Billing operation after the checkpoint. It is not part of this deletion.
        op.complete(new DeletionOperation.CompletionEvidence(0, 0, 0, true,
                clock.instant()), clock.instant());
        op.holdRetention(); // Do not expire the operation before all target TTL metadata has been persisted.
    }
    private DeletionTarget externalTarget(DeletionOperation op) {
        DeletionTarget found = switch (op.getStage()) {
            case DELETING_S3 -> target(op, Criteria.where("storageSweptAt").is(null));
            case CLEARING_CACHE -> target(op, Criteria.where("cacheCleared").ne(true));
            case VERIFYING -> target(op, Criteria.where("verifiedAt").is(null));
            default -> null;
        };
        if (found != null && (!found.isPrepared() || !found.isReferencesInventoried()
                || found.getCallbackFence() != DeletionTarget.CallbackFence.SEALED
                || !op.getUserId().equals(found.getUserId()))) throw new UnsafeEvidence();
        return found;
    }
    private DeletionTarget target(DeletionOperation op, Criteria progress) {
        return mongo.findOne(Query.query(new Criteria().andOperator(Criteria.where("deletionId").is(op.getDeletionId()), progress))
                .with(Sort.by("_id")), DeletionTarget.class);
    }
    private boolean pendingCreation(DeletionOperation op) {
        return mongo.exists(Query.query(Criteria.where("userId").is(op.getUserId()).and("activeGuard").is(true)), "exam_creation_operations");
    }
    private void waitForCoordination(DeletionOperation op) {
        op.recordRetry(DeletionOperation.FailureCategory.COORDINATION_WAIT);
        if (op.getRetryCount() >= 200) op.requireIntervention(true);
    }
    private DeletionOperation ownedClaim(String id, String token) {
        return mongo.findOne(Query.query(Criteria.where("_id").is(id).and("leaseToken").is(token).and("leaseUntil").gt(clock.instant())
                .and("activeGuard").is(true)), DeletionOperation.class);
    }
    private List<String> children(DeletionTarget target) {
        return target.getTargetType() == DeletionTarget.Type.EXAM
                ? List.of("exam_results", "exam_summaries", "question_grading_jobs", "summary_grading_jobs", "azure_results", "speechace_results", "exam_submission_receipts")
                : List.of("challenge_10s_grading_jobs", "challenge_10s_submit_receipts", "challenge_10s_callback_receipts");
    }
    private String rootCollection(DeletionTarget target) {
        return target.getTargetType() == DeletionTarget.Type.EXAM ? "exam_sessions" : "challenge_10s_attempts";
    }
    private void retainCompletedTargets() {
        var done = mongo.find(Query.query(Criteria.where("status").is(DeletionOperation.Status.COMPLETED)
                .and("retentionFinalized").ne(true)).with(Sort.by("completedAt", "_id")).limit(1), DeletionOperation.class);
        for (var op : done) {
            var targets = mongo.find(Query.query(Criteria.where("deletionId").is(op.getDeletionId()).and("expiresAt").is(null)).limit(BATCH), DeletionTarget.class);
            for (var target : targets) { target.retainUntil(op.getCompletedAt().plus(Duration.ofDays(30))); mongo.save(target); }
            if (targets.isEmpty()) {
                tx.run(() -> {
                    var current = mongo.findById(op.getDeletionId(), DeletionOperation.class);
                    if (current != null && !current.isRetentionFinalized()) {
                        current.finishRetention(); mongo.save(current);
                        mongo.updateMulti(Query.query(Criteria.where("deletionId").is(current.getDeletionId())),
                                new Update().set("expiresAt", current.getExpiresAt()), DeletionCommand.class);
                    }
                    return null;
                });
            }
        }
    }
    public void maintainRetention() { retainCompletedTargets(); }
    private static final class UnsafeEvidence extends RuntimeException {}
}
