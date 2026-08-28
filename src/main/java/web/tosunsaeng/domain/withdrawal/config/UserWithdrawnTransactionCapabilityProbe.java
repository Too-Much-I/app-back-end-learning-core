package web.tosunsaeng.domain.withdrawal.config;

import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Component
@Profile({"staging", "prod"})
@ConditionalOnProperty(prefix = "app.user-withdrawn", name = "consumer-enabled", havingValue = "true")
public class UserWithdrawnTransactionCapabilityProbe implements ApplicationRunner {

    static final String COLLECTION = "user_withdrawn_transaction_probe";

    private final MongoTemplate mongoTemplate;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    public UserWithdrawnTransactionCapabilityProbe(
            MongoTemplate mongoTemplate,
            @Qualifier("userWithdrawnTransactionOperations") TransactionOperations transactionOperations,
            @Qualifier("userWithdrawnClock") Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.transactionOperations = transactionOperations;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        String probeId = UUID.randomUUID().toString();
        try {
            transactionOperations.executeWithoutResult(status -> {
                mongoTemplate.insert(
                        new Document("_id", probeId)
                                .append("createdAt", Date.from(clock.instant())),
                        COLLECTION
                );
                status.setRollbackOnly();
            });
            if (mongoTemplate.exists(
                    Query.query(Criteria.where("_id").is(probeId)),
                    COLLECTION)) {
                throw new IllegalStateException("UserWithdrawn transaction capability probe left a canary document");
            }
            log.info("UserWithdrawn Mongo 검증 완료 event=transaction_capability outcome=verified");
        } catch (RuntimeException exception) {
            throw new IllegalStateException("UserWithdrawn Mongo transaction capability verification failed", exception);
        }
    }
}
