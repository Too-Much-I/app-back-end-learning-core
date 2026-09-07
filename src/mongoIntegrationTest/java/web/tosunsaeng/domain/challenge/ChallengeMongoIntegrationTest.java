package web.tosunsaeng.domain.challenge;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Query;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static web.tosunsaeng.domain.challenge.ChallengeModels.*;

@Testcontainers
class ChallengeMongoIntegrationTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");
    static final String OWNER = "30000000-0000-4000-8000-000000000001";
    static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");
    static MongoClient client;
    static SimpleMongoClientDatabaseFactory factory;
    MongoTemplate mongo;
    ChallengeStore store;
    ChallengeTransactions tx;
    ChallengeCatalog catalog;
    ChallengeService service;
    ChallengeCallbackService callbacks;
    ChallengeAudioStorage audio;
    ChallengeAiClient ai;
    ChallengeMetrics metrics;
    ChallengeWorker worker;
    MutableClock clock;
    UserOwnedTransactionExecutor guards;
    @BeforeAll static void connect() { client = MongoClients.create(MONGO.getReplicaSetUrl()); factory = new SimpleMongoClientDatabaseFactory(client, "isolated-challenge-it"); }
    @AfterAll static void disconnect() { client.close(); }
    @BeforeEach void setup() {
        mongo = new MongoTemplate(factory);
        for (String collection : ChallengeStartupValidator.COLLECTIONS) {
            if (!mongo.collectionExists(collection)) mongo.createCollection(collection);
            mongo.remove(new Query(), collection);
        }
        for (var expected : ChallengeStartupValidator.INDEXES) {
            Index index = new Index().named(expected.name()); expected.keys().keySet().forEach(k -> index.on(k, Sort.Direction.ASC));
            if (expected.unique()) index.unique(); mongo.indexOps(expected.collection()).ensureIndex(index);
        }
        List<Document> questions = new ArrayList<>();
        for (int n = 1; n <= 3; n++) questions.add(new Document("questionNumber", n).append("questionId", "Q" + n)
                .append("korean", "테스트 문장 " + n).append("referenceAnswer", "Fixture answer " + n).append("difficulty", n));
        mongo.insert(new Document("dayNumber", 1).append("questions", questions), ChallengeCatalog.QUESTIONS);
        clock = new MutableClock(NOW); guards = mock(UserOwnedTransactionExecutor.class); tx = new ChallengeTransactions(factory, guards);
        store = spy(new ChallengeStore(mongo)); catalog = new ChallengeCatalog(mongo, clock);
        new ChallengeStartupValidator(store, tx, catalog).validate();
        audio = mock(ChallengeAudioStorage.class); ai = mock(ChallengeAiClient.class); metrics = new ChallengeMetrics(new SimpleMeterRegistry());
        when(audio.read(anyString())).thenReturn(new byte[]{1, 2, 3});
        when(ai.send(any(), any(), any(), any())).thenReturn(new ChallengeAiClient.Reply(true, false, null));
        wire(tx);
    }
    void wire(ChallengeTransactions transactions) {
        service = new ChallengeService(store, transactions, catalog, audio, clock);
        callbacks = new ChallengeCallbackService(store, transactions, clock, metrics);
        worker = new ChallengeWorker(store, transactions, service, callbacks, audio, ai, clock, metrics);
    }
    String start(int q) { return service.start(OWNER, q, "2026-09-07").attemptId(); }
    String submitted() { String id = start(1); service.submit(OWNER, 1, id, UUID.randomUUID().toString()); return id; }
    ChallengeCallback callback(String id, int generation, String outcome) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract_version", "v1"); body.put("callback_id", UUID.randomUUID().toString()); body.put("attempt_id", id);
        body.put("job_id", Job.id(id, generation)); body.put("grading_attempt", generation); body.put("outcome", outcome);
        body.put("transcript", null); body.put("verdict", null); body.put("corrected_answer", null); body.put("feedback", null); body.put("error", null);
        if (outcome.equals("failed")) body.put("error", Map.of("code", "MODEL_UNAVAILABLE", "retryable", true));
        try { return ChallengeCallback.parse(ChallengeCallback.JSON.writeValueAsBytes(body), "v1"); } catch (Exception e) { throw new AssertionError(e); }
    }
    @Test void oneTimeBaseDateAndNonCyclicCatalog() {
        clock.at(NOW.plusSeconds(86400)); catalog.initialize();
        assertThat(catalog.baseDate()).isEqualTo(LocalDate.parse("2026-09-07"));
        assertThatThrownBy(() -> catalog.questions(catalog.today())).isInstanceOf(ChallengeFailure.class);
        assertThat(mongo.count(new Query(), ChallengeCatalog.STATE)).isEqualTo(1);
    }
    @Test void startupFailsBeforeBaseDateOnMissingIndex() {
        mongo.remove(new Query(), ChallengeCatalog.STATE);
        mongo.indexOps("challenge_10s_attempts").dropIndex("challenge_expiry");
        assertThatThrownBy(() -> new ChallengeStartupValidator(store, tx, catalog).validate()).isInstanceOf(IllegalStateException.class);
        assertThat(mongo.count(new Query(), ChallengeCatalog.STATE)).isZero();
    }
    @Test void sequentialStartSameAttemptNoJobsBeforeSubmit() {
        assertThatThrownBy(() -> start(2)).satisfies(e -> assertThat(((ChallengeFailure)e).code).isEqualTo("CHALLENGE_PREVIOUS_QUESTION_INCOMPLETE"));
        String first = start(1); assertThat(start(1)).isEqualTo(first);
        assertThat(store.attempt(first).uploadKey).isEqualTo("temp/challenges/" + first + "/q_1.m4a");
        assertThat(mongo.count(new Query(), Job.class)).isZero();
        assertThat(store.attempt(first).submissionDeadlineAt).isEqualTo(NOW.plusSeconds(3600));
    }
    @Test void replayAfterDeadlineIsOriginalResponseAndOnlyOneJob() {
        String id = start(1), key = UUID.randomUUID().toString(); var response = service.submit(OWNER, 1, id, key);
        callbacks.accept(callback(id, 1, "no_speech")); clock.at(NOW.plusSeconds(7200));
        assertThat(service.submit(OWNER, 1, id, key)).isEqualTo(response);
        assertThat(response.gradingStatus()).isEqualTo("pending");
        assertThat(store.attempt(id).gradingStatus).isEqualTo("completed");
        assertThat(mongo.count(new Query(), Job.class)).isEqualTo(1);
        verify(audio, times(1)).validate(anyString());
    }
    @Test void sameKeyOtherCommandConflictsAndDifferentKeyCannotResubmit() {
        String id = start(1), key = UUID.randomUUID().toString(); service.submit(OWNER, 1, id, key); String other = start(2);
        assertThatThrownBy(() -> service.submit(OWNER, 2, other, key)).satisfies(e -> assertThat(((ChallengeFailure)e).code).isEqualTo("CHALLENGE_IDEMPOTENCY_CONFLICT"));
        assertThatThrownBy(() -> service.submit(OWNER, 1, id, UUID.randomUUID().toString())).satisfies(e -> assertThat(((ChallengeFailure)e).code).isEqualTo("CHALLENGE_ALREADY_ATTEMPTED"));
    }
    @Test void validationCrossingDeadlineCommitsExpiryWithoutPartialJob() {
        String id = start(1); doAnswer(i -> { clock.at(NOW.plusSeconds(3600)); return null; }).when(audio).validate(anyString());
        assertThatThrownBy(() -> service.submit(OWNER, 1, id, UUID.randomUUID().toString())).satisfies(e -> assertThat(((ChallengeFailure)e).status).isEqualTo(410));
        assertThat(store.attempt(id).state).isEqualTo(State.EXPIRED);
        assertThat(mongo.count(new Query(), Job.class)).isZero(); assertThat(mongo.count(new Query(), SubmitReceipt.class)).isZero();
    }
    @ParameterizedTest @ValueSource(strings = {"attempt", "job", "receipt"})
    void everySubmitWriteFailureRollsBack(String step) {
        String id = start(1);
        if (step.equals("attempt")) doAnswer(i -> { Attempt a = i.getArgument(0); i.callRealMethod(); throw new IllegalStateException("injected"); }).when(store).save(any(Attempt.class));
        if (step.equals("job")) doAnswer(i -> { i.callRealMethod(); throw new IllegalStateException("injected"); }).when(store).insert(any(Job.class));
        if (step.equals("receipt")) doAnswer(i -> { i.callRealMethod(); throw new IllegalStateException("injected"); }).when(store).insert(any(SubmitReceipt.class));
        assertThatThrownBy(() -> service.submit(OWNER, 1, id, UUID.randomUUID().toString())).isInstanceOf(ChallengeFailure.class);
        assertThat(store.attempt(id).state).isEqualTo(State.CREATED);
        assertThat(mongo.count(new Query(), Job.class)).isZero(); assertThat(mongo.count(new Query(), SubmitReceipt.class)).isZero();
    }
    @Test void realCommitThenLostAckRecoversSubmitAndCallbackReceipts() {
        String id = start(1); AtomicBoolean lose = new AtomicBoolean(true);
        ChallengeTransactions uncertain = new ChallengeTransactions(factory, guards) {
            @Override public <T> T run(String owner, Supplier<T> command) {
                T result = super.run(owner, command);
                if (lose.getAndSet(false)) { MongoException error = new MongoException("synthetic lost commit ack"); error.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL); throw error; }
                return result;
            }
        };
        wire(uncertain); service.submit(OWNER, 1, id, UUID.randomUUID().toString());
        assertThat(store.attempt(id).state).isEqualTo(State.SUBMITTED);
        lose.set(true); ChallengeCallback c = callback(id, 1, "no_speech"); callbacks.accept(c);
        assertThat(store.callback(c.callbackId())).isNotNull(); assertThat(store.attempt(id).gradingStatus).isEqualTo("completed");
    }
    @Test void preCommitUnknownDoesNotFabricateSuccessOrBlindlyReplay() {
        String id = start(1); AtomicInteger runs = new AtomicInteger();
        ChallengeTransactions uncertain = new ChallengeTransactions(factory, guards) {
            @Override public <T> T run(String owner, Supplier<T> command) {
                runs.incrementAndGet(); MongoException error = new MongoException("synthetic unknown"); error.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL); throw error;
            }
        };
        wire(uncertain); assertThatThrownBy(() -> service.submit(OWNER, 1, id, UUID.randomUUID().toString())).satisfies(e -> assertThat(((ChallengeFailure)e).status).isEqualTo(500));
        assertThat(runs.get()).isEqualTo(1); assertThat(store.attempt(id).state).isEqualTo(State.CREATED);
    }
    @Test void expiredCountsZeroButProgressAndReferenceRemainAvailable() {
        for (int q = 1; q <= 3; q++) { start(q); clock.at(clock.instant().plusSeconds(3600)); service.today(OWNER); }
        var progress = service.today(OWNER); assertThat(progress.dailyStatus()).isEqualTo("completed");
        assertThat(progress.completedQuestionNumbers()).containsExactly(1, 2, 3);
        var results = (ChallengeViews.Results) service.results(OWNER, "2026-09-07", 1);
        assertThat(results.solvedQuestionCount()).isZero(); assertThat(results.question()).isNotNull(); assertThat(results.question().submittedAt()).isNull();
        assertThat(service.history(OWNER, "2026-09").dates()).containsExactly(new ChallengeViews.Day("2026-09-07", false, 0));
    }
    @Test void crossMidnightSubmitBelongsToOriginalDate() {
        clock.at(Instant.parse("2026-09-07T14:59:50Z")); String id = start(1);
        clock.at(Instant.parse("2026-09-07T15:00:10Z")); service.submit(OWNER, 1, id, UUID.randomUUID().toString());
        assertThat(((ChallengeViews.Count)service.results(OWNER, "2026-09-07", null)).solvedQuestionCount()).isEqualTo(1);
        assertThatThrownBy(() -> start(2)).satisfies(e -> assertThat(((ChallengeFailure)e).code).isEqualTo("CHALLENGE_DATE_CHANGED"));
    }
    @Test void foreignOwnerCannotAccessOrSubmit() {
        String id = start(1);
        assertThatThrownBy(() -> service.upload(UUID.randomUUID().toString(), id)).satisfies(e -> assertThat(((ChallengeFailure)e).status).isEqualTo(403));
        assertThatThrownBy(() -> service.submit(UUID.randomUUID().toString(), 1, id, UUID.randomUUID().toString())).satisfies(e -> assertThat(((ChallengeFailure)e).status).isEqualTo(403));
    }
    @Test void earlyCallbackWinsOverLater202() {
        String id = submitted();
        when(ai.send(any(), any(), any(), any())).thenAnswer(i -> { callbacks.accept(callback(id, 1, "no_speech")); return new ChallengeAiClient.Reply(true, false, null); });
        worker.tick(); assertThat(store.attempt(id).gradingStatus).isEqualTo("completed");
        assertThat(store.job(Job.id(id, 1)).state).isEqualTo(JobState.COMPLETED);
        assertThat(store.job(Job.id(id, 1)).acceptedAt).isNull();
    }
    @Test void changedAudioIsNeverSentAgainOrEvadedWithNewGeneration() {
        String id = submitted(); when(ai.send(any(), any(), any(), any())).thenReturn(new ChallengeAiClient.Reply(false, true, null));
        worker.tick(); String digest = store.attempt(id).audioDigest;
        when(audio.read(anyString())).thenReturn(new byte[]{4,5,6}); clock.at(NOW.plusSeconds(31)); worker.tick();
        verify(ai, times(1)).send(any(), any(), any(), any());
        assertThat(store.attempt(id).audioDigest).isEqualTo(digest); assertThat(store.attempt(id).gradingStatus).isEqualTo("failed");
        assertThat(store.attempt(id).state).isEqualTo(State.SUBMITTED); assertThat(store.attempt(id).generation).isEqualTo(1);
    }
    @Test void budgetAndRetryAfterSurviveRestartThenLateCallbackIsNoOp() {
        String id = submitted(); when(ai.send(any(), any(), any(), any())).thenReturn(new ChallengeAiClient.Reply(false, true, NOW.plusSeconds(3600)));
        worker.tick(); Job j = store.job(Job.id(id, 1)); assertThat(j.nextAttemptAt).isEqualTo(NOW.plusSeconds(300));
        clock.at(NOW.plusSeconds(299)); wire(tx); worker.tick(); verify(ai, times(1)).send(any(), any(), any(), any());
        clock.at(NOW.plusSeconds(300)); worker.tick();
        assertThat(store.attempt(id).gradingStatus).isEqualTo("failed"); assertThat(store.attempt(id).generation).isEqualTo(1);
        callbacks.accept(callback(id, 1, "no_speech")); assertThat(store.attempt(id).gradingStatus).isEqualTo("failed");
        assertThat(store.job(j.id).firstDispatchAt).isEqualTo(NOW);
    }
    @Test void threeAcceptedGenerationTimeoutsTerminateAndOldCallbackIsStale() {
        String id = submitted();
        for (int generation = 1; generation <= 3; generation++) {
            worker.tick(); assertThat(store.job(Job.id(id, generation)).callbackDeadlineAt).isEqualTo(clock.instant().plusSeconds(120));
            clock.at(clock.instant().plusSeconds(120)); worker.tick();
        }
        assertThat(store.attempt(id).generation).isEqualTo(3); assertThat(store.attempt(id).gradingStatus).isEqualTo("failed");
        callbacks.accept(callback(id, 1, "no_speech")); callbacks.accept(callback(id, 3, "no_speech"));
        assertThat(store.attempt(id).gradingStatus).isEqualTo("failed");
        assertThatThrownBy(() -> callbacks.accept(callback(id, 4, "no_speech"))).satisfies(e -> assertThat(((ChallengeFailure)e).status).isEqualTo(409));
    }
    @Test void callbackDuplicatesConflictsAndRollbackAreAtomic() {
        String id = submitted(); ChallengeCallback c = callback(id, 1, "no_speech");
        doAnswer(i -> { i.callRealMethod(); throw new IllegalStateException("injected"); }).when(store).insert(any(CallbackReceipt.class));
        assertThatThrownBy(() -> callbacks.accept(c)).isInstanceOf(ChallengeFailure.class);
        assertThat(store.attempt(id).gradingStatus).isEqualTo("pending"); assertThat(store.job(Job.id(id, 1)).callbackDigest).isNull();
        doCallRealMethod().when(store).insert(any(CallbackReceipt.class));
        callbacks.accept(c); callbacks.accept(c); callbacks.accept(callback(id, 1, "no_speech"));
        assertThatThrownBy(() -> callbacks.accept(callback(id, 1, "failed"))).satisfies(e -> assertThat(((ChallengeFailure)e).code).isEqualTo("CALLBACK_PAYLOAD_CONFLICT"));
        assertThat(store.attempt(id).result).isNotNull();
    }
    @Test void twoInstancesStartAndSubmitConvergeToOneAggregate() throws Exception {
        List<String> ids = race(() -> start(1), () -> start(1)); assertThat(ids.get(0)).isEqualTo(ids.get(1));
        String key = UUID.randomUUID().toString(), id = ids.getFirst();
        var responses = race(() -> service.submit(OWNER, 1, id, key), () -> service.submit(OWNER, 1, id, key));
        assertThat(responses.getFirst()).isEqualTo(responses.get(1));
        assertThat(mongo.count(new Query(), Attempt.class)).isEqualTo(1); assertThat(mongo.count(new Query(), Job.class)).isEqualTo(1);
    }
    @Test void concurrentDuplicateCallbacksAndTimeoutConverge() throws Exception {
        String id = submitted(); ChallengeCallback c = callback(id, 1, "no_speech");
        race(() -> { callbacks.accept(c); return true; }, () -> { callbacks.accept(c); return true; });
        assertThat(mongo.count(new Query(), CallbackReceipt.class)).isEqualTo(1);
        assertThat(store.attempt(id).gradingStatus).isEqualTo("completed");
    }
    @Test void actualCallbackTimeoutRacePreservesOneWinner() throws Exception {
        String id = submitted(); worker.tick(); clock.at(NOW.plusSeconds(120));
        race(() -> { callbacks.accept(callback(id, 1, "no_speech")); return true; }, () -> { worker.tick(); return true; });
        Attempt a = store.attempt(id); Job j = store.job(Job.id(id, 1));
        if (a.generation == 1) {
            assertThat(a.gradingStatus).isEqualTo("completed"); assertThat(j.state).isEqualTo(JobState.COMPLETED);
            assertThat(store.job(Job.id(id, 2))).isNull();
        } else {
            assertThat(a.generation).isEqualTo(2); assertThat(a.result).isNull();
            assertThat(j.state).isEqualTo(JobState.TIMED_OUT); assertThat(store.job(Job.id(id, 2))).isNotNull();
        }
    }
    @Test void twoPublishersCannotClaimSameLiveLease() throws Exception {
        String id = submitted();
        ChallengeWorker other = new ChallengeWorker(store, tx, service, callbacks, audio, ai, clock, metrics);
        race(() -> { worker.tick(); return true; }, () -> { other.tick(); return true; });
        verify(ai, times(1)).send(any(), any(), any(), any());
        assertThat(store.job(Job.id(id, 1)).state).isEqualTo(JobState.WAITING_CALLBACK);
    }
    @Test void submitAndExpiryRaceNeverCreatesExpiredJob() throws Exception {
        String id = start(1); CountDownLatch validating = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(i -> { validating.countDown(); if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); return null; }).when(audio).validate(anyString());
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<?> submit = pool.submit(() -> assertThatThrownBy(() -> service.submit(OWNER, 1, id, UUID.randomUUID().toString()))
                    .satisfies(e -> assertThat(((ChallengeFailure)e).status).isEqualTo(410)));
            try {
                assertThat(validating.await(10, TimeUnit.SECONDS)).isTrue(); clock.at(NOW.plusSeconds(3600)); worker.expire();
            } finally { release.countDown(); }
            submit.get(10, TimeUnit.SECONDS);
        }
        assertThat(store.attempt(id).state).isEqualTo(State.EXPIRED); assertThat(store.job(Job.id(id, 1))).isNull();
    }
    @Test void retryableCallbackCreatesNewGenerationWithSameAudioDigest() {
        String id = submitted(); worker.tick(); String digest = store.attempt(id).audioDigest;
        callbacks.accept(callback(id, 1, "failed"));
        assertThat(store.attempt(id).generation).isEqualTo(2); assertThat(store.attempt(id).audioDigest).isEqualTo(digest);
        worker.tick(); assertThat(store.job(Job.id(id, 2)).state).isEqualTo(JobState.WAITING_CALLBACK);
        callbacks.accept(callback(id, 1, "no_speech")); assertThat(store.attempt(id).gradingStatus).isEqualTo("processing");
    }
    @Test void mixedExpiryAndSubmissionCountsActualSubmittedOnlyAndHistoryClipsMonths() {
        String first = start(1); clock.at(NOW.plusSeconds(3600)); service.today(OWNER);
        String second = start(2); service.submit(OWNER, 2, second, UUID.randomUUID().toString());
        assertThat(store.attempt(first).state).isEqualTo(State.EXPIRED);
        assertThat(service.history(OWNER, "2026-09").dates()).containsExactly(new ChallengeViews.Day("2026-09-07", true, 1));
        assertThat(service.history(OWNER, "2026-08").dates()).isEmpty();
        assertThatThrownBy(() -> service.history(OWNER, "2026-10")).isInstanceOf(ChallengeFailure.class);
        clock.at(Instant.parse("2026-10-03T00:00:00Z"));
        assertThat(service.history(OWNER, "2026-09").dates()).hasSize(24);
        assertThat(service.history(OWNER, "2026-10").dates()).hasSize(3);
    }
    @Test void actualMongoshPreparationDryRunAndApplyPreserveBaseDateAndData() throws Exception {
        String id = submitted(); callbacks.accept(callback(id, 1, "no_speech"));
        MONGO.copyFileToContainer(org.testcontainers.utility.MountableFile.forHostPath(
                java.nio.file.Path.of("scripts/mongodb/challenge-10s-prepare.js").toAbsolutePath()), "/tmp/challenge-prepare.js");
        for (String apply : List.of("false", "true")) {
            if (apply.equals("true")) mongo.indexOps("challenge_10s_attempts").dropIndex("challenge_expiry");
            var result = MONGO.execInContainer("env", "MONGODB_URI=mongodb://localhost:27017/", "MONGODB_DATABASE=isolated-challenge-it",
                    "CHALLENGE_MONGOSH_PAYLOAD=true", "CHALLENGE_PREPARE_APPLY=" + apply, "CHALLENGE_WRITERS_DRAINED=true",
                    "mongosh", "--nodb", "--quiet", "--file", "/tmp/challenge-prepare.js");
            assertThat(result.getExitCode()).describedAs(result.getStdout() + result.getStderr()).isZero();
            assertThat(result.getStdout()).contains("\"days\":1");
        }
        new ChallengeStartupValidator(store, tx, catalog).validate();
        assertThat(catalog.baseDate()).isEqualTo(LocalDate.parse("2026-09-07"));
        assertThat(store.attempt(id).gradingStatus).isEqualTo("completed");
    }
    @Test void actualOwnershipGuardParticipatesInChallengeTransaction() {
        var repository = new org.springframework.data.mongodb.repository.support.MongoRepositoryFactory(mongo)
                .getRepository(web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository.class);
        if (!mongo.collectionExists("user_ownership_guards")) mongo.createCollection("user_ownership_guards");
        mongo.remove(new Query(), "user_ownership_guards");
        var properties = new web.tosunsaeng.domain.usermerge.config.UserMergedProperties(); properties.setWriterEnabled(true);
        var guardService = new web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardService(mongo, repository);
        var provider = new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(org.springframework.transaction.support.TransactionOperations.class);
        var realGuards = new UserOwnedTransactionExecutor(properties, guardService, provider, clock);
        wire(new ChallengeTransactions(factory, realGuards)); String id = start(1);
        var before = repository.findById(OWNER).orElseThrow();
        doAnswer(i -> { i.callRealMethod(); throw new IllegalStateException("injected"); }).when(store).insert(any(Job.class));
        assertThatThrownBy(() -> service.submit(OWNER, 1, id, UUID.randomUUID().toString())).isInstanceOf(ChallengeFailure.class);
        assertThat(repository.findById(OWNER).orElseThrow().getRevision()).isEqualTo(before.getRevision());
        assertThat(store.attempt(id).state).isEqualTo(State.CREATED);
    }
    @Test void expiredLeaseCanBeRecoveredButOldWorkerCannotUpdateWinner() {
        String id = submitted(); AtomicInteger calls = new AtomicInteger();
        when(ai.send(any(), any(), any(), any())).thenAnswer(i -> {
            if (calls.incrementAndGet() == 1) {
                clock.at(NOW.plusSeconds(31));
                ChallengeWorker another = new ChallengeWorker(store, tx, service, callbacks, audio, ai, clock, metrics);
                another.tick(); return new ChallengeAiClient.Reply(false, false, null);
            }
            return new ChallengeAiClient.Reply(true, false, null);
        });
        worker.tick(); Job job = store.job(Job.id(id, 1));
        assertThat(job.state).isEqualTo(JobState.WAITING_CALLBACK); assertThat(job.dispatchCount).isEqualTo(2);
        assertThat(job.firstDispatchAt).isEqualTo(NOW); assertThat(store.attempt(id).gradingStatus).isEqualTo("processing");
    }
    @SafeVarargs static <T> List<T> race(Callable<T>... tasks) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(tasks.length)) {
            CyclicBarrier barrier = new CyclicBarrier(tasks.length); List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) futures.add(pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return task.call(); }));
            List<T> values = new ArrayList<>(); for (Future<T> future : futures) values.add(future.get(20, TimeUnit.SECONDS)); return values;
        }
    }
    static class MutableClock extends Clock {
        final AtomicReference<Instant> current;
        MutableClock(Instant now) { current = new AtomicReference<>(now); }
        void at(Instant time) { current.set(time); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return current.get(); }
    }
}
