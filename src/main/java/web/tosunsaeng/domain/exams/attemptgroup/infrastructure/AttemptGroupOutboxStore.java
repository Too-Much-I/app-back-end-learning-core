package web.tosunsaeng.domain.exams.attemptgroup.infrastructure;

import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupEventOutbox;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupOutboxStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupEventSlot;

@Repository
@RequiredArgsConstructor
public class AttemptGroupOutboxStore {
    private final MongoTemplate mongoTemplate;

    public Optional<AttemptGroupEventOutbox> findBySessionAndSlot(
            String sessionId,
            AttemptGroupEventSlot slot
    ) {
        Query query = Query.query(Criteria.where("sessionId").is(sessionId)
                .and("eventSlot").is(slot));
        return Optional.ofNullable(mongoTemplate.findOne(query, AttemptGroupEventOutbox.class));
    }

    public AttemptGroupEventOutbox insert(AttemptGroupEventOutbox event) {
        return mongoTemplate.insert(event);
    }

    public Optional<AttemptGroupEventOutbox> claimNext(
            String owner,
            Instant now,
            Instant leaseUntil
    ) {
        Criteria duePending = new Criteria().andOperator(
                Criteria.where("status").is(AttemptGroupOutboxStatus.PENDING),
                new Criteria().orOperator(
                        Criteria.where("nextAttemptAt").lte(now),
                        Criteria.where("nextAttemptAt").exists(false)
                )
        );
        Criteria expiredLease = new Criteria().andOperator(
                Criteria.where("status").is(AttemptGroupOutboxStatus.IN_FLIGHT),
                Criteria.where("leaseUntil").lte(now)
        );
        Query query = new Query(new Criteria().orOperator(duePending, expiredLease))
                .with(Sort.by(Sort.Direction.ASC, "nextAttemptAt", "eventId"));
        String token = UUID.randomUUID().toString();
        Update update = new Update()
                .set("status", AttemptGroupOutboxStatus.IN_FLIGHT)
                .set("leaseOwner", owner)
                .set("leaseToken", token)
                .set("leaseUntil", leaseUntil)
                .set("updatedAt", now)
                .inc("attemptCount", 1);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                AttemptGroupEventOutbox.class
        ));
    }

    public boolean markDelivered(
            String eventId,
            String leaseToken,
            Instant now,
            Instant expiresAt
    ) {
        return updateLease(eventId, leaseToken, new Update()
                .set("status", AttemptGroupOutboxStatus.DELIVERED)
                .set("deliveredAt", now)
                .set("expiresAt", expiresAt)
                .set("updatedAt", now)
                .unset("leaseOwner").unset("leaseToken").unset("leaseUntil"));
    }

    public boolean scheduleRetry(
            String eventId,
            String leaseToken,
            Instant nextAttemptAt,
            String category,
            Instant now
    ) {
        return updateLease(eventId, leaseToken, new Update()
                .set("status", AttemptGroupOutboxStatus.PENDING)
                .set("nextAttemptAt", nextAttemptAt)
                .set("lastFailureCategory", category)
                .set("updatedAt", now)
                .unset("expiresAt")
                .unset("leaseOwner").unset("leaseToken").unset("leaseUntil"));
    }

    public boolean markDeadLetter(
            String eventId,
            String leaseToken,
            String category,
            Instant now,
            Instant expiresAt
    ) {
        return updateLease(eventId, leaseToken, new Update()
                .set("status", AttemptGroupOutboxStatus.DEAD_LETTER)
                .set("lastFailureCategory", category)
                .set("deadLetterAt", now)
                .set("expiresAt", expiresAt)
                .set("updatedAt", now)
                .unset("leaseOwner").unset("leaseToken").unset("leaseUntil"));
    }

    public boolean markBlockedAuth(
            String eventId,
            String leaseToken,
            String category,
            Instant now
    ) {
        return updateLease(eventId, leaseToken, new Update()
                .set("status", AttemptGroupOutboxStatus.BLOCKED_AUTH)
                .set("lastFailureCategory", category)
                .set("updatedAt", now)
                .unset("expiresAt")
                .unset("leaseOwner").unset("leaseToken").unset("leaseUntil"));
    }

    public long releaseBlocked(Instant now) {
        Query query = Query.query(Criteria.where("status").is(AttemptGroupOutboxStatus.BLOCKED_AUTH));
        Update update = new Update()
                .set("status", AttemptGroupOutboxStatus.PENDING)
                .set("nextAttemptAt", now)
                .set("updatedAt", now)
                .unset("lastFailureCategory");
        return mongoTemplate.updateMulti(query, update, AttemptGroupEventOutbox.class).getModifiedCount();
    }

    public boolean repairTraceContext(
            String eventId,
            String leaseToken,
            AttemptGroupTraceContext.StoredContext context,
            Instant now
    ) {
        return updateLease(eventId, leaseToken, new Update()
                .set("traceId", context.traceId())
                .set("parentSpanId", context.parentSpanId())
                .set("traceFlags", context.traceFlags())
                .set("updatedAt", now));
    }

    private boolean updateLease(String eventId, String leaseToken, Update update) {
        Query query = Query.query(Criteria.where("eventId").is(eventId)
                .and("status").is(AttemptGroupOutboxStatus.IN_FLIGHT)
                .and("leaseToken").is(leaseToken));
        UpdateResult result = mongoTemplate.updateFirst(query, update, AttemptGroupEventOutbox.class);
        return result.getModifiedCount() == 1;
    }
}
