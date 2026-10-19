package web.tosunsaeng.domain.exams.billing.reconciliation;

import io.micrometer.core.instrument.MeterRegistry;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.PropagationContext;
import io.sentry.SentryEvent;
import io.sentry.protocol.SentryId;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import web.tosunsaeng.global.sentry.SentryEventSanitizer;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Durable pending metadata lives on its operation/circuit; SDK acceptance is NOT delivery acknowledgement. */
@Component
public class ReservationReconciliationAlertReporter {
    public static final String SOURCE = "billing_reservation_reconcile";
    public static final String INCIDENT_CONTEXT = "correlation.alert_incident_id";
    public static final String EARLY_REASON = "creation_blocked_5m";
    private static final java.time.Duration EARLY_AFTER = java.time.Duration.ofMinutes(5);
    public static final Set<String> REASONS = Set.of("retry_exhausted", "state_inconsistent", "auth_failure", "owner_blocked", EARLY_REASON);
    private final MongoTemplate mongo;
    private final ReservationOperationExecution execution;
    private final ReservationReconciliationProperties properties;
    private final IHub hub;
    private final MeterRegistry metrics;
    private final SentryEventSanitizer sanitizer;

    public ReservationReconciliationAlertReporter(MongoTemplate mongo, ReservationOperationExecution execution,
            ReservationReconciliationProperties properties, IHub hub, MeterRegistry metrics, SentryEventSanitizer sanitizer) {
        this.mongo = new MongoTemplate(mongo.getMongoDatabaseFactory(), mongo.getConverter());
        this.mongo.setWriteConcern(com.mongodb.WriteConcern.MAJORITY); this.execution = execution; this.properties = properties;
        this.hub = hub; this.metrics = metrics; this.sanitizer = sanitizer;
    }

    public void poll() {
        if (!properties.isEnabled()) return;
        // Independent bounded lanes: a warning cannot starve or overwrite a quarantine alert.
        // At most three SDK captures per poll, independent from the business worker queue.
        recoverOne(ReservationAuthCircuit.COLLECTION, "recovery.");
        recoverOne(ReservationOperationExecution.COLLECTION, "recovery.");
        enqueueEarlyWarnings();
        recoverOne(ReservationOperationExecution.COLLECTION, "recovery.earlyAlert.");
    }

    void enqueueEarlyWarnings() {
        if (!properties.isEnabled()) return;
        // Auth failure already has one global incident; do not fan it out to every user.
        if (mongo.exists(Query.query(Criteria.where("_id").is(ReservationAuthCircuit.ID)
                .and("blocked").is(true)), ReservationAuthCircuit.COLLECTION)) return;
        Instant now = execution.now();
        for (int i = 0; i < properties.getBatchSize(); i++) {
            Query candidate = Query.query(Criteria.where("recovery.schemaVersion").is(1)
                    .and("activeGuard").is(true).and("recovery.earlyAlert.alertIncidentId").is(null)
                    .and("createdAt").lte(now.minus(EARLY_AFTER))
                    .and("state").in("PREPARED", "RESERVED", "SESSION_COMMITTED", "CANCEL_PENDING")
                    .and("recovery.status").in("READY", "IN_FLIGHT")
                    .and("recovery.alertIncidentId").is(null)
                    .andOperator(new Criteria().orOperator(Criteria.where("recovery.leaseUntil").is(null),
                            Criteria.where("recovery.leaseUntil").lte(now))))
                    .with(Sort.by("createdAt", "_id"));
            Update pending = new Update().set("recovery.earlyAlert.alertIncidentId", UUID.randomUUID().toString())
                    .set("recovery.earlyAlert.alertReason", EARLY_REASON).set("recovery.earlyAlert.alertStatus", "PENDING")
                    .set("recovery.earlyAlert.alertNextAt", now).set("recovery.earlyAlert.alertCreatedAt", now)
                    .set("recovery.earlyAlert.alertAttempts", 0).inc("version", 1);
            Document found = mongo.findAndModify(candidate, pending, FindAndModifyOptions.options().returnNew(true),
                    Document.class, ReservationOperationExecution.COLLECTION);
            if (found == null) break;
        }
    }

