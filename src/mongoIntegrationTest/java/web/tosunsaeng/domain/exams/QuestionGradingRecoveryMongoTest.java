package web.tosunsaeng.domain.exams;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import web.tosunsaeng.domain.exams.domain.entity.QuestionGradingJob;
import web.tosunsaeng.domain.exams.domain.repository.*;

import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class QuestionGradingRecoveryMongoTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");
    static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");
    MongoClient client;
    MongoTemplate mongo;
    QuestionGradingJobRepository repository;

    @BeforeEach void setup() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        mongo = new MongoTemplate(client, "recovery-regression");
        mongo.getDb().drop();
        mongo.createCollection(QuestionGradingJob.class);
        repository = new MongoRepositoryFactory(mongo).getRepository(QuestionGradingJobRepository.class,
                RepositoryFragments.just(new QuestionGradingJobRecoveryImpl(mongo)));
    }

    @AfterEach void close() { client.close(); }

    @Test void repeatedRecoveryAdvancesCycleAndVersionAndRejectsStaleClaims() {
        var job = QuestionGradingJob.pending("job", "exam", 1, 0, "key", NOW);
        job.startProcessing(NOW);
        job.complete(NOW);
        repository.insert(job);
        long version = repository.findById("job").orElseThrow().getVersion();
        assertThat(repository.reopenCompletedMissingResult("job", 0, NOW.plusSeconds(1))).isEqualTo(1);
        var recovered = repository.findById("job").orElseThrow();
        assertThat(recovered.effectiveRecoveryCycle()).isEqualTo(1);
        assertThat(recovered.getVersion()).isEqualTo(version + 1);
        assertThat(recovered.getDispatchAttempt()).isZero();
        assertThat(recovered.getPendingAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(recovered.getCompletedAt()).isNull();
        assertThat(recovered.getProcessingStartedAt()).isNull();
        assertThat(recovered.getLastDispatchedAt()).isNull();
        assertThat(recovered.getFailedAt()).isNull();
        assertThat(recovered.getFailureReason()).isNull();
        assertThat(repository.reopenCompletedMissingResult("job", 0, NOW)).isZero();
        recovered.startProcessing(NOW);
        repository.save(recovered);
        assertThat(repository.failClaimedAttempt("job", 1, 0, NOW, "stale")).isZero();
        assertThat(repository.failClaimedAttempt("job", 1, 1, NOW, "current")).isEqualTo(1);
        var completed = repository.findById("job").orElseThrow();
        completed.complete(NOW);
        repository.save(completed);
        assertThat(repository.reopenCompletedMissingResult("job", 0, NOW)).isZero();
        assertThat(repository.reopenCompletedMissingResult("job", 1, NOW)).isEqualTo(1);
        assertThat(repository.findById("job").orElseThrow().effectiveRecoveryCycle()).isEqualTo(2);
        assertThat(repository.reopenCompletedMissingResult("missing", 0, NOW)).isZero();
    }

    @Test void missingAndExplicitNullLegacyCycleRecoverFromZero() {
        for (String id : new String[] {"missing-cycle", "null-cycle"}) {
            var document = new Document("_id", id).append("status", "COMPLETED").append("version", 0L);
            if (id.equals("null-cycle")) document.append("recoveryCycle", null);
            mongo.getCollection("question_grading_jobs").insertOne(document);
            assertThat(repository.reopenCompletedMissingResult(id, 0, NOW)).isEqualTo(1);
            assertThat(repository.findById(id).orElseThrow().effectiveRecoveryCycle()).isEqualTo(1);
        }
    }

    @Test void concurrentReopenHasOnlyOneWinner() throws Exception {
        repository.insert(QuestionGradingJob.completed("job", "exam", 1, 0, "key", NOW));
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Long> recover = () -> {
                start.await();
                return repository.reopenCompletedMissingResult("job", 0, NOW);
            };
            var first = workers.submit(recover);
            var second = workers.submit(recover);
            start.countDown();
            assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS)
                    + second.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(1);
        }
        var job = repository.findById("job").orElseThrow();
        assertThat(job.effectiveRecoveryCycle()).isEqualTo(1);
        assertThat(job.getVersion()).isEqualTo(1L);
    }

    @Test void updateParticipatesInCallerTransactionAndRollsBack() {
        repository.insert(QuestionGradingJob.completed("job", "exam", 1, 0, "key", NOW));
        var before = mongo.findOne(Query.query(Criteria.where("_id").is("job")), Document.class, "question_grading_jobs");
        var tx = new TransactionTemplate(new MongoTransactionManager(mongo.getMongoDatabaseFactory()));
        assertThatThrownBy(() -> tx.execute(status -> {
            assertThat(repository.reopenCompletedMissingResult("job", 0, NOW)).isEqualTo(1);
            throw new IllegalStateException("rollback fixture");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(mongo.findOne(Query.query(Criteria.where("_id").is("job")), Document.class, "question_grading_jobs"))
                .isEqualTo(before);
    }
}
