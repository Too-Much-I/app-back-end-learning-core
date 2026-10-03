package web.tosunsaeng.domain.learningrecorddeletion.infrastructure;

import org.springframework.data.mongodb.MongoDatabaseUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.learningrecorddeletion.domain.DeletedExamContinuation;
import web.tosunsaeng.domain.learningrecorddeletion.domain.DeletionOperation;
import web.tosunsaeng.domain.learningrecorddeletion.domain.DeletionTarget;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardService;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Joins the caller's Mongo transaction. No remote Billing calls, transaction nesting or automatic retries.
 * Not a component: production wiring is withheld until deletion fences and the rollout gates are complete.
 */
public class DeletedExamContinuationStore {
    private final MongoTemplate mongo;
    private final UserOwnershipGuardService guards;
    private final Clock clock;

    public DeletedExamContinuationStore(MongoTemplate mongo, UserOwnershipGuardService guards, Clock clock) {
        this.mongo = mongo;
        this.guards = guards;
        this.clock = clock;
    }

    public DeletedExamContinuation retainAndDeleteSession(String deletionId, String sourceExamId) {
        requireTransaction();
        DeletionOperation deletion = mongo.findById(deletionId, DeletionOperation.class);
        require(deletion != null && deletion.isActiveGuard() && deletion.isWriteBlocked()
                && deletion.getStatus() == DeletionOperation.Status.PROCESSING
                && deletion.getStage() == DeletionOperation.Stage.PREPARING_RESTART);
        touch(deletion.getUserId());
        DeletionTarget target = mongo.findOne(Query.query(Criteria.where("deletionId").is(deletionId)
                .and("targetType").is(DeletionTarget.Type.EXAM).and("aggregateId").is(sourceExamId)), DeletionTarget.class);
        require(target != null && Objects.equals(target.getUserId(), deletion.getUserId())
                && target.isReferencesInventoried() && target.getCallbackFence() == DeletionTarget.CallbackFence.SEALED);
        require(!mongo.exists(Query.query(Criteria.where("userId").is(deletion.getUserId())
                .and("activeGuard").is(true)), ExamCreationOperation.class));
        ExamSession session = mongo.findById(sourceExamId, ExamSession.class);
        DeletedExamContinuation existing = mongo.findById(sourceExamId, DeletedExamContinuation.class);
        if (existing != null) {
            require(session == null && Objects.equals(existing.getDeletionId(), deletionId)
                    && Objects.equals(existing.getUserId(), deletion.getUserId()));
            return existing;
        }
        require(session != null && Objects.equals(session.getUserId(), deletion.getUserId()));
        DeletedExamContinuation evidence = DeletedExamContinuation.capture(deletionId, session, clock.instant());
        if (session.getTerminalEventId() != null) {
            require(mongo.exists(Query.query(Criteria.where("_id").is(session.getTerminalEventId())
                    .and("sessionId").is(sourceExamId).and("attemptGroupId").is(session.getAttemptGroupId())
                    .and("targetStatus").is(session.getAttemptGroupProjectionStatus().name())), "attempt_group_event_outbox"));
        }
        mongo.insert(evidence);
        require(mongo.remove(Query.query(Criteria.where("_id").is(sourceExamId)
                .and("userId").is(deletion.getUserId()).and("version").is(session.getVersion())), ExamSession.class)
                .getDeletedCount() == 1);
        return evidence;
    }

    /** A CLAIMED source is not treated as absence, which could otherwise fall through to INITIAL. */
    public Optional<DeletedExamContinuation> findAvailable(String userId) {
        List<DeletedExamContinuation> sources = pending(userId);
        require(sources.size() <= 1);
        if (sources.isEmpty()) return Optional.empty();
        DeletedExamContinuation source = sources.getFirst();
        require(source.getTransferState() == DeletedExamContinuation.TransferState.AVAILABLE);
        return Optional.of(source);
    }

    /** Called in the same transaction that inserts the new ExamCreationOperation. */
    public void claimForInsert(ExamCreationOperation operation) {
        requireTransaction();
        touch(operation.getUserId());
        requireLearningAllowed(operation.getUserId());
        List<DeletedExamContinuation> sources = pending(operation.getUserId());
        require(sources.size() <= 1);
        if (sources.isEmpty()) {
            // A consumed source must never be used again after operation TTL expiry.
            require(sourceFor(operation) == null);
            return;
        }
        DeletedExamContinuation source = sources.getFirst();
        source.claim(operation, clock.instant());
        mongo.save(source);
    }

    /** Called after inserting the new Session and marking SESSION_COMMITTED, in that same transaction. */
    public void transferForCommittedSession(ExamCreationOperation operation) {
        requireTransaction();
        touch(operation.getUserId());
        requireLearningAllowed(operation.getUserId());
        DeletedExamContinuation source = sourceFor(operation);
        if (source == null) return;
        ExamSession session = mongo.findById(operation.getSessionId(), ExamSession.class);
        require(session != null && Objects.equals(session.getUserId(), operation.getUserId())
                && Objects.equals(session.getCreationOperationId(), operation.getOperationId())
                && Objects.equals(session.getBillingReservationId(), operation.getReservationId())
                && Objects.equals(session.getAttemptGroupId(), source.getAttemptGroupId())
                && Objects.equals(session.getMockExamId(), source.getMockExamId()));
        source.transfer(operation, clock.instant());
        mongo.save(source);
    }

    /** Only CANCELED/EXPIRED precommit evidence permits release. A timeout is never sufficient. */
    public void releaseCanceledClaim(ExamCreationOperation operation) {
        requireTransaction();
        touch(operation.getUserId());
        DeletedExamContinuation source = sourceFor(operation);
        if (source == null) return;
        if (source.getTransferState() == DeletedExamContinuation.TransferState.TRANSFERRED
                || source.getTransferState() == DeletedExamContinuation.TransferState.AVAILABLE) return;
        require(!mongo.exists(Query.query(Criteria.where("_id").is(operation.getSessionId())), ExamSession.class));
        require(!mongo.exists(Query.query(Criteria.where("userId").is(operation.getUserId())
                .and("creationOperationId").is(operation.getOperationId())), ExamSession.class));
        source.releaseCanceledClaim(operation);
        mongo.save(source);
    }

    private DeletedExamContinuation sourceFor(ExamCreationOperation operation) {
        return operation.getReplacementSourceSessionId() == null ? null
                : mongo.findById(operation.getReplacementSourceSessionId(), DeletedExamContinuation.class);
    }

    private List<DeletedExamContinuation> pending(String userId) {
        return mongo.find(Query.query(Criteria.where("userId").is(userId).and("transferState").in(
                DeletedExamContinuation.TransferState.AVAILABLE, DeletedExamContinuation.TransferState.CLAIMED))
                .limit(2), DeletedExamContinuation.class);
    }

    private void requireLearningAllowed(String userId) {
        require(!mongo.exists(Query.query(Criteria.where("userId").is(userId)
                .and("activeGuard").is(true).and("writeBlocked").is(true)), DeletionOperation.class));
    }

    private void touch(String userId) {
        require(!mongo.exists(Query.query(Criteria.where("_id").is(userId)
                .and("blockedUntil").gt(clock.instant())), "withdrawn_user_access_denies"));
        guards.touchActive(userId, clock.instant());
    }

    private void requireTransaction() {
        require(MongoDatabaseUtils.isTransactionActive(mongo.getMongoDatabaseFactory()));
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("Deleted exam continuation requires verified coordination");
    }
}