    private void recoverOne(String collection, String prefix) {
        Instant now = execution.now();
        String token = UUID.randomUUID().toString();
        Query query = Query.query(Criteria.where(prefix + "alertStatus").is("PENDING")
                .and(prefix + "alertNextAt").lte(now).andOperator(new Criteria().orOperator(
                        Criteria.where(prefix + "alertLeaseUntil").is(null), Criteria.where(prefix + "alertLeaseUntil").lte(now))))
                .with(Sort.by(prefix + "alertNextAt", "_id"));
        Document claimed = mongo.findAndModify(query, new Update().set(prefix + "alertLeaseToken", token)
                        .set(prefix + "alertLeaseUntil", now.plus(properties.getLease())).inc("version", 1),
                FindAndModifyOptions.options().returnNew(true), Document.class, collection);
        if (claimed == null) return;
        Document metadata = claimed.get("recovery", Document.class);
        if (prefix.equals("recovery.earlyAlert.")) metadata = metadata.get("earlyAlert", Document.class);
        Query owned = Query.query(Criteria.where("_id").is(claimed.get("_id"))
                .and(prefix + "alertLeaseToken").is(token).and(prefix + "alertLeaseUntil").gt(now));
        String reason = metadata.getString("alertReason"), incident = metadata.getString("alertIncidentId");
        int attempts = ((Number) metadata.getOrDefault("alertAttempts", 0)).intValue();
        Date created = metadata.getDate("alertCreatedAt");
        Update next = new Update().unset(prefix + "alertLeaseToken").unset(prefix + "alertLeaseUntil").inc("version", 1);
        String outcome;
        if (created == null || !REASONS.contains(reason) || !ReservationSnapshotValidator.uuid4(incident)
                || attempts >= properties.getAlertMaxAttempts()
                || !now.isBefore(created.toInstant().plus(properties.getAlertMaxAge()))) {
            next.set(prefix + "alertStatus", "UNSUBMITTED");
            outcome = "exhausted";
        } else {
            next.inc(prefix + "alertAttempts", 1);
            if (capture(reason, incident)) {
                next.set(prefix + "alertStatus", "SDK_ACCEPTED");
                outcome = "sdk_accepted";
            } else {
                next.set(prefix + "alertNextAt", now.plusSeconds(ReservationRecoveryStore.retryDelay(attempts + 1, null)));
                outcome = "submission_failed";
            }
        }
        mongo.updateFirst(owned, next, collection);
        metrics.counter("learning_core.billing.reconciliation.alert", "outcome", outcome).increment();
        if (created != null) metrics.timer("learning_core.billing.reconciliation.alert_age")
                .record(java.time.Duration.ofMillis(Math.max(0, now.toEpochMilli() - created.getTime())));
    }

    boolean capture(String reason, String incident) {
        if (!REASONS.contains(reason) || !ReservationSnapshotValidator.uuid4(incident) || !hub.isEnabled()) return false;
        SentryEvent event = new SentryEvent();
        event.setEventId(new SentryId(UUID.fromString(incident)));
        event.setTag("capture.source", SOURCE);
        event.setTag("service", "learning-core");
        event.setTag("reconciliation.reason", reason);
        event.getContexts().put(INCIDENT_CONTEXT, Map.of("value", incident));
        // Sanitizer owns the fixed fingerprint and strips all non-allowlisted content again before send.
        SentryEvent safe = sanitizer.execute(event, new Hint());
        if (safe == null) return false;
        try {
            SentryId id = hub.captureEvent(safe, new Hint(), scope -> {
                scope.clear(); scope.getContexts().clear(); scope.clearSession();
                scope.setPropagationContext(new PropagationContext()); scope.setReplayId(SentryId.EMPTY_ID);
            });
            return id != null && !SentryId.EMPTY_ID.equals(id);
        } catch (RuntimeException unavailable) { return false; }
    }
}
