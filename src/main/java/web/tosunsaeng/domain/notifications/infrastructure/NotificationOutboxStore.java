package web.tosunsaeng.domain.notifications.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationOutbox;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationErrorCode;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationOutboxStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class NotificationOutboxStore {

    private final MongoTemplate mongoTemplate;

    public NotificationOutbox claimNext(Instant now, Duration leaseDuration) {
        Criteria ready = new Criteria().orOperator(
                new Criteria().andOperator(
                        Criteria.where("status").in(
                                NotificationOutboxStatus.PENDING,
                                NotificationOutboxStatus.FAILED
                        ),
                        Criteria.where("nextAttemptAt").lte(now)
                ),
                new Criteria().andOperator(
                        Criteria.where("status").is(NotificationOutboxStatus.PROCESSING),
                        Criteria.where("leaseUntil").lte(now)
                )
        );
        Query query = Query.query(ready).with(Sort.by(
                Sort.Order.asc("nextAttemptAt"),
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("_id")
        ));
        Update update = new Update()
                .set("status", NotificationOutboxStatus.PROCESSING)
                .set("leaseUntil", now.plus(leaseDuration))
                .set("updatedAt", now)
                .unset("lastErrorCode")
                .inc("attemptCount", 1);
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                NotificationOutbox.class
        );
    }

    public boolean markDeliveriesCreated(NotificationOutbox claim, Instant now) {
        return updateClaim(claim, new Update()
                .set("status", NotificationOutboxStatus.DELIVERIES_CREATED)
                .set("updatedAt", now)
                .unset("leaseUntil")
                .unset("nextAttemptAt")
                .unset("lastErrorCode"));
    }

    public boolean markSkippedNoDevice(NotificationOutbox claim, Instant now) {
        return updateClaim(claim, new Update()
                .set("status", NotificationOutboxStatus.SKIPPED_NO_DEVICE)
                .set("updatedAt", now)
                .set("completedAt", now)
                .unset("leaseUntil")
                .unset("nextAttemptAt")
                .unset("lastErrorCode"));
    }

    public boolean reschedule(
            NotificationOutbox claim,
            NotificationErrorCode errorCode,
            Instant nextAttemptAt,
            Instant now) {
        return updateClaim(claim, new Update()
                .set("status", NotificationOutboxStatus.FAILED)
                .set("lastErrorCode", errorCode.name())
                .set("nextAttemptAt", nextAttemptAt)
                .set("updatedAt", now)
                .unset("leaseUntil"));
    }

    public boolean fail(
            NotificationOutbox claim,
            NotificationErrorCode errorCode,
            Instant now) {
        return updateClaim(claim, new Update()
                .set("status", NotificationOutboxStatus.FAILED)
                .set("lastErrorCode", errorCode.name())
                .set("updatedAt", now)
                .set("completedAt", now)
                .unset("leaseUntil")
                .unset("nextAttemptAt"));
    }

    public List<NotificationOutbox> findAwaitingFinalization(int limit) {
        Query query = Query.query(Criteria.where("status")
                        .is(NotificationOutboxStatus.DELIVERIES_CREATED))
                .with(Sort.by(Sort.Order.asc("updatedAt"), Sort.Order.asc("_id")))
                .limit(limit);
        return mongoTemplate.find(query, NotificationOutbox.class);
    }

    public boolean completeIfDeliveriesCreated(
            String notificationId,
            NotificationOutboxStatus finalStatus,
            NotificationErrorCode errorCode,
            Instant now) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(notificationId),
                Criteria.where("status").is(NotificationOutboxStatus.DELIVERIES_CREATED)
        ));
        Update update = new Update()
                .set("status", finalStatus)
                .set("completedAt", now)
                .set("updatedAt", now)
                .unset("leaseUntil")
                .unset("nextAttemptAt");
        if (errorCode == null) {
            update.unset("lastErrorCode");
        } else {
            update.set("lastErrorCode", errorCode.name());
        }
        return mongoTemplate.updateFirst(query, update, NotificationOutbox.class)
                .getModifiedCount() == 1;
    }

    private boolean updateClaim(NotificationOutbox claim, Update update) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(claim.getNotificationId()),
                Criteria.where("status").is(NotificationOutboxStatus.PROCESSING),
                Criteria.where("attemptCount").is(claim.getAttemptCount())
        ));
        return mongoTemplate.updateFirst(query, update, NotificationOutbox.class)
                .getModifiedCount() == 1;
    }
}
