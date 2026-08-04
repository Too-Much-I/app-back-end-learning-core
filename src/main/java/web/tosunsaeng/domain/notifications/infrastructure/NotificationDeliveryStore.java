package web.tosunsaeng.domain.notifications.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import web.tosunsaeng.domain.notifications.domain.entity.NotificationDelivery;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationDeliveryStatus;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class NotificationDeliveryStore {

    private final MongoTemplate mongoTemplate;

    public List<NotificationDelivery> claimTicketBatch(
            Instant now,
            Duration leaseDuration,
            int limit) {
        Criteria ready = new Criteria().andOperator(
                Criteria.where("expoTicketId").is(null),
                new Criteria().orOperator(
                        new Criteria().andOperator(
                                Criteria.where("status").is(NotificationDeliveryStatus.PENDING),
                                Criteria.where("nextAttemptAt").lte(now)
                        ),
                        new Criteria().andOperator(
                                Criteria.where("status").is(NotificationDeliveryStatus.PROCESSING),
                                Criteria.where("leaseUntil").lte(now)
                        )
                )
        );
        return claimBatch(ready, now, leaseDuration, limit, "nextAttemptAt");
    }

    public List<NotificationDelivery> claimReceiptBatch(
            Instant now,
            Instant receiptCutoff,
            Duration leaseDuration,
            int limit) {
        Criteria ready = new Criteria().andOperator(
                Criteria.where("expoTicketId").ne(null),
                Criteria.where("ticketReceivedAt").lte(receiptCutoff),
                new Criteria().orOperator(
                        new Criteria().andOperator(
                                Criteria.where("status").is(NotificationDeliveryStatus.TICKET_RECEIVED),
                                Criteria.where("nextAttemptAt").lte(now)
                        ),
                        new Criteria().andOperator(
                                Criteria.where("status").is(NotificationDeliveryStatus.PROCESSING),
                                Criteria.where("leaseUntil").lte(now)
                        )
                )
        );
        return claimBatch(ready, now, leaseDuration, limit, "ticketReceivedAt");
    }

    public boolean markTicketReceived(
            NotificationDelivery claim,
            String ticketId,
            String deviceTokenHashAtSend,
            Instant receiptDueAt,
            Instant now) {
        return updateClaim(claim, new Update()
                .set("status", NotificationDeliveryStatus.TICKET_RECEIVED)
                .set("expoTicketId", ticketId)
                .set("deviceTokenHashAtSend", deviceTokenHashAtSend)
                .set("ticketReceivedAt", now)
                .set("nextAttemptAt", receiptDueAt)
                .set("updatedAt", now)
                .unset("leaseUntil")
                .unset("lastErrorCode"));
    }

    public boolean markSent(NotificationDelivery claim, Instant now) {
        return updateClaim(claim, new Update()
                .set("status", NotificationDeliveryStatus.SENT)
                .set("sentAt", now)
                .set("receiptCheckedAt", now)
                .set("updatedAt", now)
                .unset("leaseUntil")
                .unset("nextAttemptAt")
                .unset("lastErrorCode"));
    }

    public boolean rescheduleTicket(
            NotificationDelivery claim,
            NotificationErrorCode errorCode,
            Instant nextAttemptAt,
            Instant now) {
        return updateClaim(claim, new Update()
                .set("status", NotificationDeliveryStatus.PENDING)
                .set("lastErrorCode", errorCode.name())
                .set("nextAttemptAt", nextAttemptAt)
                .set("updatedAt", now)
                .unset("leaseUntil"));
    }

    public boolean rescheduleReceipt(
            NotificationDelivery claim,
            NotificationErrorCode errorCode,
            Instant nextAttemptAt,
            Instant now) {
        return updateClaim(claim, new Update()
                .set("status", NotificationDeliveryStatus.TICKET_RECEIVED)
                .set("lastErrorCode", errorCode.name())
                .set("nextAttemptAt", nextAttemptAt)
                .set("receiptCheckedAt", now)
                .set("updatedAt", now)
                .unset("leaseUntil"));
    }

    public boolean fail(
            NotificationDelivery claim,
            NotificationErrorCode errorCode,
            Instant now) {
        Update update = new Update()
                .set("status", NotificationDeliveryStatus.FAILED)
                .set("lastErrorCode", errorCode.name())
                .set("updatedAt", now)
                .unset("leaseUntil")
                .unset("nextAttemptAt");
        if (claim.getExpoTicketId() != null) {
            update.set("receiptCheckedAt", now);
        }
        return updateClaim(claim, update);
    }

    public boolean deviceNotRegistered(
            NotificationDelivery claim,
            NotificationErrorCode errorCode,
            String deviceTokenHashAtSend,
            Instant now) {
        Update update = new Update()
                .set("status", NotificationDeliveryStatus.DEVICE_NOT_REGISTERED)
                .set("lastErrorCode", errorCode.name())
                .set("updatedAt", now)
                .unset("leaseUntil")
                .unset("nextAttemptAt");
        if (deviceTokenHashAtSend != null) {
            update.set("deviceTokenHashAtSend", deviceTokenHashAtSend);
        }
        if (claim.getExpoTicketId() != null) {
            update.set("receiptCheckedAt", now);
        }
        return updateClaim(claim, update);
    }

    private List<NotificationDelivery> claimBatch(
            Criteria ready,
            Instant now,
            Duration leaseDuration,
            int limit,
            String firstSortField) {
        List<NotificationDelivery> claims = new ArrayList<>(limit);
        for (int index = 0; index < limit; index++) {
            Query query = Query.query(ready).with(Sort.by(
                    Sort.Order.asc(firstSortField),
                    Sort.Order.asc("createdAt"),
                    Sort.Order.asc("_id")
            ));
            Update update = new Update()
                    .set("status", NotificationDeliveryStatus.PROCESSING)
                    .set("leaseUntil", now.plus(leaseDuration))
                    .set("updatedAt", now)
                    .inc("attemptCount", 1);
            NotificationDelivery claimed = mongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().returnNew(true),
                    NotificationDelivery.class
            );
            if (claimed == null) {
                break;
            }
            claims.add(claimed);
        }
        return claims;
    }

    private boolean updateClaim(NotificationDelivery claim, Update update) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(claim.getDeliveryId()),
                Criteria.where("status").is(NotificationDeliveryStatus.PROCESSING),
                Criteria.where("attemptCount").is(claim.getAttemptCount())
        ));
        return mongoTemplate.updateFirst(query, update, NotificationDelivery.class)
                .getModifiedCount() == 1;
    }
}
