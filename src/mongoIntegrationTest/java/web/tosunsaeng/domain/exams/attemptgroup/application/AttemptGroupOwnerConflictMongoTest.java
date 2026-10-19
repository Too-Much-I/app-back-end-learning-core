package web.tosunsaeng.domain.exams.attemptgroup.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.*;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.usermerge.application.*;
import web.tosunsaeng.domain.usermerge.config.UserMergedProperties;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class AttemptGroupOwnerConflictMongoTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");
    static MongoClient client;
    @BeforeAll static void connect() { client = MongoClients.create(MONGO.getReplicaSetUrl()); }
    @AfterAll static void close() { client.close(); }

    @RepeatedTest(5)
    @SuppressWarnings("unchecked")
    void ownerWriteConflictRestartsWithFreshSnapshotAndOneCommittedTouch() {
        var factory = new SimpleMongoClientDatabaseFactory(client, "isolated-coordinator-" + UUID.randomUUID());
        var mongo = new MongoTemplate(factory);
        String owner = "00000000-0000-4000-8000-000000000001";
        try {
            mongo.insert(new Document("_id", owner).append("state", "ACTIVE").append("revision", 0L), "user_ownership_guards");
            mongo.insert(ExamSession.builder().examId("ex_conflict").userId(owner).build());
            var repository = mock(ExamSessionRepository.class);
            AtomicInteger attempts = new AtomicInteger();
            when(repository.findById("ex_conflict")).thenAnswer(call -> {
                var session = mongo.findById("ex_conflict", ExamSession.class); // Establish transaction snapshot.
                if (attempts.incrementAndGet() == 1) {
                    // A separate raw-driver write does not participate in Spring's bound session.
                    client.getDatabase(factory.getMongoDatabase().getName()).getCollection("user_ownership_guards")
                            .updateOne(new Document("_id", owner), new Document("$inc", new Document("revision", 1L)));
                }
                return Optional.of(session);
            });
            ObjectProvider<TransactionOperations> provider = mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(new TransactionTemplate(new MongoTransactionManager(factory)));
            var properties = new AttemptGroupEventProperties(true, false, "", "ap-northeast-2",
                    Duration.ofMinutes(30), Duration.ofSeconds(1), 20, Duration.ofSeconds(30),
                    Duration.ofMinutes(15), Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofDays(30), Duration.ofDays(90));
            var coordinator = new AttemptGroupStateCoordinator(properties, repository, mock(AttemptGroupOutboxStore.class),
                    mock(AttemptGroupEvidenceEvaluator.class), new AttemptGroupEventCanonicalizer(new ObjectMapper()),
                    mock(AttemptGroupTraceContext.class), mock(AttemptGroupEventMetrics.class), provider, Clock.systemUTC());
            var ownerProperties = new UserMergedProperties(); ownerProperties.setWriterEnabled(true);
            var guard = new UserOwnershipGuardService(mongo, mock(UserOwnershipGuardRepository.class));
            ReflectionTestUtils.setField(coordinator, "userOwnedTransactionExecutor",
                    new UserOwnedTransactionExecutor(ownerProperties, guard, provider, Clock.systemUTC()));
            coordinator.reconcile("ex_conflict");
            assertEquals(2, attempts.get());
            assertEquals(2L, mongo.getCollection("user_ownership_guards").find(new Document("_id", owner)).first().getLong("revision"));
        } finally {
            mongo.getDb().drop();
        }
    }
}
