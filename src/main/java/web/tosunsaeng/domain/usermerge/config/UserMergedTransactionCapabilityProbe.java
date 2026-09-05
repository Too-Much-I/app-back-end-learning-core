package web.tosunsaeng.domain.usermerge.config;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
@ConditionalOnExpression("${app.user-merged.writer-enabled:false} || "
        + "${app.user-merged.consumer-enabled:false}")
public class UserMergedTransactionCapabilityProbe implements ApplicationRunner {

    static final String COLLECTION = "user_merged_transaction_probe";

    private final MongoTemplate mongoTemplate;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    public UserMergedTransactionCapabilityProbe(
            MongoTemplate mongoTemplate,
            @Qualifier("userOwnedTransactionOperations") TransactionOperations transactionOperations,
            @Qualifier("userMergedClock") Clock clock
    ) {
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
                    COLLECTION
            )) {
                throw new IllegalStateException("UserMerged transaction probe left a canary document");
            }
            log.info("UserMerged Mongo 검증 완료 event=transaction_capability outcome=verified");
        } catch (RuntimeException failure) {
            throw new IllegalStateException(
                    "UserMerged Mongo transaction capability verification failed",
                    failure
            );
        }
    }
}
