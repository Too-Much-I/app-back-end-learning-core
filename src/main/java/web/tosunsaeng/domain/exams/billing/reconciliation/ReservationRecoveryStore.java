package web.tosunsaeng.domain.exams.billing.reconciliation;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.enums.ExamCreationState;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class ReservationRecoveryStore {
    private final MongoTemplate mongo;
    private final ReservationOperationExecution execution;
    private final ReservationReconciliationProperties properties;

    public ReservationRecoveryStore(MongoTemplate mongo, ReservationOperationExecution execution,
                                    ReservationReconciliationProperties properties) {
        this.mongo = new MongoTemplate(mongo.getMongoDatabaseFactory(), mongo.getConverter());
        this.mongo.setWriteConcern(com.mongodb.WriteConcern.MAJORITY);
        this.execution = execution;
        this.properties = properties;
    }

    public List<ExamCreationOperation> due() {
        // Separate ready and expired-lease index paths; no full collection scan or unbounded queue.
        Query expired = Query.query(Criteria.where("recovery.schemaVersion").is(1).and("activeGuard").is(true)
                .and("recovery.status").is(ReservationRecovery.Status.IN_FLIGHT)
                .and("recovery.leaseUntil").lte(execution.now()).and("recovery.nextAt").lte(execution.now()))
                .with(Sort.by("recovery.leaseUntil", "_id")).limit(properties.getBatchSize());
        List<ExamCreationOperation> found = new java.util.ArrayList<>(mongo.find(expired, ExamCreationOperation.class));
        int remaining = properties.getBatchSize() - found.size();
        if (remaining > 0) {
            Query ready = Query.query(Criteria.where("recovery.schemaVersion").is(1).and("activeGuard").is(true)
                    .and("recovery.status").is(ReservationRecovery.Status.READY).and("recovery.nextAt").lte(execution.now()))
                    .with(Sort.by("recovery.nextAt", "_id")).limit(remaining);
            found.addAll(mongo.find(ready, ExamCreationOperation.class));
        }
        return found;
    }

    public long dueCount() {
        Query ready = Query.query(Criteria.where("recovery.schemaVersion").is(1).and("activeGuard").is(true)
                .and("recovery.status").is(ReservationRecovery.Status.READY).and("recovery.nextAt").lte(execution.now()));
        Query expired = Query.query(Criteria.where("recovery.schemaVersion").is(1).and("activeGuard").is(true)
                .and("recovery.status").is(ReservationRecovery.Status.IN_FLIGHT)
                .and("recovery.leaseUntil").lte(execution.now()).and("recovery.nextAt").lte(execution.now()));
        return mongo.count(ready, ExamCreationOperation.class) + mongo.count(expired, ExamCreationOperation.class);
    }

    public long dueAgeSeconds(ExamCreationOperation op) {
        return Math.max(0, java.time.Duration.between(op.getRecovery().getNextAt(), execution.now()).toSeconds());
    }

    public boolean startAttempt(ReservationOperationExecution.Lease lease, ExamCreationOperation op) {
        ReservationRecovery r = op.getRecovery();
        Instant now = execution.now();
        if (r.getAttempts() >= properties.getMaxAttempts()
                || (r.getFirstAt() != null && !now.isBefore(r.getFirstAt().plus(properties.getMaxAge())))) {
            quarantine(lease, ReservationRecovery.Status.NEEDS_REVIEW, "retry_exhausted");
            return false;
        }
        Update update = new Update().inc("recovery.attempts", 1).inc("version", 1);
        if (r.getFirstAt() == null) update.set("recovery.firstAt", now);
        check(mongo.updateFirst(execution.owned(lease), update, ExamCreationOperation.class).getModifiedCount());
        return true;
    }

    public void retry(ReservationOperationExecution.Lease lease, Integer retryAfter) {
        ExamCreationOperation fresh = execution.freshOperation(lease.id());
        if (fresh == null || fresh.isTerminal()) return;
        long delay = retryDelay(fresh.getRecovery().getAttempts(), retryAfter);
        Update update = released().set("recovery.status", ReservationRecovery.Status.READY)
                .set("recovery.nextAt", execution.now().plusSeconds(delay));
        check(mongo.updateFirst(execution.owned(lease), update, ExamCreationOperation.class).getModifiedCount());
    }

    public void quarantine(ReservationOperationExecution.Lease lease, ReservationRecovery.Status status, String reason) {
        Query query = execution.owned(lease).addCriteria(Criteria.where("activeGuard").is(true));
        Update update = released().set("recovery.status", status).set("recovery.failureCode", reason)
                .unset("purgeAt").set("recovery.alertIncidentId", UUID.randomUUID().toString())
                .set("recovery.alertReason", reason).set("recovery.alertStatus", "PENDING")
                .set("recovery.alertNextAt", execution.now()).set("recovery.alertCreatedAt", execution.now())
                .set("recovery.alertAttempts", 0);
        check(mongo.updateFirst(query, update, ExamCreationOperation.class).getModifiedCount());
    }

    public void wakeAuthBlocked() {
        List<String> ids = mongo.find(Query.query(Criteria.where("recovery.status").is(ReservationRecovery.Status.BLOCKED_AUTH)
                        .and("recovery.schemaVersion").is(1).and("activeGuard").is(true))
                        .limit(properties.getBatchSize()), ExamCreationOperation.class).stream()
                .map(ExamCreationOperation::getCommandId).toList();
        if (ids.isEmpty()) return;
        mongo.updateMulti(Query.query(Criteria.where("_id").in(ids)
                        .and("recovery.status").is(ReservationRecovery.Status.BLOCKED_AUTH)),
                new Update().set("recovery.status", ReservationRecovery.Status.READY)
                        .set("recovery.nextAt", execution.now()).inc("version", 1), ExamCreationOperation.class);
    }

    public void blockAuth(ReservationOperationExecution.Lease lease) {
        check(mongo.updateFirst(execution.owned(lease), released()
                .set("recovery.status", ReservationRecovery.Status.BLOCKED_AUTH)
                .set("recovery.failureCode", "auth_failure").unset("purgeAt"), ExamCreationOperation.class).getModifiedCount());
    }

    private static Update released() {
        return new Update().unset("recovery.leaseToken").unset("recovery.leaseOwner")
                .unset("recovery.leaseUntil").inc("version", 1);
    }

    static long retryDelay(int attempts, Integer retryAfter) {
        long[] seconds = {5, 15, 60, 300, 900};
        long base = seconds[Math.min(Math.max(attempts - 1, 0), seconds.length - 1)];
        long jittered = base + ThreadLocalRandom.current().nextLong(1, Math.max(2, base / 10 + 1));
        return Math.max(jittered, retryAfter == null ? 0L : Math.max(0L, retryAfter.longValue()));
    }

    private static void check(long changed) {
        if (changed != 1) throw new ReservationRecoveryException(ReservationRecoveryException.Reason.LEASE_LOST);
    }
}
