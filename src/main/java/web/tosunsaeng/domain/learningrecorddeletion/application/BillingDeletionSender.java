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
        String token = UUID.randomUUID().toString();
        DeletionTarget claimed = tx.run(() -> {
            var current = mongo.findById(candidate.getId(), DeletionTarget.class);
            if (current == null || !Objects.equals(current.getVersion(), candidate.getVersion())) return null;
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
        var result = client.send(fresh.getBillingDelivery().getPayload());
        tx.run(() -> {
            var current = mongo.findById(claimed.getId(), DeletionTarget.class);
            if (current == null || !Objects.equals(current.getVersion(), claimed.getVersion())) return null;
            var delivery = current.getBillingDelivery();
            if (!delivery.owned(token, clock.instant()) || !Objects.equals(delivery.getPayloadDigest(), claimed.getBillingDelivery().getPayloadDigest())) return null;
            guards.touchActive(current.getUserId(), clock.instant());
            if (result.acknowledged()) delivery.acknowledge(clock.instant());
            else if (result.failure() != null) delivery.review(result.failure());
            else delivery.retry(clock.instant(), result.retryAfter(), ThreadLocalRandom.current().nextLong(1000));
            mongo.save(current); return null;
        });
        return true;
    }
}
