package web.tosunsaeng.domain.learningrecorddeletion.config;

import com.mongodb.client.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import web.tosunsaeng.domain.exams.billing.*;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

@Testcontainers
class BillingDeletionStartupMongoTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");
    MongoClient connection; MongoTemplate mongo;
    @BeforeEach void setup() {
        connection = MongoClients.create(MONGO.getReplicaSetUrl());
        mongo = new MongoTemplate(new SimpleMongoClientDatabaseFactory(connection, "deletion-startup"));
        mongo.getDb().drop();
        mongo.getCollection("learning_record_deletion_targets").createIndex(new Document("prepared", 1)
                .append("billingDelivery.state", 1).append("billingDelivery.nextAttemptAt", 1).append("_id", 1));
    }
    @AfterEach void close() { connection.close(); }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"missing", "false", "true"})
    void deliveryProofIsPostDeploymentEvidenceAndIsNeverWritten(String flag) {
        if (!flag.equals("missing")) mongo.insert(new Document("_id", "v1")
                .append("billingDeliveryVerified", Boolean.parseBoolean(flag)), "learning_record_deletion_rollout");
        Document before = mongo.findById("v1", Document.class, "learning_record_deletion_rollout");
        assertThatCode(() -> create(true, true, true)).doesNotThrowAnyException();
        assertThat(mongo.findById("v1", Document.class, "learning_record_deletion_rollout")).isEqualTo(before);
    }
    @Test void requiredIndexStillBlocksStartup() {
        mongo.getCollection("learning_record_deletion_targets").dropIndexes();
        assertThatThrownBy(() -> create(true, true, true)).isInstanceOf(IllegalStateException.class);
    }
    @Test void unsafeDeliveryTtlStillBlocksStartup() {
        mongo.insert(new Document("billingDelivery", new Document("state", "PENDING"))
                .append("expiresAt", new java.util.Date()), "learning_record_deletion_targets");
        assertThatThrownBy(() -> create(true, true, true)).isInstanceOf(IllegalStateException.class);
    }
    @Test void signerAndFenceAndWorkerStillRequired() {
        assertThatThrownBy(() -> create(false, true, true)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> create(true, false, true)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> create(true, true, false)).isInstanceOf(IllegalStateException.class);
    }
    void create(boolean signing, boolean fence, boolean worker) {
        var env = new MockEnvironment().withProperty("app.learning-record-deletion.writer-fence-enabled", "" + fence)
                .withProperty("app.learning-record-deletion.worker-enabled", "" + worker);
        var jwt = new WorkloadJwtProperties(); jwt.setSenderEnabled(signing);
        var saga = new BillingSagaProperties(); saga.setBaseUrl("https://billing.example.invalid");
        new BillingDeletionConfiguration().billingDeletionClient(env, jwt, saga,
                mock(BillingWorkloadTokenIssuer.class), Clock.systemUTC(), mongo);
    }
}
