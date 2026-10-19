package web.tosunsaeng.domain.learningrecorddeletion.application;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import web.tosunsaeng.domain.learningrecorddeletion.domain.*;
import web.tosunsaeng.domain.learningrecorddeletion.infrastructure.*;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardService;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** HTTP outside transactions; per-target version and lease bind responses to original evidence. */
public final class BillingDeletionSender {
    private final MongoTemplate mongo;
    private final DeletionTransactions tx;
    private final UserOwnershipGuardService guards;
    private final BillingDeletionClient client;
    private final Clock clock;
    private final String environment;
    public BillingDeletionSender(MongoTemplate mongo, DeletionTransactions tx, UserOwnershipGuardService guards,
            BillingDeletionClient client, Clock clock, String environment) {
        this.mongo = mongo; this.tx = tx; this.guards = guards; this.client = client; this.clock = clock; this.environment = environment;
    }
    public boolean tick() {
        Instant now = clock.instant();
        var candidate = mongo.findOne(Query.query(Criteria.where("prepared").is(true)
                .and("billingDelivery.state").is("PENDING").and("billingDelivery.nextAttemptAt").lte(now)
                .orOperator(Criteria.where("billingDelivery.leaseUntil").is(null), Criteria.where("billingDelivery.leaseUntil").lte(now)))
                .with(Sort.by("billingDelivery.nextAttemptAt", "_id")), DeletionTarget.class);
        if (candidate == null) return false;
        return deliver(candidate, null);
    }

    /** One-shot operator path: never selects a different target or resets retry/lease evidence. */
    public boolean sendExact(ExactTarget expected) {
        Objects.requireNonNull(expected);
        var candidate = mongo.findById(expected.targetId(), DeletionTarget.class);
        if (!expected.matches(candidate)) throw new IllegalStateException("Deletion preflight target mismatch");
        var delivery = candidate.getBillingDelivery();
        if (!Objects.equals(environment, delivery.getEnvironment()))
            throw new IllegalStateException("Deletion preflight environment mismatch");
        if (delivery.getState() != BillingDeletionDelivery.State.PENDING
                || delivery.getNextAttemptAt() == null || delivery.getNextAttemptAt().isAfter(clock.instant())
                || (delivery.getLeaseUntil() != null && delivery.getLeaseUntil().isAfter(clock.instant()))) return false;
        return deliver(candidate, expected);
    }

    public record ExactTarget(String targetId, String userId, String eventId, String payloadDigest) {
        public ExactTarget {
            DeletionIdentifiers.userId(userId);
            DeletionIdentifiers.uuidV4(eventId);
            if (targetId == null || !targetId.matches("[a-f0-9-]{36}:EXAM:[A-Za-z0-9_-]{1,128}")
                    || payloadDigest == null || !payloadDigest.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Invalid deletion preflight selection");
            DeletionIdentifiers.uuidV4(targetId.substring(0, 36));
        }
        public boolean matches(DeletionTarget target) {
            if (target == null || !targetId.equals(target.getId()) || !userId.equals(target.getUserId())
                    || target.getTargetType() != DeletionTarget.Type.EXAM || !target.isPrepared()
                    || target.getCallbackFence() != DeletionTarget.CallbackFence.SEALED
                    || !target.isReferencesInventoried() || target.getExpiresAt() != null
                    || !targetId.equals(target.getDeletionId() + ":EXAM:" + target.getAggregateId())) return false;
            var d = target.getBillingDelivery();
            if (d == null || !eventId.equals(d.getEventId()) || !payloadDigest.equals(d.getPayloadDigest())
                    || !payloadDigest.equals(DeletionIdentifiers.sha256(d.getPayload()))) return false;
            var body = org.bson.Document.parse(d.getPayload());
            return eventId.equals(body.getString("eventId")) && userId.equals(body.getString("userId"))
                    && target.getDeletionId().equals(body.getString("deletionOperationId"))
                    && target.getAggregateId().equals(body.getString("sessionId"));
        }
        @Override public String toString() { return "ExactTarget[redacted]"; }
    }

    private boolean deliver(DeletionTarget candidate, ExactTarget expected) {
        String token = UUID.randomUUID().toString();
        DeletionTarget claimed = tx.run(() -> {
            var current = mongo.findById(candidate.getId(), DeletionTarget.class);
            if (current == null || !Objects.equals(current.getVersion(), candidate.getVersion())) return null;
            if (expected != null && !expected.matches(current)) throw new IllegalStateException("Deletion preflight target changed");
            if (expected != null) requireExistingOwner(current.getUserId());
            guards.touchActive(current.getUserId(), clock.instant());
            var delivery = current.getBillingDelivery();
            if (!Objects.equals(environment, delivery.getEnvironment())
                    || !Objects.equals(delivery.getPayloadDigest(), DeletionIdentifiers.sha256(delivery.getPayload()))) {
                delivery.review(BillingDeletionDelivery.Failure.CONTRACT_ERROR); mongo.save(current); return null;
            }
            if (delivery.getLeaseUntil() != null && !delivery.getLeaseUntil().isAfter(clock.instant())) {
                delivery.retry(clock.instant(), null, ThreadLocalRandom.current().nextLong(1000));
                mongo.save(current); return null;
            }
            boolean acquired = delivery.claim(clock.instant(), token);
            mongo.save(current);
            return acquired ? current : null;
        });
        if (claimed == null) return true;
        var fresh = mongo.findById(claimed.getId(), DeletionTarget.class);
        if (fresh == null || !Objects.equals(fresh.getVersion(), claimed.getVersion())
                || !fresh.getBillingDelivery().maySend(token, clock.instant())) return true;
        if (expected != null && !expected.matches(fresh)) throw new IllegalStateException("Deletion preflight target changed");
        var result = client.send(fresh.getBillingDelivery().getPayload());
        tx.run(() -> {
            var current = mongo.findById(claimed.getId(), DeletionTarget.class);
            if (current == null || !Objects.equals(current.getVersion(), claimed.getVersion())) return null;
            if (expected != null && !expected.matches(current)) throw new IllegalStateException("Deletion preflight target changed");
            var delivery = current.getBillingDelivery();
            if (!delivery.owned(token, clock.instant()) || !Objects.equals(delivery.getPayloadDigest(), claimed.getBillingDelivery().getPayloadDigest())) return null;
            if (expected != null) requireExistingOwner(current.getUserId());
            guards.touchActive(current.getUserId(), clock.instant());
            if (result.acknowledged()) delivery.acknowledge(clock.instant());
            else if (result.failure() != null) delivery.review(result.failure());
            else delivery.retry(clock.instant(), result.retryAfter(), ThreadLocalRandom.current().nextLong(1000));
            mongo.save(current); return null;
        });
        return true;
    }
    private void requireExistingOwner(String userId) {
        var owner = guards.find(userId).orElseThrow(() -> new IllegalStateException("Missing preflight owner evidence"));
        if (owner.getState() != web.tosunsaeng.domain.usermerge.domain.OwnershipGuardState.ACTIVE)
            throw new IllegalStateException("Preflight owner is not active");
    }
}
