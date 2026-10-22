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
        mongo.createCollection(web.tosunsaeng.domain.usermerge.application.CallbackWriteFence.COLLECTION);
        org.springframework.test.util.ReflectionTestUtils.setField(guards, "callbackWriteFence",
                new web.tosunsaeng.domain.usermerge.application.CallbackWriteFence(mongo, true));
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
        mongo.insert(web.tosunsaeng.domain.exams.domain.entity.AzureResult.builder().id("old-azure").examId("old").build());
        mongo.insert(new Document("_id", "old:1").append("examId", "old").append("questionNumber", 1), "exam_submission_receipts");
        mongo.insert(new Document("_id", "day-count").append("count", 120L), "learning_activity_daily_aggregates");
        commands.request(OWNER, KEY);
        assertThat(access.hidden(DeletionTarget.Type.EXAM, "old", OWNER)).isTrue();
        progressToCheckpoint();
        assertThatCode(() -> access.requirePublicWrite(OWNER)).doesNotThrowAnyException();
        mongo.insert(ExamSession.builder().examId("new").userId(OWNER).build());
        mongo.insert(new Document("_id", "new-result").append("examId", "new").append("userId", OWNER), "exam_results");
        mongo.insert(web.tosunsaeng.domain.exams.domain.entity.AzureResult.builder().id("new-azure").examId("new").build());
        mongo.insert(new Document("_id", "new:1").append("examId", "new").append("questionNumber", 1), "exam_submission_receipts");
        clock.now = clock.now.plusSeconds(360);
        for (int i = 0; i < 80 && commands.latest(OWNER).isActiveGuard(); i++) step();
        var completed = commands.latest(OWNER);
        assertThat(completed.getStatus()).isEqualTo(DeletionOperation.Status.COMPLETED);
        assertThat(mongo.findById("old", ExamSession.class)).isNull();
        assertThat(mongo.findById("old-result", Document.class, "exam_results")).isNull();
        assertThat(mongo.findById("old-azure", Document.class, "azure_results")).isNull();
        assertThat(mongo.findById("new-azure", Document.class, "azure_results").getString("exam_id")).isEqualTo("new");
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

    @Test void answerLearningDeletionIncludesPartOneAndCannotEraseNewCompletionOrReviveOldResult() {
        var store = new web.tosunsaeng.domain.answerlearning.LearningStore(mongo, null, access); store.indexes();
        var s = new web.tosunsaeng.domain.answerlearning.LearningModels.Submission();
        s.id=UUID.randomUUID().toString(); s.userId=OWNER; s.lessonId="graded"; s.level="IH"; s.contentVersion="v1";
        s.status="completed"; s.completionPending=true; s.result=new Document("transcript","test");
        s.nextRunAt=clock.instant(); s.leaseUntil=Instant.EPOCH;
        mongo.insert(s); var claim=store.claim(s,clock.instant());
        store.complete(OWNER,"reading","common","v1",null,clock.instant());
        String oldCompletion=mongo.findAll(Document.class,"learning_completions").getFirst().getString("_id");
        commands.request(OWNER,KEY); progressToCheckpoint();
        assertThat(store.find(s.id)).isNull();
        store.completeClaim(claim,clock.instant());
        assertThatThrownBy(()->store.complete(OWNER,"graded","IH","v1",s.id,clock.instant())).hasMessage("COMPLETION_NOT_READY");
        assertThatThrownBy(()->store.create(s,60)).hasMessage("LEARNING_NOT_FOUND");
        store.complete(OWNER,"reading","common","v1",null,clock.instant());
        String fresh=mongo.findAll(Document.class,"learning_completions").getFirst().getString("_id");
        assertThat(fresh).isNotEqualTo(oldCompletion);
        clock.now=clock.now.plusSeconds(360);
        for(int i=0;i<80 && commands.latest(OWNER).isActiveGuard();i++) step();
        assertThat(commands.latest(OWNER).getStatus()).isEqualTo(DeletionOperation.Status.COMPLETED);
        assertThat(mongo.findAll(Document.class,"learning_completions")).hasSize(1);
        assertThat(mongo.findById(fresh,Document.class,"learning_completions")).isNotNull();
        verify(storage,atLeastOnce()).sweep(argThat(t->t.getTargetType()==DeletionTarget.Type.LEARNING_SUBMISSION && t.getAggregateId().equals(s.id)));
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
        mongo.insert(web.tosunsaeng.domain.exams.domain.entity.AzureResult.builder().id("unexpected").examId("old").build());
        step();
        assertThat(commands.latest(OWNER).getStatus()).isEqualTo(DeletionOperation.Status.NEEDS_REVIEW);
        assertThat(commands.latest(OWNER).isWriteBlocked()).isTrue();
    }

    @Test void azurePersistenceFenceUsesMappedReferenceAndPreservesDeletionDeny() {
        mongo.insert(ExamSession.builder().examId("azure-parent").userId(OWNER).build());
        var guards = new UserOwnershipGuardService(mongo,
                new MongoRepositoryFactory(mongo).getRepository(UserOwnershipGuardRepository.class));
        var fence = new DeletionPersistenceFence(mongo, access, guards, clock);
        var document = new Document();
        mongo.getConverter().write(web.tosunsaeng.domain.exams.domain.entity.AzureResult.builder()
                .id("azure-child").examId("azure-parent").build(), document);
        assertThat(document.getString("exam_id")).isEqualTo("azure-parent");
        assertThat(document).doesNotContainKey("examId");
        assertThatCode(() -> tx.run(() -> { fence.check("azure_results", document); return null; }))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> tx.run(() -> {
            fence.check("azure_results", new Document("examId", "azure-parent")); return null;
        })).isInstanceOf(IllegalStateException.class);
        commands.request(OWNER, KEY);
        progressToCheckpoint();
        assertThatThrownBy(() -> tx.run(() -> { fence.check("azure_results", document); return null; }))
                .isInstanceOf(DeletionFailure.class);
    }

    void progressToCheckpoint() {
        for (int i = 0; i < 40 && commands.latest(OWNER).isWriteBlocked(); i++) step();
        assertThat(commands.latest(OWNER).getSafeToStartLearningAt()).isNotNull();
    }
    DeletionTarget linkedPrepared() {
        return linkedPrepared(false);
    }
    DeletionTarget linkedPrepared(boolean allAccepted) {
        worker.withBillingEnvironment("test");
        mongo.insert(ExamSession.builder().examId("linked").userId(OWNER).mockExamId("mock").cycleNumber(1)
                .createdAt(LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC))
                .submissionEvidenceVersion(1).requiredQuestionNumbers(List.of(1,2))
                .submissionCompletedAt(allAccepted?clock.instant():null)
                .attemptGroupId("22222222-2222-4222-8222-222222222222").billingReservationId("reservation")
                .creationOperationId("33333333-3333-4333-8333-333333333333")
                .entitlementState(web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState.CONFIRMED)
                .attemptGroupProjectionStatus(web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupProjectionStatus.OPEN)
                .status(web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus.IN_PROGRESS).active(true).build());
        commands.request(OWNER, KEY);
        for (int i=0;i<30 && !commands.latest(OWNER).isCleanupPrepared();i++) step();
        assertThat(commands.latest(OWNER).isCleanupPrepared()).isTrue();
        assertThat(commands.latest(OWNER).isWriteBlocked()).isTrue();
        return mongo.findOne(Query.query(Criteria.where("aggregateId").is("linked")), DeletionTarget.class);
    }
    BillingDeletionSender sender(BillingDeletionClient remote) {
        var guards = new UserOwnershipGuardService(mongo, new MongoRepositoryFactory(mongo).getRepository(UserOwnershipGuardRepository.class));
        return new BillingDeletionSender(mongo,tx,guards,remote,clock,"test");
    }
    @Test void linkedCleanupCompletesWhileDeliveryQuarantinedAndEvidenceSurvivesThirtyDays() {
        var target = linkedPrepared();
        var remote = mock(BillingDeletionClient.class);
        when(remote.send(any())).thenReturn(new BillingDeletionClient.Result(false, BillingDeletionDelivery.Failure.AUTHENTICATION_FAILED,null));
        sender(remote).tick();
        clock.now = clock.now.plus(Duration.ofDays(31));
        for(int i=0;i<50;i++) step();
        var op = commands.latest(OWNER);
        var retained = mongo.findById(target.getId(),DeletionTarget.class);
        assertThat(retained.getVerifiedAt()).isNotNull();
        assertThat(retained.getBillingDelivery().getState()).isEqualTo(BillingDeletionDelivery.State.NEEDS_REVIEW);
        assertThat(op.isWriteBlocked()).isTrue(); assertThat(op.getCompletedAt()).isNull();
        assertThat(op.getExpiresAt()).isNull(); assertThat(retained.getExpiresAt()).isNull();
        assertThat(mongo.findOne(new Query(),DeletionCommand.class).expiresAt()).isNull();
    }
    @Test void persisted204OpensCheckpointAndCompletedLinkedEvidenceHasOnlyReviewTime() {
        var target = linkedPrepared();
        var remote = mock(BillingDeletionClient.class);
        when(remote.send(any())).thenReturn(new BillingDeletionClient.Result(true,null,null));
        assertThat(sender(remote).tick()).isTrue();
        step(); assertThat(commands.latest(OWNER).isWriteBlocked()).isFalse();
        clock.now = clock.now.plusSeconds(360);
        for(int i=0;i<60 && commands.latest(OWNER).isActiveGuard();i++) step();
        worker.maintainRetention();
        var op = commands.latest(OWNER);
        assertThat(op.getStatus()).isEqualTo(DeletionOperation.Status.COMPLETED);
        assertThat(op.getBillingDetailReviewAt()).isEqualTo(op.getCompletedAt().plus(Duration.ofDays(120)));
        assertThat(op.getExpiresAt()).isNull();
        assertThat(mongo.findById(target.getId(),DeletionTarget.class).getExpiresAt()).isNull();
        verify(remote,times(1)).send(target.getBillingDelivery().getPayload());
    }
    @Test void responseLostRetriesExactPayloadAndStaleVersionCannotAcknowledge() {
        var target = linkedPrepared();
        var remote = mock(BillingDeletionClient.class);
        when(remote.send(any())).thenAnswer(call -> {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(target.getId())),new Update().inc("version",1),DeletionTarget.class);
            return new BillingDeletionClient.Result(true,null,null);
        });
        var sender = sender(remote); sender.tick();
        assertThat(mongo.findById(target.getId(),DeletionTarget.class).getBillingDelivery().getState()).isEqualTo(BillingDeletionDelivery.State.PENDING);
        when(remote.send(any())).thenReturn(new BillingDeletionClient.Result(true,null,null));
        clock.now = clock.now.plusSeconds(31); sender.tick(); // expired claim consumes budget and backs off
        clock.now = clock.now.plusSeconds(5); sender.tick();
        assertThat(mongo.findById(target.getId(),DeletionTarget.class).getBillingDelivery().getState()).isEqualTo(BillingDeletionDelivery.State.ACKED);
        verify(remote,times(2)).send(target.getBillingDelivery().getPayload());
    }
    @Test void senderOffDoesNotInterpretLinkedTargetAsUnlinked() {
        linkedPrepared(); clock.now = clock.now.plusSeconds(360);
        for(int i=0;i<60;i++) step();
        assertThat(commands.latest(OWNER).isWriteBlocked()).isTrue();
        assertThat(commands.latest(OWNER).getSafeToStartLearningAt()).isNull();
    }
    @Test void exactPreflightDoesNotSelectOtherPendingTargetsAndReplaysStoredAck() {
        var target = linkedPrepared();
        var other = DeletionTarget.draining(UUID.randomUUID().toString(), OWNER, DeletionTarget.Type.EXAM, "other");
        other.sealCallbacks(true, true, clock.instant()); other.markPrepared();
        other.prepareBilling("test", UUID.randomUUID().toString(), clock.instant(), clock.instant().minusSeconds(10));
        mongo.insert(other);
        var remote = mock(BillingDeletionClient.class);
        when(remote.send(any())).thenReturn(new BillingDeletionClient.Result(true, null, null));
        var expected = exact(target);
        assertThat(sender(remote).sendExact(expected)).isTrue();
        assertThat(sender(remote).sendExact(expected)).isFalse();
        assertThat(mongo.findById(target.getId(), DeletionTarget.class).getBillingDelivery().getState()).isEqualTo(BillingDeletionDelivery.State.ACKED);
        assertThat(mongo.findById(other.getId(), DeletionTarget.class).getBillingDelivery().getAttempts()).isZero();
        verify(remote, times(1)).send(target.getBillingDelivery().getPayload());
        verifyNoMoreInteractions(remote);
    }
    @Test void exactPreflightRejectsMismatchWithoutWritesOrSend() {
        var target = linkedPrepared();
        var remote = mock(BillingDeletionClient.class);
        for (var wrong : List.of(
                new BillingDeletionSender.ExactTarget(target.getId(), OWNER, UUID.randomUUID().toString(), target.getBillingDelivery().getPayloadDigest()),
                new BillingDeletionSender.ExactTarget(target.getId(), OWNER, target.getBillingDelivery().getEventId(), "0".repeat(64)),
                new BillingDeletionSender.ExactTarget(target.getId(), UUID.randomUUID().toString(), target.getBillingDelivery().getEventId(), target.getBillingDelivery().getPayloadDigest())))
            assertThatThrownBy(() -> sender(remote).sendExact(wrong)).isInstanceOf(IllegalStateException.class);
        assertThat(mongo.findById(target.getId(), DeletionTarget.class).getVersion()).isEqualTo(target.getVersion());
        verifyNoInteractions(remote);
    }
    @Test void exactPreflightHonorsDelayAndPreservesPayloadAfterLostResponse() {
        var target = linkedPrepared();
        var remote = mock(BillingDeletionClient.class);
        when(remote.send(any())).thenReturn(BillingDeletionClient.Result.retry(), new BillingDeletionClient.Result(true, null, null));
        var expected = exact(target);
        sender(remote).sendExact(expected);
        assertThat(sender(remote).sendExact(expected)).isFalse();
        clock.now = clock.now.plusSeconds(5);
        sender(remote).sendExact(expected);
        verify(remote, times(2)).send(target.getBillingDelivery().getPayload());
        assertThat(mongo.findById(target.getId(), DeletionTarget.class).getBillingDelivery().getState()).isEqualTo(BillingDeletionDelivery.State.ACKED);
    }
    @Test void exactPreflightCannotAcknowledgeChangedVersion() {
        var target = linkedPrepared();
        var remote = mock(BillingDeletionClient.class);
        when(remote.send(any())).thenAnswer(call -> {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(target.getId())), new Update().inc("version", 1), DeletionTarget.class);
            return new BillingDeletionClient.Result(true, null, null);
        });
        sender(remote).sendExact(exact(target));
        assertThat(mongo.findById(target.getId(), DeletionTarget.class).getBillingDelivery().getState()).isEqualTo(BillingDeletionDelivery.State.PENDING);
    }
    @Test void exactPreflightDoesNotCreateMissingOwnerOrSend() {
        var target = linkedPrepared();
        mongo.remove(Query.query(Criteria.where("_id").is(OWNER)), UserOwnershipGuard.class);
        var remote = mock(BillingDeletionClient.class);
        assertThatThrownBy(() -> sender(remote).sendExact(exact(target))).isInstanceOf(IllegalStateException.class);
        assertThat(mongo.findById(OWNER, UserOwnershipGuard.class)).isNull();
        assertThat(mongo.findById(target.getId(), DeletionTarget.class).getBillingDelivery().getAttempts()).isZero();
        verifyNoInteractions(remote);
    }
    @Test void exactPreflightPreservesQuarantineAndLiveLease() {
        var target = linkedPrepared();
        target.getBillingDelivery().claim(clock.instant(), "other-worker"); mongo.save(target);
        var remote = mock(BillingDeletionClient.class);
        assertThat(sender(remote).sendExact(exact(target))).isFalse();
        target.getBillingDelivery().review(BillingDeletionDelivery.Failure.RETRY_EXHAUSTED); mongo.save(target);
        assertThat(sender(remote).sendExact(exact(target))).isFalse();
        assertThat(mongo.findById(target.getId(), DeletionTarget.class).getBillingDelivery().getState()).isEqualTo(BillingDeletionDelivery.State.NEEDS_REVIEW);
        verifyNoInteractions(remote);
    }
    @Test void exactPreflightUnknownClaimCommitDoesNotSendOrResetEvidence() {
        var target = linkedPrepared();
        var realTx = tx;
        tx = mock(DeletionTransactions.class);
        when(tx.run(any())).thenAnswer(call -> {
            realTx.run(call.getArgument(0));
            var failure = new com.mongodb.MongoException(91, "fixture unknown commit");
            failure.addLabel(com.mongodb.MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
            throw failure;
        });
        var remote = mock(BillingDeletionClient.class);
        assertThatThrownBy(() -> sender(remote).sendExact(exact(target))).isInstanceOf(com.mongodb.MongoException.class);
        assertThat(mongo.findById(target.getId(), DeletionTarget.class).getBillingDelivery().getAttempts()).isEqualTo(1);
        verifyNoInteractions(remote);
    }
    @Test void exactPreflightRejectsForeignEnvironmentWithoutMutatingTarget() {
        var target = linkedPrepared();
        var remote = mock(BillingDeletionClient.class);
        var guards = new UserOwnershipGuardService(mongo, new MongoRepositoryFactory(mongo).getRepository(UserOwnershipGuardRepository.class));
        var prod = new BillingDeletionSender(mongo, tx, guards, remote, clock, "production");
        assertThatThrownBy(() -> prod.sendExact(exact(target))).isInstanceOf(IllegalStateException.class);
        assertThat(mongo.findById(target.getId(), DeletionTarget.class).getVersion()).isEqualTo(target.getVersion());
        verifyNoInteractions(remote);
    }
    private BillingDeletionSender.ExactTarget exact(DeletionTarget target) {
        return new BillingDeletionSender.ExactTarget(target.getId(), OWNER, target.getBillingDelivery().getEventId(), target.getBillingDelivery().getPayloadDigest());
    }
    private void acknowledgeDeleted() {
        var remote=mock(BillingDeletionClient.class);
        when(remote.send(any())).thenReturn(new BillingDeletionClient.Result(true,null,null));
        sender(remote).tick(); step();
        assertThat(commands.latest(OWNER).isWriteBlocked()).isFalse();
    }
    private PaidHarness paidHarness() {
        var repos=new MongoRepositoryFactory(mongo);
        var ops=repos.getRepository(web.tosunsaeng.domain.exams.domain.repository.ExamCreationOperationRepository.class);
        mongo.getCollection("exam_creation_operations").createIndex(new Document("userId",1).append("activeGuard",1),
                new com.mongodb.client.model.IndexOptions().unique(true).partialFilterExpression(new Document("activeGuard",true)));
        var beans=new org.springframework.beans.factory.support.StaticListableBeanFactory();
        beans.addBean("tx",new org.springframework.transaction.support.TransactionTemplate(new org.springframework.data.mongodb.MongoTransactionManager(mongo.getMongoDatabaseFactory())));
        var props=new web.tosunsaeng.domain.usermerge.config.UserMergedProperties(); props.setWriterEnabled(true);
        var guards=new UserOwnershipGuardService(mongo,repos.getRepository(UserOwnershipGuardRepository.class));
        var owners=new web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor(props,guards,
                beans.getBeanProvider(org.springframework.transaction.support.TransactionOperations.class),clock);
        org.springframework.test.util.ReflectionTestUtils.setField(owners,"deletionAccess",access);
        beans.addBean("owners",owners);
        var provider=beans.getBeanProvider(web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor.class);
        var deleted=new DeletedExamContinuationStore(mongo,guards,clock);
        var catalog=mock(web.tosunsaeng.domain.exams.application.MockExamCatalogService.class);
        var questions=List.of(web.tosunsaeng.domain.exams.domain.entity.Question.builder().questionNumber(1).build(),
                web.tosunsaeng.domain.exams.domain.entity.Question.builder().questionNumber(2).build());
        var first=web.tosunsaeng.domain.exams.domain.entity.MockExam.builder().mockExamId("mock").questions(questions).build();
        var next=web.tosunsaeng.domain.exams.domain.entity.MockExam.builder().mockExamId("next").questions(questions).build();
        when(catalog.getRequiredExam("mock")).thenReturn(first); when(catalog.getRequiredExam("next")).thenReturn(next);
        when(catalog.findAssignableExams()).thenReturn(List.of(new web.tosunsaeng.domain.exams.application.MockExamCatalogService.CatalogExam(first,1),
                new web.tosunsaeng.domain.exams.application.MockExamCatalogService.CatalogExam(next,2)));
        var prep=new web.tosunsaeng.domain.exams.application.PaidExamPreparationService(mongo,ops,catalog,
                new web.tosunsaeng.domain.exams.domain.repository.ExamSessionCompletionQuery(mongo),
                new web.tosunsaeng.domain.exams.application.ExamSubmissionEvidence(mongo),provider,clock);
        org.springframework.test.util.ReflectionTestUtils.setField(prep,"deletedSources",deleted);
        var settings=new web.tosunsaeng.domain.exams.billing.reconciliation.ReservationReconciliationProperties();
        var execution=new web.tosunsaeng.domain.exams.billing.reconciliation.ReservationOperationExecution(mongo,settings,clock);
        var store=new web.tosunsaeng.domain.exams.billing.reconciliation.ReservationRecoveryStore(mongo,execution,settings);
        var lifecycle=new web.tosunsaeng.domain.exams.application.PaidExamLifecycleService(mongo,ops,provider,execution,
                mock(web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventProperties.class));
        org.springframework.test.util.ReflectionTestUtils.setField(lifecycle,"deletedSources",deleted);
        org.springframework.test.util.ReflectionTestUtils.setField(lifecycle,"deletionAccess",access);
        var billing=mock(web.tosunsaeng.domain.exams.billing.BillingReservationClient.class);
        var circuit=mock(web.tosunsaeng.domain.exams.billing.reconciliation.ReservationAuthCircuit.class);
        var recovery=new web.tosunsaeng.domain.exams.application.PaidExamRecoveryService(execution,lifecycle,billing,store,circuit);
        var evidence=new web.tosunsaeng.domain.exams.application.PaidReservationEvidenceStore(ops,mongo,provider,clock);
        var sagaProps=new web.tosunsaeng.domain.exams.billing.BillingSagaProperties(); sagaProps.setCreationSagaEnabled(true);
        var saga=new web.tosunsaeng.domain.exams.application.PaidExamCreationSaga(ops,prep,evidence,recovery,lifecycle,
                mock(web.tosunsaeng.domain.exams.application.BillingExamCreationSaga.class),billing,sagaProps,circuit,mongo,catalog,clock);
        return new PaidHarness(prep,saga,billing,ops,store);
    }
    private record PaidHarness(web.tosunsaeng.domain.exams.application.PaidExamPreparationService prep,
            web.tosunsaeng.domain.exams.application.PaidExamCreationSaga saga,
            web.tosunsaeng.domain.exams.billing.BillingReservationClient billing,
            web.tosunsaeng.domain.exams.domain.repository.ExamCreationOperationRepository ops,
            web.tosunsaeng.domain.exams.billing.reconciliation.ReservationRecoveryStore store) { }

    private web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationSnapshot paidSnapshot(PaidHarness h,String key,boolean confirmed) {
        var op=h.ops().findByUserIdAndOperationId(OWNER,key).orElseThrow();
        return new web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationSnapshot(key,
                "44444444-4444-4444-8444-444444444444",web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind.INITIAL,
                confirmed?web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus.CONFIRMED:web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus.RESERVED,
                "55555555-5555-4555-8555-555555555555",confirmed?web.tosunsaeng.domain.exams.billing.BillingReservationClient.AttemptGroupStatus.OPEN:null,
                op.getSessionId(),op.getMockExamId(),null,null,clock.instant().plusSeconds(300),confirmed?clock.instant():null,
                web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.Source.SUBSCRIPTION,
                new web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.Transition(op.getExpectedAttemptGroupId(),op.getReplacementSourceSessionId()),null);
    }
    @Test void deletedPartialStartsInitialWithoutRestoringOldSessionAndSameKeyReplays() {
        var old=linkedPrepared(); acknowledgeDeleted(); var h=paidHarness(); String key=UUID.randomUUID().toString();
        when(h.billing().reservePaid(eq(key),any())).thenAnswer(c->paidSnapshot(h,key,false));
        when(h.billing().confirmPaid(eq(key),any(),any(),any(),any())).thenAnswer(c->paidSnapshot(h,key,true));
        when(h.billing().statusPaid(OWNER,key)).thenAnswer(c->paidSnapshot(h,key,true));
        var result=h.saga().start(OWNER,key);
        assertThat(result.session().getMockExamId()).isEqualTo("mock");
        assertThat(result.session().getBillingReservationKind()).isEqualTo(web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind.INITIAL);
        assertThat(mongo.findById("linked",ExamSession.class)).isNull();
        assertThat(mongo.findById("linked",DeletedExamContinuation.class).getTransferState()).isEqualTo(DeletedExamContinuation.TransferState.TRANSFERRED);
        assertThat(h.ops().findByUserIdAndOperationId(OWNER,key).orElseThrow().getPaidDeletedSource().eventId()).isEqualTo(old.getBillingDelivery().getEventId());
        assertThat(h.saga().start(OWNER,key).session().getExamId()).isEqualTo(result.session().getExamId());
        verify(h.billing(),times(1)).reservePaid(eq(key),any());
        mongo.remove(Query.query(Criteria.where("_id").is(result.session().getExamId())),ExamSession.class);
        assertThatThrownBy(()->h.saga().start(OWNER,key))
                .isInstanceOfSatisfying(web.tosunsaeng.domain.exams.exception.ExamsException.class,
                        e->assertThat(e.getCode()).isEqualTo(web.tosunsaeng.global.error.code.status.ErrorStatus._EXAM_NOT_FOUND));
        verify(h.billing(),times(1)).reservePaid(eq(key),any());
    }
    @Test void deletedAllAcceptedChoosesNextAndRejectsMissingAck() {
        linkedPrepared(true); var h=paidHarness();
        assertThatThrownBy(()->h.prep().prepare(OWNER,UUID.randomUUID().toString())).isInstanceOf(RuntimeException.class);
        acknowledgeDeleted();
        var op=h.prep().prepare(OWNER,UUID.randomUUID().toString());
        assertThat(op.getMockExamId()).isEqualTo("next");
        assertThat(op.getPaidSelection().startReason()).isEqualTo(web.tosunsaeng.domain.exams.domain.entity.PaidExamSelection.StartReason.NEW_AFTER_SUBMISSION);
        assertThat(h.prep().prepare(OWNER,op.getOperationId()).getCommandId()).isEqualTo(op.getCommandId());
    }
    @Test void deletedInitialRejectsReplacement() {
        linkedPrepared(); acknowledgeDeleted(); var h=paidHarness(); String key=UUID.randomUUID().toString();
        var op=h.prep().prepare(OWNER,key);
        var s=paidSnapshot(h,key,false);
        var wrong=new web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationSnapshot(key,s.reservationId(),
                web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind.REPLACEMENT,s.reservationStatus(),op.getExpectedAttemptGroupId(),null,
                op.getSessionId(),op.getMockExamId(),null,null,s.expiresAt(),null,s.entitlementSource(),null,null);
        assertThatThrownBy(()->web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.matches(op,wrong))
                .isInstanceOf(web.tosunsaeng.domain.exams.billing.BillingClientException.class);
    }
    @Test void paidWorkerDueRequiresExplicitPaidRoutingFlag() {
        linkedPrepared(); acknowledgeDeleted(); var h=paidHarness(); var op=h.prep().prepare(OWNER,UUID.randomUUID().toString());
        clock.now=clock.now.plusSeconds(121);
        assertThat(h.store().due()).isEmpty();
        org.springframework.test.util.ReflectionTestUtils.setField(h.store(),"paidEnabled",true);
        assertThat(h.store().due()).extracting(web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation::getCommandId).contains(op.getCommandId());
    }
    @Test void deletedMissingCoverageCannotFallBackToUnrelatedInitial() {
        linkedPrepared(); acknowledgeDeleted(); var h=paidHarness();
        mongo.updateFirst(Query.query(Criteria.where("_id").is("linked")),new Update().unset("paidStartReason"),DeletedExamContinuation.class);
        assertThatThrownBy(()->h.prep().prepare(OWNER,UUID.randomUUID().toString())).isInstanceOf(IllegalStateException.class);
        assertThat(h.ops().count()).isZero();
        verifyNoInteractions(h.billing());
    }
    @Test void durablePreReserveRejectionReleasesDeletedClaimWithoutResurrection() {
        linkedPrepared(); acknowledgeDeleted(); var h=paidHarness(); String key=UUID.randomUUID().toString();
        when(h.billing().reservePaid(eq(key),any())).thenThrow(new web.tosunsaeng.domain.exams.billing.BillingClientException(
                web.tosunsaeng.domain.exams.billing.BillingClientException.Category.TEMPORARILY_UNAVAILABLE,1));
        assertThatThrownBy(()->h.saga().start(OWNER,key)).isInstanceOf(web.tosunsaeng.domain.exams.exception.ExamsException.class);
        assertThat(mongo.findById("linked",DeletedExamContinuation.class).getTransferState()).isEqualTo(DeletedExamContinuation.TransferState.CLAIMED);
        when(h.billing().statusPaid(OWNER,key)).thenThrow(new web.tosunsaeng.domain.exams.billing.BillingClientException(
                web.tosunsaeng.domain.exams.billing.BillingClientException.Category.PRE_RESERVE_REJECTED,null));
        assertThatThrownBy(()->h.saga().start(OWNER,key)).isInstanceOf(web.tosunsaeng.domain.exams.exception.ExamsException.class);
        assertThat(h.ops().findByUserIdAndOperationId(OWNER,key).orElseThrow().isTerminal()).isTrue();
        assertThat(mongo.findById("linked",DeletedExamContinuation.class).getTransferState()).isEqualTo(DeletedExamContinuation.TransferState.AVAILABLE);
        assertThat(mongo.count(new Query(),ExamSession.class)).isZero();
        verify(h.billing(),times(1)).reservePaid(eq(key),any());
    }
    void step() { clock.now = clock.now.plusSeconds(10); worker.tick(); }
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-03T01:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
