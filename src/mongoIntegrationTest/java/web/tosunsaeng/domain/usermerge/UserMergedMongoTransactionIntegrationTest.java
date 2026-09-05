package web.tosunsaeng.domain.usermerge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClients;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.repository.AzureResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamCreationOperationRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamSummaryRepository;
import web.tosunsaeng.domain.exams.domain.repository.SpeechAceResultRepository;
import web.tosunsaeng.domain.exams.application.BillingExamCreationSaga;
import web.tosunsaeng.domain.exams.application.ExamGradingService;
import web.tosunsaeng.domain.exams.application.ExamServiceImpl;
import web.tosunsaeng.domain.exams.application.ExamSessionManager;
import web.tosunsaeng.domain.exams.application.MockExamCatalogService;
import web.tosunsaeng.domain.exams.application.ModelAnswerCatalogService;
import web.tosunsaeng.domain.exams.billing.BillingSagaProperties;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.usermerge.application.NormalizedUserMergedEvent;
import web.tosunsaeng.domain.usermerge.application.UserMergedConsumerService;
import web.tosunsaeng.domain.usermerge.application.UserMergedConsumeResult;
import web.tosunsaeng.domain.usermerge.application.UserMergedEventException;
import web.tosunsaeng.domain.usermerge.application.UserMergedEventMetrics;
import web.tosunsaeng.domain.usermerge.application.UserMergedTransactionService;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardService;
import web.tosunsaeng.domain.usermerge.api.UserMergedEventRequest;
import web.tosunsaeng.domain.usermerge.config.UserMergedProperties;
import web.tosunsaeng.domain.usermerge.domain.UserMergedInboxEvent;
import web.tosunsaeng.domain.usermerge.domain.OwnershipGuardState;
import web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard;
import web.tosunsaeng.domain.usermerge.repository.UserMergedInboxRepository;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;
import web.tosunsaeng.domain.withdrawal.domain.WithdrawnUserAccessDeny;
import web.tosunsaeng.domain.withdrawal.repository.WithdrawnUserAccessDenyRepository;
import web.tosunsaeng.global.auth.CurrentUserProvider;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@Testcontainers
class UserMergedMongoTransactionIntegrationTest {

    private static final String SOURCE = "73a18ed4-1d56-4c4f-afd6-b39175b82a86";
    private static final String TARGET = "45c05c3f-ae7f-4ca7-af88-3ab8aa8f428e";
    private static final Instant NOW = Instant.parse("2026-08-20T02:00:01Z");

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");

    private static MongoTemplate mongoTemplate;
    private static UserMergedTransactionService service;
    private static UserMergedInboxRepository inboxRepository;
    private static WithdrawnUserAccessDenyRepository withdrawalRepository;
    private static ExamCreationOperationRepository operationRepository;
    private static ExamSessionRepository sessionRepository;
    private static ExamResultRepository resultRepository;
    private static ExamSummaryRepository summaryRepository;
    private static SpeechAceResultRepository speechAceRepository;
    private static AzureResultRepository azureRepository;
    private static UserOwnershipGuardService guardService;
    private static UserOwnedTransactionExecutor transactionExecutor;
    private static TransactionOperations transactions;
    private static Clock clock;

