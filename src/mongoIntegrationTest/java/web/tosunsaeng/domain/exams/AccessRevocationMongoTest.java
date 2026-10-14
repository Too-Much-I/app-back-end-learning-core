package web.tosunsaeng.domain.exams;

import com.mongodb.client.*;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import web.tosunsaeng.domain.exams.access.*;
import web.tosunsaeng.domain.exams.application.ExamAcceptedWorkPolicy;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class AccessRevocationMongoTest {
    @Container static final MongoDBContainer MONGO=new MongoDBContainer("mongo:7.0.14");
    static MongoClient client;
    MongoTemplate mongo; AccessRevocationStore store;
    static final String USER="30000000-0000-4000-8000-000000000001", GROUP="30000000-0000-4000-8000-000000000002";
    static final Instant NOW=Instant.parse("2026-10-14T00:00:00Z");
    @BeforeAll static void connect(){client=MongoClients.create(MONGO.getReplicaSetUrl());}
    @AfterAll static void close(){client.close();}
    @BeforeEach void setup(){
        mongo=new MongoTemplate(new SimpleMongoClientDatabaseFactory(client,"isolated-revoke-"+UUID.randomUUID()));
        for(String c:List.of(AccessRevocationStore.COLLECTION,"exam_sessions","user_ownership_guards")) mongo.createCollection(c);
        indexes(); store=new AccessRevocationStore(mongo,"test","fixture-test",Clock.fixed(NOW,ZoneOffset.UTC)); store.validateIndexes();
        mongo.insert(new Document("_id",USER).append("state","ACTIVE").append("revision",0L),"user_ownership_guards");
    }
    @AfterEach void drop(){mongo.getDb().drop();}
    void indexes(){
        mongo.getCollection(AccessRevocationStore.COLLECTION).createIndex(AccessRevocationStore.GROUP_INDEX,new IndexOptions().unique(true).name("uq_paid_revoke_group"));
        mongo.getCollection(AccessRevocationStore.COLLECTION).createIndex(AccessRevocationStore.EVENT_INDEX,new IndexOptions().unique(true).name("uq_paid_revoke_event"));
    }
    AccessRevocationEvent event(String id,String group,String protectedId) {
        String raw="{\"eventId\":\""+id+"\",\"eventType\":\"AttemptGroupAccessRevoked\",\"schemaVersion\":1,\"producer\":\"billing\","
                +"\"consumerScopeId\":\"fixture-test\",\"occurredAt\":\"2026-10-14T00:00:00.000Z\",\"userId\":\""+USER+"\","
                +"\"attemptGroupId\":\""+group+"\",\"reason\":\"PURCHASE_REFUNDED\",\"confirmedSessionId\":"
                +(protectedId==null?"null":AccessEventJson.quote(protectedId))+"}";
        return AccessRevocationEvent.decode(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8),"fixture-test");
    }
    void session(String id,String group){mongo.insert(ExamSession.builder().examId(id).userId(USER).attemptGroupId(group)
            .active(true).status(ExamSessionStatus.IN_PROGRESS).build());}
    Document fact(){return mongo.findOne(new Query(),Document.class,AccessRevocationStore.COLLECTION);}
    @Test void firstAtomicDenyAndDuplicateLeaveOriginalFactAndExceptionUnchanged(){
        session("protected",GROUP); session("other",GROUP); session("another-right","another-group");
        var e=event(UUID.randomUUID().toString(),GROUP,"protected"); store.accept(e);
        var original=fact(); store.accept(e);
        assertThat(fact()).isEqualTo(original);
        assertThat(mongo.findById("protected",ExamSession.class).isAccessRevoked()).isFalse();
        assertThat(mongo.findById("other",ExamSession.class).isAccessRevoked()).isTrue();
        assertThat(ExamAcceptedWorkPolicy.processable(mongo.findById("other",ExamSession.class))).isFalse();
        assertThat(mongo.findById("another-right",ExamSession.class).isAccessRevoked()).isFalse();
        assertThat(store.denied(GROUP,"protected",USER)).isFalse();
        assertThat(store.denied(GROUP,"later",USER)).isTrue();
        assertThat(store.denied(GROUP,"protected","different-owner")).isTrue();
    }
    @Test void eventConflictPrecedesGroupConflictAndCannotChangeProtectedNull(){
        String id=UUID.randomUUID().toString(); store.accept(event(id,GROUP,null));
        assertThatThrownBy(()->store.accept(event(id,GROUP,"new"))).isInstanceOfSatisfying(AccessEventFailure.class,e->assertThat(e.code()).isEqualTo("EVENT_ID_CONFLICT"));
        assertThatThrownBy(()->store.accept(event(UUID.randomUUID().toString(),GROUP,null))).isInstanceOfSatisfying(AccessEventFailure.class,e->assertThat(e.code()).isEqualTo("GROUP_REVOKE_CONFLICT"));
        assertThatThrownBy(()->store.accept(event(id,UUID.randomUUID().toString(),null))).isInstanceOfSatisfying(AccessEventFailure.class,e->assertThat(e.code()).isEqualTo("EVENT_ID_CONFLICT"));
        assertThat(fact().get("confirmedSessionId")).isNull();
    }
    @Test void concurrentSameEventHasOneFactAndBothAcknowledge() throws Exception {
        var e=event(UUID.randomUUID().toString(),GROUP,null); var gate=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->{gate.await();return receive(e);});
            var b=pool.submit(()->{gate.await();return receive(e);}); gate.countDown();
            int first=a.get(15,TimeUnit.SECONDS),second=b.get(15,TimeUnit.SECONDS);
            assertThat(first==503?receive(e):first).isEqualTo(204);
            assertThat(second==503?receive(e):second).isEqualTo(204);
            assertThat(mongo.count(new Query(),AccessRevocationStore.COLLECTION)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void concurrentDifferentEventsHaveOneWinnerAndOneConflict() throws Exception {
        var gate=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(2);
        var firstEvent=event(UUID.randomUUID().toString(),GROUP,null);
        var secondEvent=event(UUID.randomUUID().toString(),GROUP,null);
        try {
            var a=pool.submit(()->{gate.await();return receive(firstEvent);});
            var b=pool.submit(()->{gate.await();return receive(secondEvent);});gate.countDown();
            int first=a.get(15,TimeUnit.SECONDS),second=b.get(15,TimeUnit.SECONDS);
            assertThat(List.of(first==503?receive(firstEvent):first,second==503?receive(secondEvent):second))
                    .containsExactlyInAnyOrder(204,409);
            assertThat(mongo.count(new Query(),AccessRevocationStore.COLLECTION)).isEqualTo(1);
        }
        finally{pool.shutdownNow();}
    }
    private int receive(AccessRevocationEvent event) {
        try {store.accept(event);return 204;}
        catch(AccessEventFailure failure) {
            if(failure.status()==503) assertThat(failure.code()).isEqualTo("LC_TEMPORARILY_UNAVAILABLE");
            else {
                assertThat(failure.status()).isEqualTo(409);
                assertThat(failure.code()).isEqualTo("GROUP_REVOKE_CONFLICT");
            }
            return failure.status();
        }
    }
    @Test void sessionWriteFailureRollsBackFactAndOwnerTouch(){
        session("blocked",GROUP);
        mongo.executeCommand(new Document("collMod","exam_sessions").append("validator",new Document("accessRevoked",new Document("$ne",true))));
        assertThatThrownBy(()->store.accept(event(UUID.randomUUID().toString(),GROUP,null))).isInstanceOf(AccessEventFailure.class);
        assertThat(fact()).isNull(); assertThat(mongo.findById("blocked",ExamSession.class).isAccessRevoked()).isFalse();
        assertThat(mongo.findById(USER,Document.class,"user_ownership_guards").getLong("revision")).isZero();
    }
    @Test void deletedUserAndSessionAreNeverRecreated(){
        mongo.remove(new Query(),"user_ownership_guards");
        store.accept(event(UUID.randomUUID().toString(),GROUP,"deleted"));
        assertThat(mongo.count(new Query(),"user_ownership_guards")).isZero();
        assertThat(mongo.count(new Query(),"exam_sessions")).isZero();
        assertThat(fact()).doesNotContainKey("expiresAt");
    }
    @Test void missingIndexOrUnsafeTtlFailsClosed(){
        mongo.getCollection(AccessRevocationStore.COLLECTION).dropIndex("uq_paid_revoke_event");
        var unready=new AccessRevocationStore(mongo,"test","fixture-test",Clock.systemUTC());
        assertThatThrownBy(unready::validateIndexes).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->unready.accept(event(UUID.randomUUID().toString(),GROUP,null))).isInstanceOf(AccessEventFailure.class);
        indexes(); mongo.getCollection(AccessRevocationStore.COLLECTION).createIndex(new Document("firstObservedAt",1),new IndexOptions().expireAfter(120L,TimeUnit.DAYS));
        assertThatThrownBy(unready::validateIndexes).isInstanceOf(IllegalStateException.class);
    }
    @Test void originalSignedFixtureThroughProductionFilterCommitsBefore204() throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var root=java.nio.file.Path.of("src/test/resources/contracts/lc-billing-paid-exam-r2-fixtures-2");
        var keys=json.readTree(java.nio.file.Files.readString(root.resolve("public-jwks.json"))).get("current").toString();
        var http=json.readTree(java.nio.file.Files.readString(root.resolve("http-cases.json"))).get(0);
        byte[] bytes=null;
        for(var c:json.readTree(java.nio.file.Files.readString(root.resolve("canonical-cases.json"))))
            if(c.path("kind").asText().equals("event")) {bytes=Base64.getDecoder().decode(c.get("inputBase64").asText());break;}
        assertThat(bytes).isNotNull();
        var filter=new AccessEventFilter(true,8443,"fixture-test",new AccessWorkloadVerifier("test",keys,Clock.fixed(NOW,ZoneOffset.UTC)),store::accept);
        for(int replay=0;replay<2;replay++) {
            var request=new org.springframework.mock.web.MockHttpServletRequest("POST",AccessEventFilter.ROUTE);
            request.setLocalPort(8443);request.addHeader("Authorization",http.get("authorization").asText());request.setContent(bytes);
            var response=new org.springframework.mock.web.MockHttpServletResponse();filter.doFilter(request,response,(a,b)->{throw new AssertionError();});
            assertThat(response.getStatus()).isEqualTo(204);assertThat(response.getContentAsByteArray()).isEmpty();
            assertThat(mongo.count(new Query(),AccessRevocationStore.COLLECTION)).isEqualTo(1);
        }
    }
}
