package web.tosunsaeng.domain.usermerge;

import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamCreationOperationRepository;
import web.tosunsaeng.domain.usermerge.application.NormalizedUserMergedEvent;
import web.tosunsaeng.domain.usermerge.application.UserMergedConsumeResult;
import web.tosunsaeng.domain.usermerge.application.UserMergedEventException;
import web.tosunsaeng.domain.usermerge.application.UserMergedTransactionService;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardService;
import web.tosunsaeng.domain.usermerge.config.UserMergedProperties;
import web.tosunsaeng.domain.usermerge.domain.OwnershipGuardState;
import web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard;
import web.tosunsaeng.domain.usermerge.repository.UserMergedInboxRepository;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;
import web.tosunsaeng.domain.withdrawal.domain.WithdrawnUserAccessDeny;
import web.tosunsaeng.domain.withdrawal.repository.WithdrawnUserAccessDenyRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class UserMergedMongoTransactionIntegrationTest {

    private static final String SOURCE = "73a18ed4-1d56-4c4f-afd6-b39175b82a86";
    private static final String TARGET = "45c05c3f-ae7f-4ca7-af88-3ab8aa8f428e";
    private static final Instant NOW = Instant.parse("2026-08-20T02:00:01Z");

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");

    private static MongoTemplate mongoTemplate;
    private static UserMergedTransactionService service;

    @BeforeAll
    static void setUp() {
        var client = MongoClients.create(MONGO.getReplicaSetUrl());
        var factory = new SimpleMongoClientDatabaseFactory(client, "learning-core-user-merged-it");
        mongoTemplate = new MongoTemplate(factory);
        var repositories = new MongoRepositoryFactory(mongoTemplate);
        UserOwnershipGuardRepository guardRepository = repositories.getRepository(
                UserOwnershipGuardRepository.class
        );
        UserMergedInboxRepository inboxRepository = repositories.getRepository(
                UserMergedInboxRepository.class
        );
        WithdrawnUserAccessDenyRepository withdrawalRepository = repositories.getRepository(
                WithdrawnUserAccessDenyRepository.class
        );
        ExamCreationOperationRepository operationRepository = repositories.getRepository(
                ExamCreationOperationRepository.class
        );
        TransactionOperations transactions = new TransactionTemplate(
                new MongoTransactionManager(factory)
        );
        StaticListableBeanFactory transactionBeans = new StaticListableBeanFactory();
        transactionBeans.addBean("transactions", transactions);
        UserMergedProperties properties = new UserMergedProperties();
        properties.setWriterEnabled(true);
        UserOwnershipGuardService guardService = new UserOwnershipGuardService(
                mongoTemplate,
                guardRepository
        );
        UserOwnedTransactionExecutor executor = new UserOwnedTransactionExecutor(
                properties,
                guardService,
                transactionBeans.getBeanProvider(TransactionOperations.class),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        service = new UserMergedTransactionService(
                inboxRepository,
                withdrawalRepository,
                operationRepository,
                guardService,
                executor,
                mongoTemplate
        );
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
