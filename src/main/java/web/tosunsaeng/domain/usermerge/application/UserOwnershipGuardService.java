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
