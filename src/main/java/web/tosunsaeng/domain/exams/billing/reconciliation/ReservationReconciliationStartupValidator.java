package web.tosunsaeng.domain.exams.billing.reconciliation;

import io.sentry.IHub;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import web.tosunsaeng.domain.exams.billing.BillingSagaProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class ReservationReconciliationStartupValidator implements ApplicationRunner {
    public static final Map<String, List<String>> INDEXES = Map.of(
            "reservation_recovery_due", List.of("recovery.schemaVersion", "activeGuard", "recovery.status", "recovery.nextAt", "_id"),
            "reservation_recovery_lease", List.of("recovery.schemaVersion", "activeGuard", "recovery.status", "recovery.leaseUntil", "_id"),
            "reservation_recovery_early_due", List.of("recovery.schemaVersion", "activeGuard", "recovery.earlyAlert.alertIncidentId", "createdAt", "_id"),
            "reservation_recovery_early_pending", List.of("recovery.earlyAlert.alertStatus", "recovery.earlyAlert.alertNextAt", "_id"),
            "reservation_recovery_alert", List.of("recovery.alertStatus", "recovery.alertNextAt", "_id"));
    private final ReservationReconciliationProperties properties;
    private final BillingSagaProperties billing;
    private final MongoTemplate mongo;
    private final ObjectProvider<TransactionOperations> transactions;
    private final IHub hub;
    private final ObjectProvider<web.tosunsaeng.domain.exams.billing.BillingSagaIndexValidator> sagaIndexes;

    public ReservationReconciliationStartupValidator(ReservationReconciliationProperties properties, BillingSagaProperties billing,
            MongoTemplate mongo, @Qualifier("billingTransactionOperations") ObjectProvider<TransactionOperations> transactions,
            IHub hub, ObjectProvider<web.tosunsaeng.domain.exams.billing.BillingSagaIndexValidator> sagaIndexes) {
        this.properties = properties; this.billing = billing; this.mongo = mongo; this.transactions = transactions; this.hub = hub; this.sagaIndexes = sagaIndexes;
    }

    @Override
    public void run(ApplicationArguments args) {
        properties.validateExecutionLimits(); // Shared HTTP fencing also needs valid lease limits.
        if (!properties.isEnabled()) return; // No DB, remote or Sentry access when disabled.
        properties.validate();
        if (!billing.isCreationSagaEnabled() || transactions.getIfAvailable() == null
                || billing.getReadTimeout().compareTo(Duration.ofSeconds(5)) > 0
                || billing.getConnectTimeout().compareTo(Duration.ofSeconds(2)) > 0
                || properties.getAttemptTimeout().compareTo(Duration.ofSeconds(14)) < 0 || !hub.isEnabled()) {
            throw new IllegalStateException("Reservation recovery requires saga, bounded HTTP, transaction manager and enabled Sentry");
        }
        var baseValidator = sagaIndexes.getIfAvailable();
        if (baseValidator == null) throw new IllegalStateException("Billing saga index validator is required");
        baseValidator.validate(true);
        Document hello = mongo.executeCommand(new Document("hello", 1));
        if (hello.get("setName") == null && !"isdbgrid".equals(hello.getString("msg"))) {
            throw new IllegalStateException("Reservation recovery requires Mongo transactions");
        }
        if (!mongo.collectionExists(ReservationAuthCircuit.COLLECTION)) {
            throw new IllegalStateException("Reservation recovery control collection must be prepared offline");
        }
        for (var expected : INDEXES.entrySet()) validateIndex(ReservationOperationExecution.COLLECTION, expected.getKey(), expected.getValue());
        validateIndex(ReservationAuthCircuit.COLLECTION, "reservation_recovery_alert", INDEXES.get("reservation_recovery_alert"));
        for (Document index : mongo.getCollection(ReservationOperationExecution.COLLECTION).listIndexes()) {
            if (index.containsKey("expireAfterSeconds") && !"ttl_exam_creation_purge".equals(index.getString("name"))) {
                throw new IllegalStateException("Unexpected Reservation operation TTL");
            }
        }
        for (Document index : mongo.getCollection(ReservationAuthCircuit.COLLECTION).listIndexes()) {
            if (index.containsKey("expireAfterSeconds")) throw new IllegalStateException("Reservation circuit must not expire");
        }
        // Read-only transaction round-trip also checks the actual manager/session integration.
        transactions.getObject().execute(status -> mongo.findById(ReservationAuthCircuit.ID, Document.class, ReservationAuthCircuit.COLLECTION));
    }

    private void validateIndex(String collection, String name, List<String> fields) {
        Document expected = new Document();
        fields.forEach(field -> expected.append(field, 1));
        for (Document index : mongo.getCollection(collection).listIndexes()) {
            if (!name.equals(index.getString("name"))) continue;
            Document actual = index.get("key", Document.class);
            if (actual != null && List.copyOf(actual.keySet()).equals(fields) && actual.equals(expected)
                    && !Boolean.TRUE.equals(index.get("unique")) && !Boolean.TRUE.equals(index.get("sparse"))
                    && !Boolean.TRUE.equals(index.get("hidden")) && !index.containsKey("partialFilterExpression")
                    && !index.containsKey("expireAfterSeconds") && !index.containsKey("collation")) return;
        }
        throw new IllegalStateException("Reservation recovery index missing or incompatible: " + name);
    }
}