    @BeforeAll
    static void setUp() {
        var client = MongoClients.create(MONGO.getReplicaSetUrl());
        var factory = new SimpleMongoClientDatabaseFactory(client, "learning-core-user-merged-it");
        mongoTemplate = new MongoTemplate(factory);
        var repositories = new MongoRepositoryFactory(mongoTemplate);
        UserOwnershipGuardRepository guardRepository = repositories.getRepository(
                UserOwnershipGuardRepository.class
        );
        inboxRepository = repositories.getRepository(
                UserMergedInboxRepository.class
        );
        withdrawalRepository = repositories.getRepository(
                WithdrawnUserAccessDenyRepository.class
        );
        operationRepository = repositories.getRepository(
                ExamCreationOperationRepository.class
        );
        sessionRepository = repositories.getRepository(ExamSessionRepository.class);
        resultRepository = repositories.getRepository(ExamResultRepository.class);
        summaryRepository = repositories.getRepository(ExamSummaryRepository.class);
        speechAceRepository = repositories.getRepository(SpeechAceResultRepository.class);
        azureRepository = repositories.getRepository(AzureResultRepository.class);
        transactions = new TransactionTemplate(
                new MongoTransactionManager(factory)
        );
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        StaticListableBeanFactory transactionBeans = new StaticListableBeanFactory();
        transactionBeans.addBean("transactions", transactions);
        UserMergedProperties properties = new UserMergedProperties();
        properties.setWriterEnabled(true);
        guardService = new UserOwnershipGuardService(
                mongoTemplate,
                guardRepository
        );
        transactionExecutor = new UserOwnedTransactionExecutor(
                properties,
                guardService,
                transactionBeans.getBeanProvider(TransactionOperations.class),
                clock
        );
        service = newService(inboxRepository, transactionExecutor);
    }

    @AfterEach
    void clean() {
        mongoTemplate.getDb().drop();
    }

    @Test
    void ownerMigrationSourceDenyAndInboxCommitAtomically() {
        mongoTemplate.insert(ExamSession.builder()
                .examId("exam-source")
                .userId(SOURCE)
                .active(true)
                .status(ExamSessionStatus.IN_PROGRESS)
                .build());
        mongoTemplate.insert(ExamResult.builder()
                .id("result-source")
                .examId("exam-source")
                .userId(SOURCE)
                .build());
        mongoTemplate.insert(ExamSummary.builder()
                .id("summary-source")
                .examId("exam-source")
                .userId(SOURCE)
                .build());

        assertThat(service.consume(event("9a88bc80-d73a-4a3d-8f68-492641d27208")))
                .isEqualTo(UserMergedConsumeResult.PROCESSED);

        assertThat(mongoTemplate.findById("exam-source", ExamSession.class).getUserId())
                .isEqualTo(TARGET);
        assertThat(mongoTemplate.findById("result-source", ExamResult.class).getUserId())
                .isEqualTo(TARGET);
        assertThat(mongoTemplate.findById("summary-source", ExamSummary.class).getUserId())
                .isEqualTo(TARGET);
        assertThat(mongoTemplate.findById(SOURCE,
                web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard.class).getState())
                .isEqualTo(OwnershipGuardState.MERGED);
        assertThat(mongoTemplate.getCollection("user_merged_inbox_events").countDocuments())
                .isEqualTo(1L);

        assertThat(service.consume(event("9a88bc80-d73a-4a3d-8f68-492641d27208")))
                .isEqualTo(UserMergedConsumeResult.DUPLICATE);
    }

    @Test
    void activeWithdrawalConflictLeavesAllOwnershipUnchanged() {
        mongoTemplate.insert(ExamSession.builder()
                .examId("exam-source")
                .userId(SOURCE)
                .active(true)
                .status(ExamSessionStatus.IN_PROGRESS)
                .build());
        mongoTemplate.insert(new WithdrawnUserAccessDeny(
                SOURCE,
                "aa88bc80-d73a-4a3d-8f68-492641d27208",
                NOW.minusSeconds(10),
                NOW.plusSeconds(60),
                NOW.plusSeconds(60),
                NOW.minusSeconds(10)
        ));

        assertThatThrownBy(() -> service.consume(
                event("ba88bc80-d73a-4a3d-8f68-492641d27208")))
                .isInstanceOfSatisfying(UserMergedEventException.class, failure ->
                        assertThat(failure.getReason())
                                .isEqualTo(UserMergedEventException.Reason.LIFECYCLE_CONFLICT));

        assertThat(mongoTemplate.findById("exam-source", ExamSession.class).getUserId())
                .isEqualTo(SOURCE);
        assertThat(mongoTemplate.getCollection("user_ownership_guards").countDocuments())
                .isZero();
        assertThat(mongoTemplate.getCollection("user_merged_inbox_events").countDocuments())
                .isZero();
    }

