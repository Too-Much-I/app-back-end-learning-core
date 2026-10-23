package web.tosunsaeng.domain.usermerge.application;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import web.tosunsaeng.domain.usermerge.domain.OwnershipGuardState;
import web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;

import java.time.Instant;
import java.util.Optional;

public class UserOwnershipGuardService {

    private final ThreadLocal<Boolean> gradingReadChecks = new ThreadLocal<>();

    /** Grading deliberately accepts lifecycle changes between its checks and commit. */
    public <T> T gradingReadChecks(java.util.function.Supplier<T> command) {
        Boolean previous = gradingReadChecks.get();
        gradingReadChecks.set(true);
        try { return command.get(); }
        finally {
            if (previous == null) gradingReadChecks.remove();
            else gradingReadChecks.set(previous);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CallbackWriteFence callbackWriteFence;

    public <T> T callbackScope(String owner, String examId, CallbackWriteFence.Lane lane,
                               java.util.function.Supplier<T> command) {
        return callbackWriteFence == null ? command.get() : callbackWriteFence.within(owner, examId, lane, command);
    }

    public void fenceCallbacksForOwner(String owner, Instant now) {
        if (callbackWriteFence != null) callbackWriteFence.touchOwner(owner, now);
    }

    public void fenceCallbacksForExam(String examId, Instant now) {
        if (callbackWriteFence != null) callbackWriteFence.touchExam(examId, now);
    }

    private final MongoTemplate mongoTemplate;
    private final UserOwnershipGuardRepository repository;

    public UserOwnershipGuardService(
            MongoTemplate mongoTemplate,
            UserOwnershipGuardRepository repository
    ) {
        this.mongoTemplate = mongoTemplate;
        this.repository = repository;
    }

    public Optional<UserOwnershipGuard> find(String userId) {
        return repository.findById(userId);
    }

    public void requireNotMerged(String userId) {
        repository.findById(userId)
                .filter(UserOwnershipGuard::isMerged)
                .ifPresent(guard -> {
                    throw new UserOwnershipGuardException(
                            UserOwnershipGuardException.Reason.ALREADY_MERGED
                    );
                });
    }

    public void touchActive(String userId, Instant now) {
        if (Boolean.TRUE.equals(gradingReadChecks.get())) {
            requireActive(userId, now);
            return;
        }
        if (callbackWriteFence != null && callbackWriteFence.scoped()) {
            UserOwnershipGuard existing = repository.findById(userId).orElse(null);
            if (existing != null && existing.getState() != OwnershipGuardState.ACTIVE) {
                throw new UserOwnershipGuardException(existing.isMerged()
                        ? UserOwnershipGuardException.Reason.ALREADY_MERGED
                        : UserOwnershipGuardException.Reason.STATE_CONFLICT);
            }
            if (mongoTemplate.exists(Query.query(Criteria.where("_id").is(userId)
                    .and("blockedUntil").gt(now)), "withdrawn_user_access_denies")) {
                throw new UserOwnershipGuardException(UserOwnershipGuardException.Reason.STATE_CONFLICT);
            }
            callbackWriteFence.touchScoped(userId, now);
            return;
        }
        Query active = Query.query(Criteria.where("_id").is(userId)
                .and("state").is(OwnershipGuardState.ACTIVE));
        Update touch = new Update()
                .inc("revision", 1L)
                .set("updatedAt", now);
        if (mongoTemplate.updateFirst(active, touch, UserOwnershipGuard.class).getModifiedCount() == 1) {
            return;
        }

        UserOwnershipGuard existing = repository.findById(userId).orElse(null);
        if (existing != null) {
            throw new UserOwnershipGuardException(existing.isMerged()
                    ? UserOwnershipGuardException.Reason.ALREADY_MERGED
                    : UserOwnershipGuardException.Reason.STATE_CONFLICT);
        }

        repository.insert(UserOwnershipGuard.active(userId, now));
    }

    private void requireActive(String userId, Instant now) {
        UserOwnershipGuard existing = repository.findById(userId).orElse(null);
        if (existing != null && existing.getState() != OwnershipGuardState.ACTIVE) {
            throw new UserOwnershipGuardException(existing.isMerged()
                    ? UserOwnershipGuardException.Reason.ALREADY_MERGED
                    : UserOwnershipGuardException.Reason.STATE_CONFLICT);
        }
        if (mongoTemplate.exists(Query.query(Criteria.where("_id").is(userId)
                .and("blockedUntil").gt(now)), "withdrawn_user_access_denies")) {
            throw new UserOwnershipGuardException(UserOwnershipGuardException.Reason.STATE_CONFLICT);
        }
    }

    public void markMerged(
            String sourceUserId,
            String targetUserId,
            String eventId,
            Instant occurredAt,
            Instant now
    ) {
        Query active = Query.query(Criteria.where("_id").is(sourceUserId)
                .and("state").is(OwnershipGuardState.ACTIVE));
        Update merge = new Update()
                .set("state", OwnershipGuardState.MERGED)
                .set("targetUserId", targetUserId)
                .set("mergedAt", occurredAt)
                .set("eventId", eventId)
                .set("updatedAt", now)
                .inc("revision", 1L);
        if (mongoTemplate.updateFirst(active, merge, UserOwnershipGuard.class).getModifiedCount() != 1) {
            throw new UserOwnershipGuardException(
                    UserOwnershipGuardException.Reason.STATE_CONFLICT
            );
        }
    }
}
