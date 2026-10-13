package web.tosunsaeng.domain.answerlearning;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import web.tosunsaeng.domain.learningrecorddeletion.application.DeletionFailure;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static web.tosunsaeng.domain.answerlearning.LearningModels.*;

@Testcontainers
class LearningMongoIntegrationTest {
    @Container static final MongoDBContainer MONGO=new MongoDBContainer("mongo:7.0.14");
    static MongoClient client;
    static final Instant NOW=Instant.parse("2026-10-12T00:00:00Z");
    static final String OWNER="10000000-0000-4000-8000-000000000001";
    MongoTemplate mongo; LearningStore store;
    @BeforeAll static void connect() { client=MongoClients.create(MONGO.getReplicaSetUrl()); }
    @AfterAll static void close() { client.close(); }
    @BeforeEach void setup() {
        mongo=new MongoTemplate(client,"isolated-answer-learning");
        for(String c:List.of("learning_submissions","learning_completions","learning_submission_limits")) mongo.dropCollection(c);
        store=new LearningStore(mongo,null); store.indexes();
    }
    Submission fixture() {
        var s=new Submission(); s.id=UUID.randomUUID().toString(); s.userId=OWNER; s.lessonId="synthetic-lesson"; s.level="IH"; s.contentVersion="fixture-v1";
        s.createdAt=NOW; s.queuedAt=NOW; s.updatedAt=NOW; s.uploadSize=123;
        s.audio=new Audio("test-bucket",LearningStorage.frozenKey(s.id),"a".repeat(32),"b".repeat(64));
        s.checklist=new Document("lesson_id",s.lessonId).append("version","check-v1").append("met_threshold",.75).append("not_met_threshold",.25)
                .append("items",List.of(new Document("id","detail")));
        return s;
    }
    <T> List<T> race(int n,Supplier<T> work) throws Exception {
        try(var pool=Executors.newFixedThreadPool(n)) {
            var gate=new CountDownLatch(1); List<Future<T>> results=new ArrayList<>();
            for(int i=0;i<n;i++) results.add(pool.submit(()->{gate.await(); return work.get();})); gate.countDown();
            List<T> values=new ArrayList<>(); for(var f:results) values.add(f.get(20,TimeUnit.SECONDS)); return values;
        }
    }
    @Test void simultaneousCreationAndSubmissionAreIdempotent() throws Exception {
        var s=fixture(); race(8,()->store.create(s,60)); assertThat(mongo.count(new Query(),Submission.class)).isEqualTo(1);
        assertThat(race(8,()->store.queue(s,s.audio,NOW)).stream().filter(Objects::nonNull).count()).isEqualTo(1);
        var saved=store.owned(OWNER,s.id); assertThat(saved.status).isEqualTo("queued"); assertThat(saved.audio).isEqualTo(s.audio);
    }
    @Test void expiredLeaseCannotOverwriteAReclaimedWorkerResult() throws Exception {
        var s=fixture(); store.create(s,60); store.queue(s,s.audio,NOW);
        var claims=race(8,()->store.claim(s,NOW)); assertThat(claims.stream().filter(Objects::nonNull).count()).isEqualTo(1);
        var old=claims.stream().filter(Objects::nonNull).findFirst().orElseThrow(); var next=store.claim(s,NOW.plusSeconds(91));
        assertThat(next.leaseToken).isNotEqualTo(old.leaseToken);
        store.finish(old,new Update().set("status","completed"),NOW.plusSeconds(92),0);
        assertThat(store.find(s.id).status).isEqualTo("queued");
        store.finish(next,new Update().set("status","grading"),NOW.plusSeconds(93),0);
        assertThat(store.find(s.id).status).isEqualTo("grading");
    }
    @Test void concurrentExplicitRetriesIncrementOnlyOnceAndPreserveSnapshot() throws Exception {
        var s=fixture(); s.status="failed"; s.error=new LearningModels.Error("MODEL_FAILED","fixture",true); store.create(s,60);
        assertThat(race(8,()->store.retry(s,NOW,60)).stream().filter(Objects::nonNull).count()).isEqualTo(1);
        var next=store.find(s.id); assertThat(next.attempt).isEqualTo(2); assertThat(next.audio).isEqualTo(s.audio); assertThat(next.checklist).isEqualTo(s.checklist);
        assertThat(next.error).isNull();
    }
    @Test void terminalResultAndCompletionPersistWithoutClientPolling() {
        var s=fixture(); store.create(s,60); store.queue(s,s.audio,NOW);
        var lambda=mock(LearningLambdaClient.class); var config=new LearningProperties(); config.setGradingEnabled(true);
        var result=new Document("checklistVersion","check-v1").append("transcript","Synthetic test speech.").append("metCount",1).append("totalCount",1)
                .append("items",List.of(new Document("id","detail").append("label","내용").append("feedback","내용을 전달했어요.").append("verdict","met").append("probability",.9)));
        when(lambda.submit(any())).thenReturn(new Document("id",s.id).append("jobId",s.id+":1").append("attempt",1).append("lessonId",s.lessonId)
                .append("level",s.level).append("contentVersion",s.contentVersion).append("status","completed").append("result",result));
        new LearningWorker(store,lambda,config,Clock.fixed(NOW,ZoneOffset.UTC),new SimpleMeterRegistry()).tick();
        assertThat(store.find(s.id).status).isEqualTo("completed"); assertThat(store.find(s.id).result.getInteger("metCount")).isEqualTo(1);
        new LearningWorker(store,lambda,config,Clock.fixed(NOW.plusSeconds(3),ZoneOffset.UTC),new SimpleMeterRegistry()).tick();
        assertThat(mongo.count(new Query(),"learning_completions")).isEqualTo(1); assertThat(store.find(s.id).completionPending).isFalse();
        verify(lambda,times(1)).submit(any()); verify(lambda,never()).get(any());
    }
    @Test void repeatedConcurrentCompletionHasOneOwnerLevelVersionRow() throws Exception {
        race(8,()->{store.complete(OWNER,"lesson","IH","v1",null,NOW);return true;});
        assertThat(mongo.count(new Query(),"learning_completions")).isEqualTo(1);
        store.complete(OWNER,"lesson","AL","v1",null,NOW); assertThat(mongo.count(new Query(),"learning_completions")).isEqualTo(2);
    }
    @Test void blockedPagesDoNotStarveOtherOwnersAndAreRevisitedAfterUnblocking() {
        var blocked = new AtomicBoolean(true);
        var guards = mock(UserOwnedTransactionExecutor.class);
        when(guards.execute(anyString(),any())).thenAnswer(i -> {
            if (OWNER.equals(i.getArgument(0)) && blocked.get()) throw DeletionFailure.blocked();
            return ((Supplier<?>)i.getArgument(1)).get();
        });
        var guarded = new LearningStore(mongo,guards);
        for (int i=0;i<40;i++) {
            var s=fixture(); s.id=new UUID(0,i+1).toString(); s.status="queued";
            s.nextRunAt=NOW; s.leaseUntil=Instant.EPOCH; mongo.insert(s);
        }
        var other=fixture(); other.id="ffffffff-ffff-4fff-bfff-ffffffffffff"; other.userId="other-owner";
        other.status="queued"; other.nextRunAt=NOW; other.leaseUntil=Instant.EPOCH; mongo.insert(other);
        var lambda=mock(LearningLambdaClient.class);
        when(lambda.submit(any())).thenAnswer(i -> {
            Submission s=i.getArgument(0);
            return new Document("id",s.id).append("jobId",s.id+":1").append("attempt",1)
                    .append("lessonId",s.lessonId).append("level",s.level).append("contentVersion",s.contentVersion).append("status","no_speech");
        });
        var p=new LearningProperties(); p.setGradingEnabled(true);
        var now=new AtomicReference<>(NOW); var clock=mock(Clock.class);
        when(clock.instant()).thenAnswer(i -> now.get());
        var worker=new LearningWorker(guarded,lambda,p,clock,new SimpleMeterRegistry());
        worker.tick(); // First ten blocked candidates; the next tick must continue past them.
        var later=fixture(); later.userId="later-owner"; later.status="queued";
        later.nextRunAt=NOW.plusSeconds(1); later.leaseUntil=Instant.EPOCH; mongo.insert(later);
        now.set(NOW.plusSeconds(2));
        for(int i=0;i<4;i++) worker.tick();
        assertThat(guarded.find(other.id).dispatched).isTrue();
        assertThat(guarded.find(later.id).dispatched).isFalse(); // New arrivals wait for the bounded pass to finish.
        assertThat(mongo.count(Query.query(Criteria.where("userId").is(OWNER).and("nextRunAt").is(NOW)
                .and("leaseUntil").is(Instant.EPOCH).and("status").is("queued")),Submission.class)).isEqualTo(40);
        verify(lambda,times(1)).submit(any());
        blocked.set(false);
        for(int i=0;i<5;i++) worker.tick();
        assertThat(mongo.count(Query.query(Criteria.where("status").is("queued")),Submission.class)).isZero();
        verify(lambda,times(42)).submit(any());
    }
}