    @Test
    void targetActiveSessionWinsAndSourceActiveSessionBecomesAbandonedHistory() {
        mongoTemplate.insert(ExamSession.builder()
                .examId("exam-source")
                .userId(SOURCE)
                .active(true)
                .status(ExamSessionStatus.IN_PROGRESS)
                .build());
        mongoTemplate.insert(ExamSession.builder()
                .examId("exam-target")
                .userId(TARGET)
                .active(true)
                .status(ExamSessionStatus.IN_PROGRESS)
                .build());

        assertThat(service.consume(event("ca88bc80-d73a-4a3d-8f68-492641d27208")))
                .isEqualTo(UserMergedConsumeResult.PROCESSED);

        ExamSession migratedSource = mongoTemplate.findById("exam-source", ExamSession.class);
        ExamSession existingTarget = mongoTemplate.findById("exam-target", ExamSession.class);
        assertThat(migratedSource.getUserId()).isEqualTo(TARGET);
        assertThat(migratedSource.getStatus()).isEqualTo(ExamSessionStatus.ABANDONED);
        assertThat(migratedSource.getActive()).isFalse();
        assertThat(existingTarget.getUserId()).isEqualTo(TARGET);
        assertThat(existingTarget.getStatus()).isEqualTo(ExamSessionStatus.IN_PROGRESS);
        assertThat(existingTarget.getActive()).isTrue();
    }

    @Test
    void mergedTargetConflictsAndRollsBackSourceTouchAndMigration() {
        mongoTemplate.insert(ExamSession.builder()
                .examId("exam-source")
                .userId(SOURCE)
                .active(true)
                .status(ExamSessionStatus.IN_PROGRESS)
                .build());
        mongoTemplate.insert(new UserOwnershipGuard(
                TARGET,
                OwnershipGuardState.MERGED,
                3L,
                "00000000-0000-4000-8000-000000000099",
                NOW.minusSeconds(10),
                "da88bc80-d73a-4a3d-8f68-492641d27208",
                NOW.minusSeconds(20),
                NOW.minusSeconds(10)
        ));

        assertThatThrownBy(() -> service.consume(
                event("ea88bc80-d73a-4a3d-8f68-492641d27208")))
                .isInstanceOfSatisfying(UserMergedEventException.class, failure ->
                        assertThat(failure.getReason())
                                .isEqualTo(UserMergedEventException.Reason.LIFECYCLE_CONFLICT));

        assertThat(mongoTemplate.findById("exam-source", ExamSession.class).getUserId())
                .isEqualTo(SOURCE);
        assertThat(mongoTemplate.findById(SOURCE, UserOwnershipGuard.class)).isNull();
        assertThat(mongoTemplate.getCollection("user_merged_inbox_events").countDocuments())
                .isZero();
    }

    @Test
    void failureAtFinalInboxInsertRollsBackEveryOwnershipMutation() {
        mongoTemplate.insert(ExamSession.builder()
                .examId("exam-source")
                .userId(SOURCE)
                .active(true)
                .status(ExamSessionStatus.IN_PROGRESS)
                .build());
        mongoTemplate.insert(ExamResult.builder()
                .id("result-source")
                .examId("exam-source")
                .userId(SOURCE)
                .build());
        mongoTemplate.insert(ExamSummary.builder()
                .id("summary-source")
                .examId("exam-source")
                .userId(SOURCE)
                .build());
        UserMergedInboxRepository failingInbox = mock(
                UserMergedInboxRepository.class,
                withSettings().defaultAnswer(delegatesTo(inboxRepository))
        );
        doThrow(new DataAccessResourceFailureException("inbox unavailable"))
                .when(failingInbox).insert(any(UserMergedInboxEvent.class));
        UserMergedTransactionService failingService = newService(
                failingInbox,
                transactionExecutor
        );

        assertThatThrownBy(() -> failingService.consume(
                event("fa88bc80-d73a-4a3d-8f68-492641d27208")))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(mongoTemplate.findById("exam-source", ExamSession.class).getUserId())
                .isEqualTo(SOURCE);
        assertThat(mongoTemplate.findById("result-source", ExamResult.class).getUserId())
                .isEqualTo(SOURCE);
        assertThat(mongoTemplate.findById("summary-source", ExamSummary.class).getUserId())
                .isEqualTo(SOURCE);
        assertThat(mongoTemplate.findById(SOURCE, UserOwnershipGuard.class)).isNull();
        assertThat(mongoTemplate.findById(TARGET, UserOwnershipGuard.class)).isNull();
        assertThat(mongoTemplate.getCollection("user_merged_inbox_events").countDocuments())
                .isZero();
    }

