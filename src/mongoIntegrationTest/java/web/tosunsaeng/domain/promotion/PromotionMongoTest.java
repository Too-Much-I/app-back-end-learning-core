package web.tosunsaeng.domain.promotion;

import com.mongodb.client.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import web.tosunsaeng.domain.usermerge.application.*;
import web.tosunsaeng.domain.usermerge.config.UserMergedProperties;
import web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Testcontainers
class PromotionMongoTest {
    @Container static final MongoDBContainer MONGO=new MongoDBContainer("mongo:7.0.14");
    static MongoClient client;
    static final String OWNER="20000000-0000-4000-8000-000000000002";
    static final Instant NOW=Instant.parse("2026-10-24T00:00:00Z");
    MongoTemplate mongo;
    PromotionService service;
    PromotionReportStorage storage;
    @BeforeAll static void connect() { client=MongoClients.create(MONGO.getReplicaSetUrl()); }
    @AfterAll static void close() { if (client!=null) client.close(); }
    @BeforeEach void setup() {
        var factory=new SimpleMongoClientDatabaseFactory(client,"isolated-promotion");
        mongo=new MongoTemplate(factory);
        for (String c:List.of(PromotionService.COLLECTION,"user_ownership_guards")) mongo.dropCollection(c);
        mongo.createCollection(PromotionService.COLLECTION);
        var beans=new StaticListableBeanFactory();
        beans.addBean("transactions",new TransactionTemplate(new MongoTransactionManager(factory)));
        var props=new UserMergedProperties(); props.setWriterEnabled(true);
        var repository=new MongoRepositoryFactory(mongo).getRepository(UserOwnershipGuardRepository.class);
        var clock=Clock.fixed(NOW,ZoneOffset.UTC);
        var guards=new UserOwnedTransactionExecutor(props,new UserOwnershipGuardService(mongo,repository),
                beans.getBeanProvider(TransactionOperations.class),clock);
        mongo.insert(UserOwnershipGuard.active(OWNER,NOW));
        var config=new PromotionProperties(); config.setCampaignId("fixture"); config.setBucket("private-fixture");
        config.setOpensAt(NOW); config.setClosesAt(NOW.plusSeconds(60));
        storage=mock(PromotionReportStorage.class);
        service=new PromotionService(mongo,storage,config,guards,clock);
    }
    String apply() {
        try { service.apply(OWNER,"커피 교환권","fixture@example.com",new MockMultipartFile("report","%PDF-fixture".getBytes())); return "saved"; }
        catch (PromotionFailure f) { return f.code; }
    }
    @Test void concurrentUploadsProduceOneApplicationAndOnlyDeleteLosingFile() throws Exception {
        var gate=new CyclicBarrier(2);
        doAnswer(i -> { gate.await(10,TimeUnit.SECONDS); return null; }).when(storage).put(anyString(),any(),anyString());
        try (var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(this::apply); var b=pool.submit(this::apply);
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("saved","PROMOTION_ALREADY_APPLIED");
        }
        assertThat(mongo.count(new Query(),PromotionService.COLLECTION)).isEqualTo(1);
        Document row=mongo.findAll(Document.class,PromotionService.COLLECTION).getFirst();
        var key=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(storage).delete(key.capture());
        assertThat(key.getValue()).isNotEqualTo(row.getString("reportKey"));
        assertThat(row).containsEntry("gifticon","커피 교환권").containsEntry("email","fixture@example.com")
                .doesNotContainKeys("bankName","accountNumber","accountHolder");
    }
}
