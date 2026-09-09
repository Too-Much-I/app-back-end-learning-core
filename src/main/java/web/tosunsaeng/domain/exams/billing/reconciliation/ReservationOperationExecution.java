package web.tosunsaeng.domain.exams.billing.reconciliation;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.enums.ExamCreationState;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import static web.tosunsaeng.domain.exams.billing.reconciliation.ReservationRecoveryException.Reason.LEASE_LOST;

/** Shared by HTTP commands and recovery. A lease fences local writes, not remote delivery. */
@Component
public class ReservationOperationExecution {
    public static final String COLLECTION = "exam_creation_operations";
    private final MongoTemplate mongo;
    private final ReservationReconciliationProperties properties;
    private final Clock clock;
    private final String instance = UUID.randomUUID().toString();
    private final ThreadLocal<Lease> current = new ThreadLocal<>();

    public ReservationOperationExecution(MongoTemplate mongo, ReservationReconciliationProperties properties,
                                         @Qualifier("gradingClock") Clock clock) {
        this.mongo = new MongoTemplate(mongo.getMongoDatabaseFactory(), mongo.getConverter());
        this.mongo.setWriteConcern(WriteConcern.MAJORITY);
        this.properties = properties;
        this.clock = clock;
    }

    public <T> T http(String id, Supplier<T> work) {
        Lease lease;
        try { lease = claim(id, false); }
        catch (org.springframework.dao.DataAccessException unavailable) {
            throw new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE, 1);
        }
        if (lease == null) throw new ExamsException(ErrorStatus._EXAM_CREATION_PROCESSING, 1);
        try {
            return within(lease, work);
        } catch (ReservationRecoveryException | ReservationCommitOutcomeUnknownException
                 | org.springframework.transaction.TransactionException failure) {
            throw new ExamsException(ErrorStatus._EXAM_CREATION_PROCESSING, 1);
        } catch (org.springframework.dao.DataAccessException unavailable) {
            throw new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE, 1);
        } finally {
            // Failure to release is recoverable by expiration and must not mask an earlier result.
            try { release(lease); } catch (RuntimeException ignored) { /* no raw provider logging */ }
        }
    }

    public Lease claim(String id, boolean worker) {
        Instant now = now();
        if (!worker) {
            ReservationRecovery legacy = ReservationRecovery.prepared(now);
            legacy.setSchemaVersion(0);
            legacy.setDispatch(ReservationRecovery.Dispatch.MAY_HAVE_BEEN_SENT);
            mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("recovery").is(null)),
                    new Update().set("recovery", legacy).inc("version", 1), ExamCreationOperation.class);
        }
        Criteria eligible = Criteria.where("_id").is(id).and("activeGuard").is(true)
                .and("state").in(ExamCreationState.PREPARED, ExamCreationState.RESERVED,
                        ExamCreationState.SESSION_COMMITTED, ExamCreationState.CANCEL_PENDING)
                .and("recovery.status").in(ReservationRecovery.Status.READY, ReservationRecovery.Status.IN_FLIGHT)
                .andOperator(new Criteria().orOperator(Criteria.where("recovery.leaseUntil").is(null),
                        Criteria.where("recovery.leaseUntil").lte(now)));
        if (worker) eligible.and("recovery.schemaVersion").is(1).and("recovery.nextAt").lte(now);
        String token = UUID.randomUUID().toString();
        Instant until = now.plus(properties.getLease());
        ExamCreationOperation claimed = mongo.findAndModify(Query.query(eligible), new Update()
                        .set("recovery.leaseToken", token).set("recovery.leaseOwner", instance)
                        .set("recovery.leaseUntil", until).set("recovery.status", ReservationRecovery.Status.IN_FLIGHT)
                        .inc("version", 1), FindAndModifyOptions.options().returnNew(true), ExamCreationOperation.class);
        return claimed == null ? null : new Lease(id, token, until, worker,
                System.nanoTime() + properties.getAttemptTimeout().toNanos());
    }

    public <T> T within(Lease lease, Supplier<T> work) {
        if (current.get() != null) throw new IllegalStateException("Nested Reservation execution is forbidden");
        current.set(lease);
        try { return work.get(); } finally { current.remove(); }
    }

    public ExamCreationOperation fence(String id, Long expectedVersion) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Reservation fence requires a Mongo transaction");
        }
        Lease lease = required(id);
        Query query = owned(lease);
        if (expectedVersion != null) query.addCriteria(Criteria.where("version").is(expectedVersion));
        ExamCreationOperation operation = mongo.findAndModify(query, new Update().inc("version", 1),
                FindAndModifyOptions.options().returnNew(true), ExamCreationOperation.class);
        if (operation == null) throw new ReservationRecoveryException(LEASE_LOST);
        return operation;
    }

    public void beforeRemote(String id) {
        Lease lease = required(id);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Billing HTTP inside Mongo transaction is forbidden");
        }
        if (lease.worker && (++lease.calls > 2 || System.nanoTime() + java.time.Duration.ofSeconds(7).toNanos() > lease.deadlineNanos)) {
            throw new ReservationRecoveryException(ReservationRecoveryException.Reason.BUDGET_EXHAUSTED);
        }
        if (mongo.exists(Query.query(Criteria.where("_id").is(ReservationAuthCircuit.ID)
                .and("blocked").is(true)), ReservationAuthCircuit.COLLECTION)) {
            throw new ReservationRecoveryException(ReservationRecoveryException.Reason.AUTH_BLOCKED);
        }
        ExamCreationOperation operation = mongo.findOne(owned(lease), ExamCreationOperation.class);
        if (operation == null) throw new ReservationRecoveryException(LEASE_LOST);
        requireOwner(operation.getUserId());
    }

    public Lease required(String id) {
        Lease lease = current.get();
        if (lease == null || !lease.id.equals(id) || !lease.until.isAfter(now())) {
            throw new ReservationRecoveryException(LEASE_LOST);
        }
        return lease;
    }

    public void scheduleInitialProgress(ExamCreationOperation operation) {
        ReservationRecovery recovery = operation.getRecovery();
        if (recovery == null || recovery.getFirstAt() != null || operation.isTerminal()) return;
        recovery.setNextAt(operation.getUpdatedAt().plus(
                operation.getState() == ExamCreationState.PREPARED || operation.getState() == ExamCreationState.RESERVED
                        ? properties.getPrecommitStale() : properties.getPoll()));
    }

    public void requireOwner(String userId) {
        var deny = mongo.findById(userId, web.tosunsaeng.domain.withdrawal.domain.WithdrawnUserAccessDeny.class);
        var guard = mongo.findById(userId, web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard.class);
        if ((deny != null && deny.isActiveAt(now())) || (guard != null && guard.isMerged())) {
            throw new ReservationRecoveryException(ReservationRecoveryException.Reason.OWNER_BLOCKED);
        }
    }

    public boolean worker() { return current.get() != null && current.get().worker; }
    public Instant now() { return clock.instant(); }

    public void release(Lease lease) {
        mongo.updateFirst(owned(lease).addCriteria(Criteria.where("recovery.status").is(ReservationRecovery.Status.IN_FLIGHT)),
                new Update().set("recovery.status", ReservationRecovery.Status.READY)
                        .unset("recovery.leaseToken").unset("recovery.leaseOwner").unset("recovery.leaseUntil")
                        .inc("version", 1), ExamCreationOperation.class);
    }

    public Query owned(Lease lease) {
        return Query.query(Criteria.where("_id").is(lease.id).and("recovery.leaseToken").is(lease.token)
                .and("recovery.leaseUntil").gt(now()));
    }

    /** Explicitly outside the old transaction/session; absence is not proof of failed commit. */
    public ExamCreationOperation freshOperation(String id) {
        return fresh(COLLECTION, "_id", id, ExamCreationOperation.class);
    }

    public ExamSession freshSession(String id) {
        return fresh("exam_sessions", "_id", id, ExamSession.class);
    }

    private <T> T fresh(String collection, String key, String id, Class<T> type) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Outcome observation requires a fresh transaction boundary");
        }
        Document value = mongo.getCollection(collection).withReadConcern(ReadConcern.MAJORITY)
                .withReadPreference(ReadPreference.primary()).find(new Document(key, id)).first();
        return value == null ? null : mongo.getConverter().read(type, value);
    }

    public static final class Lease {
        private final String id;
        private final String token;
        private final Instant until;
        private final boolean worker;
        private final long deadlineNanos;
        private int calls;
        private Lease(String id, String token, Instant until, boolean worker, long deadlineNanos) {
            this.id = id; this.token = token; this.until = until; this.worker = worker; this.deadlineNanos = deadlineNanos;
        }
        public String id() { return id; }
    }
}
