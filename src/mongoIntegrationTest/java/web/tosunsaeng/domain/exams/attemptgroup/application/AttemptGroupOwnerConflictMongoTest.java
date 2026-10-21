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

    @Test @SuppressWarnings("unchecked")
    void exhaustedSubmissionCoordinationIsRecoveredByNewWorkerFromDurableEvidence() {
        var factory = new SimpleMongoClientDatabaseFactory(client, "isolated-deferred-" + UUID.randomUUID());
        var mongo = new MongoTemplate(factory);
        var repos = new org.springframework.data.mongodb.repository.support.MongoRepositoryFactory(mongo);
        var repository = repos.getRepository(ExamSessionRepository.class);
        var transactions = new TransactionTemplate(new MongoTransactionManager(factory));
        String owner = "00000000-0000-4000-8000-000000000001";
        try {
            var session = ExamSession.builder().examId("ex_deferred").userId(owner)
                    .status(web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus.IN_PROGRESS).active(true)
                    .entitlementState(web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState.CONFIRMED)
                    .attemptGroupId("group").attemptGroupProjectionStatus(
                            web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupProjectionStatus.GRADING)
                    .submissionEvidenceVersion(1).requiredQuestionNumbers(List.of(3)).build();
            var evidence = new web.tosunsaeng.domain.exams.application.ExamSubmissionEvidence(mongo);
            transactions.executeWithoutResult(status -> {
                mongo.insert(web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard.active(owner, Instant.now()));
                mongo.insert(session);
                evidence.accepted("ex_deferred", 3, 0, null, Instant.now());
            });
            var properties = new AttemptGroupEventProperties(true, false, "", "ap-northeast-2",
                    Duration.ofMinutes(30), Duration.ofSeconds(1), 1, Duration.ofSeconds(30),
                    Duration.ofMinutes(15), Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofDays(30), Duration.ofDays(90));
            ObjectProvider<TransactionOperations> provider = mock(ObjectProvider.class);
            var failed = mock(TransactionOperations.class);
            var conflict = new com.mongodb.MongoException(112, "conflict");
            conflict.addLabel(com.mongodb.MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
            doThrow(conflict).when(failed).executeWithoutResult(any());
            when(provider.getIfAvailable()).thenReturn(failed);
            var ownerProperties = new UserMergedProperties(); ownerProperties.setWriterEnabled(true);
            ObjectProvider<TransactionOperations> realProvider = mock(ObjectProvider.class);
            when(realProvider.getIfAvailable()).thenReturn(transactions);
            var guard = new UserOwnershipGuardService(mongo, repos.getRepository(UserOwnershipGuardRepository.class));
            var executor = new UserOwnedTransactionExecutor(ownerProperties, guard, realProvider, Clock.systemUTC());
            var evaluator = mock(AttemptGroupEvidenceEvaluator.class);
            when(evaluator.evaluate(any(), any())).thenReturn(new AttemptGroupEvidenceEvaluator.Evaluation(
                    true, true, web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupCompletionEvidence.complete(), null, false));
            var trace = mock(AttemptGroupTraceContext.class);
            when(trace.captureOrCreate()).thenReturn(new AttemptGroupTraceContext.StoredContext(
                    "0123456789abcdef0123456789abcdef", "0123456789abcdef", "01", false));
            var store = new AttemptGroupOutboxStore(mongo);
            var mapper = new ObjectMapper().findAndRegisterModules();
            var initial = new AttemptGroupStateCoordinator(properties, repository, store, evaluator,
                    new AttemptGroupEventCanonicalizer(mapper), trace, mock(AttemptGroupEventMetrics.class), provider, Clock.systemUTC());
            ReflectionTestUtils.setField(initial, "submissionEvidence", evidence);
            ReflectionTestUtils.setField(initial, "userOwnedTransactionExecutor", executor);
            assertDoesNotThrow(() -> initial.reconcileAfterSubmission("ex_deferred", 3, 0, () -> {}));
            verify(failed, times(3)).executeWithoutResult(any());
            assertNull(repository.findById("ex_deferred").orElseThrow().getTerminalEventId());

            // Fresh coordinator/worker, no in-memory list or post-failure marker from the request.
            var restarted = new AttemptGroupStateCoordinator(properties, repository, store, evaluator,
                    new AttemptGroupEventCanonicalizer(mapper), trace, mock(AttemptGroupEventMetrics.class), realProvider, Clock.systemUTC());
            ReflectionTestUtils.setField(restarted, "userOwnedTransactionExecutor", executor);
            var worker = new AttemptGroupStateReconciler(repository, restarted, properties);
            worker.reconcile(); worker.reconcile(); worker.reconcile();
            assertNotNull(repository.findById("ex_deferred").orElseThrow().getTerminalEventId());
            assertEquals(1, mongo.findAll(web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupEventOutbox.class).size());
            assertTrue(repository.findAttemptGroupReconciliationCandidatesAfter("",
                    org.springframework.data.domain.PageRequest.of(0, 1)).isEmpty());
        } finally { mongo.getDb().drop(); }
    }

    @Test void keysetQueryPagesPastStillEligibleCandidatesAndRestartsAtBeginning() {
        var factory = new SimpleMongoClientDatabaseFactory(client, "isolated-pages-" + UUID.randomUUID());
        var mongo = new MongoTemplate(factory);
        try {
            var repository = new org.springframework.data.mongodb.repository.support.MongoRepositoryFactory(mongo)
                    .getRepository(ExamSessionRepository.class);
            for (String id : List.of("ex_a", "ex_b", "ex_c")) {
                mongo.insert(ExamSession.builder().examId(id).userId("owner").attemptGroupId("group")
                        .entitlementState(web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState.CONFIRMED)
                        .attemptGroupProjectionStatus(web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupProjectionStatus.GRADING).build());
            }
            var page = org.springframework.data.domain.PageRequest.of(0, 2);
            assertEquals(List.of("ex_a", "ex_b"), repository.findAttemptGroupReconciliationCandidatesAfter("", page)
                    .stream().map(ExamSession::getExamId).toList());
            assertEquals(List.of("ex_c"), repository.findAttemptGroupReconciliationCandidatesAfter("ex_b", page)
                    .stream().map(ExamSession::getExamId).toList());
            assertTrue(repository.findAttemptGroupReconciliationCandidatesAfter("ex_c", page).isEmpty());
            assertEquals(2, repository.findAttemptGroupReconciliationCandidatesAfter("", page).size());
        } finally { mongo.getDb().drop(); }
    }

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
