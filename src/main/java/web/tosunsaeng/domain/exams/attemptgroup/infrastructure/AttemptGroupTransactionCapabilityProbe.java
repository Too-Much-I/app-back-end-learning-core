package web.tosunsaeng.domain.exams.attemptgroup.infrastructure;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.Date;
import java.util.UUID;

@Component
@Profile({"staging", "prod"})
@ConditionalOnProperty(
        prefix = "app.attempt-group-events",
        name = "writer-enabled",
        havingValue = "true"
)
public class AttemptGroupTransactionCapabilityProbe implements ApplicationRunner {
    static final String COLLECTION = "attempt_group_transaction_probe";
    private final MongoTemplate mongoTemplate;
    private final TransactionOperations transactions;
    private final Clock clock;

    public AttemptGroupTransactionCapabilityProbe(
            MongoTemplate mongoTemplate,
            @Qualifier("attemptGroupTransactionOperations") TransactionOperations transactions,
            @Qualifier("gradingClock") Clock clock
    ) {
        this.mongoTemplate = mongoTemplate;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        String probeId = UUID.randomUUID().toString();
        try {
            transactions.executeWithoutResult(status -> {
                mongoTemplate.insert(new Document("_id", probeId)
                        .append("createdAt", Date.from(clock.instant())), COLLECTION);
                status.setRollbackOnly();
            });
            if (mongoTemplate.exists(Query.query(Criteria.where("_id").is(probeId)), COLLECTION)) {
                throw new IllegalStateException("AttemptGroup transaction probe left a canary document");
            }
        } catch (RuntimeException failure) {
            throw new IllegalStateException(
                    "AttemptGroup Mongo transaction capability verification failed", failure);
        }
    }
}