    @Test
    void nonTerminalCreationOperationBlocksWithoutMutationAndTerminalRetrySucceeds() {
        ExamCreationOperation operation = ExamCreationOperation.prepared(
                SOURCE,
                "ab88bc80-d73a-4a3d-8f68-492641d27208",
                "exam-pending",
                "mock_exam_001",
                1,
                NOW
        );
        operationRepository.insert(operation);
        NormalizedUserMergedEvent mergeEvent =
                event("fb88bc80-d73a-4a3d-8f68-492641d27208");

        assertThatThrownBy(() -> service.consume(mergeEvent))
                .isInstanceOfSatisfying(UserMergedEventException.class, failure ->
                        assertThat(failure.getReason())
                                .isEqualTo(UserMergedEventException.Reason.RETRYABLE_PRECONDITION));
        assertThat(mongoTemplate.getCollection("user_ownership_guards").countDocuments())
                .isZero();
        assertThat(mongoTemplate.getCollection("user_merged_inbox_events").countDocuments())
                .isZero();

        operation.markFailedTerminal("TEST_TERMINAL", NOW, NOW.plusSeconds(60));
        operationRepository.save(operation);

        assertThat(service.consume(mergeEvent)).isEqualTo(UserMergedConsumeResult.PROCESSED);
        assertThat(mongoTemplate.getCollection("user_merged_inbox_events").countDocuments())
                .isEqualTo(1L);
    }

