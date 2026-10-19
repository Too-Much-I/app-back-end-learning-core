package web.tosunsaeng.domain.learningrecorddeletion;

import com.mongodb.client.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.learningrecorddeletion.application.*;
import web.tosunsaeng.domain.learningrecorddeletion.config.*;
import web.tosunsaeng.domain.learningrecorddeletion.domain.DeletionTarget;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import web.tosunsaeng.domain.usermerge.config.UserMergedConfiguration;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class DeletionWiringMongoTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");
    static final String OWNER = "00000000-0000-0000-0000-000000000001";
    MongoClient client; MongoTemplate mongo; ApplicationContextRunner runner;
    @BeforeEach void setup() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        mongo = new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, "deletion-wiring")); mongo.getDb().drop();
        prepare();
        runner = new ApplicationContextRunner().withUserConfiguration(DeletionActivationBarrier.class, DeletionConfiguration.class, UserMergedConfiguration.class)
                .withBean(MongoDatabaseFactory.class, mongo::getMongoDatabaseFactory).withBean(MongoTemplate.class, () -> mongo)
                .withBean(UserOwnershipGuardRepository.class, () -> new MongoRepositoryFactory(mongo).getRepository(UserOwnershipGuardRepository.class))
                .withPropertyValues("app.user-merged.writer-enabled=false", "app.user-merged.consumer-enabled=false",
                        "app.learning-record-deletion.writer-fence-enabled=true", "app.learning-record-deletion.read-fence-enabled=true",
                        "app.learning-record-deletion.billing-continuation-enabled=true", "app.learning-record-deletion.aggregate-enabled=true");
    }
    @AfterEach void close() { client.close(); }

    @Test void deletionOnlyRegistersOwnerTransactionFencesAndSynchronousStatistics() {
        runner.run(c -> {
            assertThat(c).hasNotFailed();
            UserOwnedTransactionExecutor writers = c.getBean(UserOwnedTransactionExecutor.class);
            assertThat(writers.enabled()).isTrue();
            writers.execute(OWNER, () -> mongo.insert(ExamSession.builder().examId("exam").userId(OWNER).build()));
            assertThat(mongo.getCollection("learning_activity_daily_aggregates").countDocuments()).isEqualTo(1);
            c.getBean(DeletionCommandService.class).request(OWNER, "139f345b-9be8-43a3-a63d-9108df972741");
            assertThatThrownBy(() -> writers.execute(OWNER, () -> mongo.insert(ExamSession.builder().examId("new").userId(OWNER).build())))
                    .isInstanceOf(DeletionFailure.class);
            assertThat(mongo.findById("new", ExamSession.class)).isNull();
            assertThat(c.getBean(DeletionAccess.class).hidden(DeletionTarget.Type.EXAM, "exam", OWNER)).isTrue();
            assertThatThrownBy(() -> mongo.insert(ExamSession.builder().examId("unprotected").userId(OWNER).build()))
                    .isInstanceOf(IllegalStateException.class);
        });
    }
    @Test void missingRolloutEvidenceFailsWithoutStartingAWorker() {
        mongo.getCollection("learning_record_deletion_rollout").deleteOne(new Document("_id", "v1"));
        runner.run(c -> assertThat(c).hasFailed());
    }
    @Test void userMergedWriterCanShareTheSameDeletionWriter() {
        runner.withPropertyValues("app.user-merged.writer-enabled=true").run(c -> {
            assertThat(c).hasNotFailed();
            var writers = c.getBean(UserOwnedTransactionExecutor.class);
            writers.execute(OWNER, () -> mongo.insert(ExamSession.builder().examId("exam").userId(OWNER).build()));
            assertThat(mongo.getCollection("learning_activity_daily_aggregates").countDocuments()).isEqualTo(1);
        });
    }
    @Test void productionLegacyIsRejected() {
        runner.withPropertyValues("spring.profiles.active=prod", "app.auth.mode=legacy").run(c -> assertThat(c).hasFailed());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"test", "staging", "prod"})
    void commandStartupDoesNotRequireMobileApproval(String profile) {
        var manifest = mongo.getCollection("learning_record_deletion_rollout");
        manifest.updateOne(new Document("_id", "v1"), new Document("$set", new Document("storageVerified", true)));
        var env = new org.springframework.mock.env.MockEnvironment()
                .withProperty("app.auth.mode", "jwt")
                .withProperty("app.learning-record-deletion.command-enabled", "true");
        env.setActiveProfiles(profile);
        assertThatCode(() -> new DeletionStartupValidator(mongo, env).afterPropertiesSet()).doesNotThrowAnyException();
        manifest.updateOne(new Document("_id", "v1"), new Document("$set", new Document("mobileContractVerified", false)));
        assertThatCode(() -> new DeletionStartupValidator(mongo, env).afterPropertiesSet()).doesNotThrowAnyException();
        manifest.updateOne(new Document("_id", "v1"), new Document("$unset", new Document("storageVerified", "")));
        assertThatThrownBy(() -> new DeletionStartupValidator(mongo, env).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
    }

    void prepare() {
        for (String c : List.of("user_ownership_guards", "withdrawn_user_access_denies", "learning_record_deletion_commands")) mongo.createCollection(c);
        mongo.getCollection("learning_record_deletion_operations").createIndex(new Document("userId", 1),
                new com.mongodb.client.model.IndexOptions().unique(true).partialFilterExpression(new Document("activeGuard", true)));
        index("learning_record_deletion_operations", new Document("activeGuard", 1).append("nextAttemptAt", 1).append("leaseUntil", 1));
        index("learning_record_deletion_operations", new Document("userId", 1).append("requestedAt", -1));
        index("learning_record_deletion_targets", new Document("targetType", 1).append("aggregateId", 1));
        index("learning_record_deletion_targets", new Document("deletionId", 1).append("targetType", 1).append("callbackFence", 1));
        for (String s : List.of("operations", "commands", "targets"))
            mongo.getCollection("learning_record_deletion_" + s).createIndex(new Document("expiresAt", 1),
                    new com.mongodb.client.model.IndexOptions().expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS));
        index("learning_record_billing_continuations", new Document("userId", 1).append("transferState", 1));
        for (String root : List.of("exam_sessions", "challenge_10s_attempts")) index(root, new Document("userId", 1).append("_id", 1));
        mongo.getCollection("learning_activity_daily_aggregates").createIndex(new Document("bucketDate", 1).append("metric", 1).append("examType", 1).append("outcome", 1),
                new com.mongodb.client.model.IndexOptions().unique(true));
        mongo.insert(new Document("_id", "v1").append("writersDrained", true).append("inventoryApproved", true).append("writerPathsVerified", true), "learning_record_deletion_rollout");
        mongo.insert(new Document("_id", "v1").append("liveStartedAt", Instant.parse("2026-10-03T00:00:00Z"))
                .append("legacyPolicy", "LEGACY_FIRST_EVENTS_UNCOVERED"), "learning_activity_collection_coverage");
    }
    void index(String c, Document key) { mongo.getCollection(c).createIndex(key); }
}
