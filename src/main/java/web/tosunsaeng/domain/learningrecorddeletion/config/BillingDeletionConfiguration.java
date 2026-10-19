package web.tosunsaeng.domain.learningrecorddeletion.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.bson.Document;
import web.tosunsaeng.domain.exams.billing.*;
import web.tosunsaeng.domain.learningrecorddeletion.application.*;
import web.tosunsaeng.domain.learningrecorddeletion.infrastructure.*;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardService;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.billing.exam-deletion.sender-enabled", havingValue = "true")
public class BillingDeletionConfiguration {
    @Bean @DependsOn("deletionStartupValidator")
    BillingDeletionClient billingDeletionClient(Environment env, WorkloadJwtProperties jwt,
            BillingSagaProperties saga, BillingWorkloadTokenIssuer issuer, Clock clock, MongoTemplate mongo) {
        if (!jwt.isSenderEnabled() || !env.getProperty("app.learning-record-deletion.writer-fence-enabled", Boolean.class, false)
                || !env.getProperty("app.learning-record-deletion.worker-enabled", Boolean.class, false))
            throw new IllegalStateException("Deletion sender requires workload signing and deletion fences");
        // Real delivery is verified after deployment through the frontend. Do not require its
        // result to start the sender that produces it. Storage/drain gates remain in the dependency.
        verifyDeliveryStorage(mongo);
        return new BillingDeletionClient(saga.getBaseUrl(), issuer, clock);
    }
    /** Shared with the bounded operator probe; does not grant rollout approval. */
    public static void verifyDeliveryStorage(MongoTemplate mongo) {
        Document keys = new Document("prepared",1).append("billingDelivery.state",1)
                .append("billingDelivery.nextAttemptAt",1).append("_id",1);
        boolean index = mongo.getCollection("learning_record_deletion_targets").listIndexes().into(new java.util.ArrayList<>()).stream()
                .anyMatch(i -> keys.equals(i.get("key")) && i.get("expireAfterSeconds") == null
                        && !Boolean.TRUE.equals(i.get("unique")) && i.get("partialFilterExpression") == null
                        && i.get("collation") == null && !Boolean.TRUE.equals(i.get("sparse")) && !Boolean.TRUE.equals(i.get("hidden")));
        if (!index || mongo.getCollection("learning_record_deletion_targets").find(new Document("billingDelivery",new Document("$ne",null))
                .append("expiresAt",new Document("$ne",null))).first() != null)
            throw new IllegalStateException("Deletion delivery index or retention is not verified");
    }
    @Bean BillingDeletionSender billingDeletionSender(MongoTemplate mongo, DeletionTransactions tx,
            UserOwnershipGuardService guards, BillingDeletionClient client, Clock clock, WorkloadJwtProperties jwt) {
        return new BillingDeletionSender(mongo, tx, guards, client, clock, jwt.getEnvironment());
    }
    @Bean BillingDeletionScheduler billingDeletionScheduler(BillingDeletionSender sender) {
        return new BillingDeletionScheduler(sender);
    }
}