    @Test
    void concurrentSameEventConvergesToOneInboxAndNoPartialOwnerState() throws Exception {
        mongoTemplate.insert(ExamSession.builder()
                .examId("exam-source")
                .userId(SOURCE)
                .active(true)
                .status(ExamSessionStatus.IN_PROGRESS)
                .build());
        NormalizedUserMergedEvent mergeEvent =
                event("fc88bc80-d73a-4a3d-8f68-492641d27208");
        CountDownLatch start = new CountDownLatch(1);
        Set<UserMergedConsumeResult> outcomes = new HashSet<>();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                start.await();
                return service.consume(mergeEvent);
            });
            var second = workers.submit(() -> {
                start.await();
                return service.consume(mergeEvent);
            });
            start.countDown();
            outcomes.add(first.get(10, TimeUnit.SECONDS));
            outcomes.add(second.get(10, TimeUnit.SECONDS));
        }

        assertThat(outcomes).contains(UserMergedConsumeResult.PROCESSED);
        assertThat(mongoTemplate.getCollection("user_merged_inbox_events").countDocuments())
                .isEqualTo(1L);
        assertThat(mongoTemplate.findById("exam-source", ExamSession.class).getUserId())
                .isEqualTo(TARGET);
        assertThat(mongoTemplate.findById(SOURCE, UserOwnershipGuard.class).getState())
                .isEqualTo(OwnershipGuardState.MERGED);
    }

    @Test
    void committedResponseLossConvergesThroughInboxWithoutReplayingMutation() {
        mongoTemplate.insert(ExamSession.builder()
                .examId("exam-source")
                .userId(SOURCE)
                .active(true)
                .status(ExamSessionStatus.IN_PROGRESS)
                .build());
        AtomicBoolean firstCommit = new AtomicBoolean(true);
        TransactionOperations responseLossTransactions = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                T result = transactions.execute(action);
                if (firstCommit.compareAndSet(true, false)) {
                    com.mongodb.MongoException mongoFailure =
                            new com.mongodb.MongoException("unknown commit result");
                    mongoFailure.addLabel(
                            com.mongodb.MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL
                    );
                    throw new org.springframework.transaction.TransactionSystemException(
                            "commit response lost",
                            mongoFailure
                    );
                }
                return result;
            }
        };
        UserMergedTransactionService responseLossService = newService(
                inboxRepository,
                executor(responseLossTransactions)
        );
        UserMergedConsumerService consumer = new UserMergedConsumerService(
                responseLossService,
                inboxRepository,
                clock,
                new UserMergedEventMetrics(new SimpleMeterRegistry())
        );

        UserMergedConsumeResult result = consumer.consume(new UserMergedEventRequest(
                "fd88bc80-d73a-4a3d-8f68-492641d27208",
                1,
                SOURCE,
                TARGET,
                NOW.minusSeconds(1).toString()
        ));

        assertThat(result).isEqualTo(UserMergedConsumeResult.DUPLICATE);
        assertThat(mongoTemplate.getCollection("user_merged_inbox_events").countDocuments())
                .isEqualTo(1L);
        assertThat(mongoTemplate.findById("exam-source", ExamSession.class).getUserId())
                .isEqualTo(TARGET);
    }

    @Test
    void mergedSourceGuardRejectsSubsequentUserOwnedWriter() {
        assertThat(service.consume(event("fe88bc80-d73a-4a3d-8f68-492641d27208")))
                .isEqualTo(UserMergedConsumeResult.PROCESSED);

        assertThatThrownBy(() -> transactionExecutor.executeWithoutResult(SOURCE, () ->
                mongoTemplate.insert(ExamResult.builder()
                        .id("late-source-result")
                        .examId("exam-source")
                        .userId(SOURCE)
                        .build())))
                .isInstanceOf(
                        web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardException.class
                );
        assertThat(mongoTemplate.findById("late-source-result", ExamResult.class)).isNull();
    }

    @Test
    void callbacksCommittedBeforeMergeConvergeToTargetOwnership() throws Exception {
        insertSourceSession();
        ExamServiceImpl callbackService = callbackService();

        invokeAllCallbackTypes(callbackService);
        assertThat(service.consume(event("af88bc80-d73a-4a3d-8f68-492641d27208")))
                .isEqualTo(UserMergedConsumeResult.PROCESSED);

        assertThat(resultRepository.findByExamId("exam-source"))
                .isNotEmpty()
                .allMatch(result -> TARGET.equals(result.getUserId()));
        assertThat(summaryRepository.findAllByExamId("exam-source"))
                .isNotEmpty()
                .allMatch(summary -> TARGET.equals(summary.getUserId()));
        assertThat(speechAceRepository.count()).isEqualTo(1L);
        assertThat(azureRepository.count()).isEqualTo(1L);
    }

    @Test
    void callbacksStartedAfterMergeResolveAndPersistTheTargetOwner() throws Exception {
        insertSourceSession();
        assertThat(service.consume(event("bf88bc80-d73a-4a3d-8f68-492641d27208")))
                .isEqualTo(UserMergedConsumeResult.PROCESSED);
        ExamServiceImpl callbackService = callbackService();

        invokeAllCallbackTypes(callbackService);

        assertThat(resultRepository.findByExamId("exam-source"))
                .isNotEmpty()
                .allMatch(result -> TARGET.equals(result.getUserId()));
        assertThat(summaryRepository.findAllByExamId("exam-source"))
                .isNotEmpty()
                .allMatch(summary -> TARGET.equals(summary.getUserId()));
        assertThat(speechAceRepository.count()).isEqualTo(1L);
        assertThat(azureRepository.count()).isEqualTo(1L);
    }

    private static void insertSourceSession() {
        mongoTemplate.insert(ExamSession.builder()
                .examId("exam-source")
                .userId(SOURCE)
                .mockExamId("mock_exam_001")
                .active(true)
                .status(ExamSessionStatus.IN_PROGRESS)
                .build());
    }

    private static ExamServiceImpl callbackService() {
        ExamGradingService gradingService = mock(ExamGradingService.class);
        when(gradingService.isCurrentSummaryGeneration(anyString(), anyInt())).thenReturn(true);
        when(gradingService.claimSummaryCompletion(anyString(), anyInt())).thenReturn(true);
        when(gradingService.completeSummary(anyString(), anyInt())).thenReturn(true);
        ExamServiceImpl callbackService = new ExamServiceImpl(
                mock(RedisTemplate.class),
                mock(S3Presigner.class),
                gradingService,
                mock(ExamSessionManager.class),
                mock(BillingExamCreationSaga.class),
                new BillingSagaProperties(),
                resultRepository,
                summaryRepository,
                sessionRepository,
                mock(MockExamCatalogService.class),
                mock(ModelAnswerCatalogService.class),
                speechAceRepository,
                azureRepository,
                mock(CurrentUserProvider.class)
        );
        ReflectionTestUtils.setField(
                callbackService,
                "userOwnedTransactionExecutor",
                transactionExecutor
        );
        return callbackService;
    }

    private static void invokeAllCallbackTypes(ExamServiceImpl callbackService) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        callbackService.updateExamResult(objectMapper.readValue("""
                {
                  "user_id": "exam-source",
                  "question_number": 4,
                  "retry_count": 0,
                  "score": 8.0
                }
                """, ExamRequestDTO.AiResultReq.class));
        callbackService.updateExamResult(objectMapper.readValue("""
                {
                  "user_id": "exam-source",
                  "mock_exam_id": "mock_exam_001",
                  "generation_attempt": 1,
                  "suggested_total_score": 170,
                  "part_feedback": {"part1": "feedback"},
                  "question_number": 0
                }
                """, ExamRequestDTO.AiResultReq.class));
        callbackService.saveSpeechAceResult(objectMapper.readValue("""
                {
                  "user_id": "exam-source",
                  "question_number": 5,
                  "retry_count": 0,
                  "speechace_result": {"score": 90}
                }
                """, ExamRequestDTO.SpeechAceReq.class));
        callbackService.processAzureCallback(Map.of(
                "metadata", Map.of(
                        "user_id", "exam-source",
                        "question_number", 6,
                        "retry_count", 0
                ),
                "result", Map.of("score", 91)
        ));
    }

    private static UserMergedTransactionService newService(
            UserMergedInboxRepository selectedInboxRepository,
            UserOwnedTransactionExecutor selectedExecutor
    ) {
        return new UserMergedTransactionService(
                selectedInboxRepository,
                withdrawalRepository,
                operationRepository,
                guardService,
                selectedExecutor,
                mongoTemplate
        );
    }

    private static UserOwnedTransactionExecutor executor(
            TransactionOperations selectedTransactions
    ) {
        StaticListableBeanFactory transactionBeans = new StaticListableBeanFactory();
        transactionBeans.addBean("transactions", selectedTransactions);
        UserMergedProperties properties = new UserMergedProperties();
        properties.setWriterEnabled(true);
        return new UserOwnedTransactionExecutor(
                properties,
                guardService,
                transactionBeans.getBeanProvider(TransactionOperations.class),
                clock
        );
    }

    private static NormalizedUserMergedEvent event(String eventId) {
        return new NormalizedUserMergedEvent(
                eventId,
                1,
                "digest",
                SOURCE,
                TARGET,
                NOW.minusSeconds(1),
                NOW
        );
    }
}
