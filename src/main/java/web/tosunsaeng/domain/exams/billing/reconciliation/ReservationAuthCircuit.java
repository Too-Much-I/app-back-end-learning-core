package web.tosunsaeng.domain.exams.billing.reconciliation;

import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Independent from the AttemptGroup outbox circuit. No credential is stored. */
@Component
public class ReservationAuthCircuit {
    public static final String COLLECTION = "reservation_reconciliation_control";
    public static final String ID = "auth:v1";
    private final MongoTemplate mongo;
    private final ReservationOperationExecution execution;
    private final ReservationReconciliationProperties properties;

    public ReservationAuthCircuit(MongoTemplate mongo, ReservationOperationExecution execution,
                                  ReservationReconciliationProperties properties) {
        this.mongo = new MongoTemplate(mongo.getMongoDatabaseFactory(), mongo.getConverter());
        this.mongo.setWriteConcern(com.mongodb.WriteConcern.MAJORITY); this.execution = execution; this.properties = properties;
    }

    public boolean blocked() {
        return mongo.exists(Query.query(Criteria.where("_id").is(ID).and("blocked").is(true)), COLLECTION);
    }

    public void blockFromHttp() {
        // OFF rollout has no probe scheduler; do not create a permanent HTTP outage there.
        if (properties.isEnabled()) block();
    }

    public void block() {
        Instant now = execution.now();
        try {
            // Preserve an undelivered incident across reopen/reblock; do not overwrite its evidence.
            Document existing = mongo.findById(ID, Document.class, COLLECTION);
            Document recovery = existing == null ? null : existing.get("recovery", Document.class);
            boolean pending = recovery != null && ("PENDING".equals(recovery.getString("alertStatus"))
                    || "UNSUBMITTED".equals(recovery.getString("alertStatus")));
            if (pending) {
                mongo.updateFirst(Query.query(Criteria.where("_id").is(ID).and("blocked").ne(true)),
                        new Update().set("blocked", true).set("nextProbeAt", now.plus(properties.getAuthProbeInterval()))
                                .unset("probeToken"), COLLECTION);
                return;
            }
            mongo.findAndModify(Query.query(Criteria.where("_id").is(ID).and("blocked").ne(true)),
                    new Update().set("blocked", true).set("nextProbeAt", now.plus(properties.getAuthProbeInterval()))
                            .set("recovery.alertIncidentId", UUID.randomUUID().toString())
                            .set("recovery.alertReason", "auth_failure").set("recovery.alertStatus", "PENDING")
                            .set("recovery.alertNextAt", now).set("recovery.alertCreatedAt", now)
                            .set("recovery.alertAttempts", 0).unset("probeToken"),
                    FindAndModifyOptions.options().upsert(true), Document.class, COLLECTION);
        } catch (DuplicateKeyException alreadyBlocked) {
            // Existing circuit won. Do not extend its deadline or emit one event per operation.
        }
    }

    public String claimProbe() {
        String token = UUID.randomUUID().toString();
        Document claimed = mongo.findAndModify(Query.query(Criteria.where("_id").is(ID).and("blocked").is(true)
                        .and("nextProbeAt").lte(execution.now())),
                new Update().set("probeToken", token).set("nextProbeAt", execution.now().plus(properties.getAuthProbeInterval())),
                FindAndModifyOptions.options().returnNew(true), Document.class, COLLECTION);
        return claimed == null ? null : token;
    }

    public void recovered(String token) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(ID).and("blocked").is(true).and("probeToken").is(token)),
                new Update().set("blocked", false).unset("probeToken"), COLLECTION);
    }
}
