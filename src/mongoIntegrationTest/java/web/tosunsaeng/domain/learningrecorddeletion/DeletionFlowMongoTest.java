package web.tosunsaeng.domain.learningrecorddeletion;

import com.mongodb.client.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.index.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.learningrecorddeletion.application.*;
import web.tosunsaeng.domain.learningrecorddeletion.domain.*;
import web.tosunsaeng.domain.learningrecorddeletion.infrastructure.*;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardService;
import web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class DeletionFlowMongoTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");
    static final String OWNER = "00000000-0000-0000-0000-000000000001";
    static final String KEY = "139f345b-9be8-43a3-a63d-9108df972741";
    MongoClient client; MongoTemplate mongo; MutableClock clock; DeletionCommandService commands;
    DeletionWorker worker; DeletionAccess access; DeletionStorage storage; DeletionTransactions tx;
    @BeforeEach void setup() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        mongo = new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, "deletion-flow"));
        mongo.getDb().drop();
        clock = new MutableClock();
        for (String c : List.of("exam_sessions", "exam_creation_operations", "exam_results", "exam_summaries",
                "question_grading_jobs", "summary_grading_jobs", "azure_results", "speechace_results", "exam_submission_receipts", "challenge_10s_attempts",
                "challenge_10s_grading_jobs", "challenge_10s_submit_receipts", "challenge_10s_callback_receipts",
                "user_ownership_guards", "withdrawn_user_access_denies", "learning_record_deletion_operations",
                "learning_record_deletion_commands", "learning_record_deletion_targets", "learning_record_billing_continuations")) mongo.createCollection(c);
        mongo.indexOps(DeletionOperation.class).ensureIndex(new Index().on("userId", Sort.Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where("activeGuard").is(true))));
        mongo.insert(UserOwnershipGuard.active(OWNER, clock.instant()));
        var guards = new UserOwnershipGuardService(mongo, new MongoRepositoryFactory(mongo).getRepository(UserOwnershipGuardRepository.class));
        tx = new DeletionTransactions(mongo.getMongoDatabaseFactory());
        commands = new DeletionCommandService(mongo, tx, guards, clock);
        access = new DeletionAccess(mongo);
        storage = mock(DeletionStorage.class);
        when(storage.sweep(any())).thenReturn(true); when(storage.clearCache(any())).thenReturn(true);
        worker = new DeletionWorker(mongo, tx, guards, new DeletedExamContinuationStore(mongo, guards, clock), storage, clock);
    }
    @AfterEach void close() { client.close(); }

    @Test void commandReplayAndSecondKeyDoNotExpandInventory() {
        var first = commands.request(OWNER, KEY);
        assertThat(commands.request(OWNER, KEY).getDeletionId()).isEqualTo(first.getDeletionId());
        assertThatThrownBy(() -> commands.request(OWNER, UUID.randomUUID().toString()))
                .isInstanceOf(DeletionFailure.class).extracting("status").isEqualTo(409);
        assertThat(mongo.count(new Query(), DeletionCommand.class)).isEqualTo(1);
        assertThatThrownBy(() -> access.requirePublicWrite(OWNER)).isInstanceOf(DeletionFailure.class);
    }

    @Test void lostCommitAcknowledgementRecoversSameCommandFromFreshMajorityEvidence() {
        DeletionTransactions uncertain = mock(DeletionTransactions.class);
        when(uncertain.run(any())).thenAnswer(call -> {
            tx.run(call.getArgument(0));
            com.mongodb.MongoException unknown = new com.mongodb.MongoException(91, "fixture lost acknowledgement");
            unknown.addLabel(com.mongodb.MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
            throw unknown;
        });
        var guards = new UserOwnershipGuardService(mongo, new MongoRepositoryFactory(mongo).getRepository(UserOwnershipGuardRepository.class));
        var service = new DeletionCommandService(mongo, uncertain, guards, clock);
        var operation = service.request(OWNER, KEY);
        assertThat(operation.getDeletionId()).isEqualTo(commands.request(OWNER, KEY).getDeletionId());
        assertThat(mongo.count(new Query(), DeletionOperation.class)).isEqualTo(1);
    }

    @Test void completeDeletesOnlySealedOldTargetsAndKeepsAnonymousAggregateAndNewLearning() {
        mongo.insert(ExamSession.builder().examId("old").userId(OWNER).build());
        mongo.insert(new Document("_id", "old-result").append("examId", "old").append("userId", OWNER), "exam_results");
        mongo.insert(new Document("_id", "old:1").append("examId", "old").append("questionNumber", 1), "exam_submission_receipts");
        mongo.insert(new Document("_id", "day-count").append("count", 120L), "learning_activity_daily_aggregates");
        commands.request(OWNER, KEY);
        assertThat(access.hidden(DeletionTarget.Type.EXAM, "old", OWNER)).isTrue();
        progressToCheckpoint();
        assertThatCode(() -> access.requirePublicWrite(OWNER)).doesNotThrowAnyException();
        mongo.insert(ExamSession.builder().examId("new").userId(OWNER).build());
        mongo.insert(new Document("_id", "new-result").append("examId", "new").append("userId", OWNER), "exam_results");
        mongo.insert(new Document("_id", "new:1").append("examId", "new").append("questionNumber", 1), "exam_submission_receipts");
        clock.now = clock.now.plusSeconds(360);
        for (int i = 0; i < 80 && commands.latest(OWNER).isActiveGuard(); i++) step();
        var completed = commands.latest(OWNER);
        assertThat(completed.getStatus()).isEqualTo(DeletionOperation.Status.COMPLETED);
        assertThat(mongo.findById("old", ExamSession.class)).isNull();
        assertThat(mongo.findById("old-result", Document.class, "exam_results")).isNull();
        assertThat(mongo.findById("old:1", Document.class, "exam_submission_receipts")).isNull();
        assertThat(mongo.findById("new:1", Document.class, "exam_submission_receipts")).isNotNull();
        assertThat(mongo.findById("new", ExamSession.class)).isNotNull();
        assertThat(mongo.findById("new-result", Document.class, "exam_results")).isNotNull();
        assertThat(mongo.findById("day-count", Document.class, "learning_activity_daily_aggregates").getLong("count")).isEqualTo(120);
        assertThat(access.sealed(DeletionTarget.Type.EXAM, "old")).isTrue();
        assertThat(access.hidden(DeletionTarget.Type.EXAM, "new", OWNER)).isFalse();
        worker.tick();
        assertThat(commands.latest(OWNER).getExpiresAt()).isNull();
        worker.tick();
        assertThat(commands.latest(OWNER).getExpiresAt()).isEqualTo(completed.getCompletedAt().plus(Duration.ofDays(30)));
        assertThat(commands.request(OWNER, KEY).getStatus()).isEqualTo(DeletionOperation.Status.COMPLETED);
    }

    @Test void orphanNeverGetsAFalseSafeCheckpoint() {
        mongo.insert(new Document("_id", "orphan").append("userId", OWNER).append("examId", "missing"), "exam_results");
        commands.request(OWNER, KEY);
        for (int i = 0; i < 12; i++) step();
        assertThat(commands.latest(OWNER).getStatus()).isEqualTo(DeletionOperation.Status.NEEDS_REVIEW);
        assertThat(commands.latest(OWNER).isWriteBlocked()).isTrue();
        assertThat(commands.latest(OWNER).getExpiresAt()).isNull();
        verifyNoInteractions(storage);
    }

    @Test void storageFailureAfterCheckpointIsNonblockingAndNeverCompleted() {
        mongo.insert(ExamSession.builder().examId("old").userId(OWNER).build());
        commands.request(OWNER, KEY); progressToCheckpoint();
        when(storage.sweep(any())).thenThrow(new IllegalStateException("fixture storage unavailable"));
        clock.now = clock.now.plus(Duration.ofDays(1)); step(); step();
        var delayed = commands.latest(OWNER);
        assertThat(delayed.getStatus()).isEqualTo(DeletionOperation.Status.CLEANUP_DELAYED);
        assertThat(delayed.isWriteBlocked()).isFalse();
        assertThat(delayed.getCompletedAt()).isNull();
    }

    @Test void unexpectedResidualInFinalVerificationRequiresReview() {
        mongo.insert(ExamSession.builder().examId("old").userId(OWNER).build());
        commands.request(OWNER, KEY); progressToCheckpoint(); clock.now = clock.now.plusSeconds(360);
        for (int i = 0; i < 50 && commands.latest(OWNER).getStage() != DeletionOperation.Stage.VERIFYING; i++) step();
        mongo.insert(new Document("_id", "unexpected").append("examId", "old"), "azure_results");
        step();
        assertThat(commands.latest(OWNER).getStatus()).isEqualTo(DeletionOperation.Status.NEEDS_REVIEW);
        assertThat(commands.latest(OWNER).isWriteBlocked()).isTrue();
    }

    void progressToCheckpoint() {
        for (int i = 0; i < 40 && commands.latest(OWNER).isWriteBlocked(); i++) step();
        assertThat(commands.latest(OWNER).getSafeToStartLearningAt()).isNotNull();
    }
    void step() { clock.now = clock.now.plusSeconds(10); worker.tick(); }
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-03T01:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
