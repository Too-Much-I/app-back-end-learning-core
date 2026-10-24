package web.tosunsaeng.domain.promotion;

import java.time.*;
import java.util.function.Supplier;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mock.web.MockMultipartFile;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PromotionServiceTest {
    static final String OWNER="11111111-1111-4111-8111-111111111111";
    MongoTemplate mongo=mock(MongoTemplate.class);
    PromotionReportStorage storage=mock(PromotionReportStorage.class);
    UserOwnedTransactionExecutor guards=mock(UserOwnedTransactionExecutor.class);
    PromotionProperties config=new PromotionProperties();
    Instant now=Instant.parse("2026-10-24T00:00:00Z");
    PromotionService service;
    @BeforeEach void setup() {
        config.setCampaignId("score-test"); config.setBucket("private-fixture");
        config.setOpensAt(now); config.setClosesAt(now.plusSeconds(60));
        when(guards.execute(eq(OWNER),any())).thenAnswer(i -> ((Supplier<?>)i.getArgument(1)).get());
        service=new PromotionService(mongo,storage,config,guards,Clock.fixed(now,ZoneOffset.UTC));
    }
    MockMultipartFile report() { return new MockMultipartFile("report","untrusted.pdf","text/plain","%PDF-1.7\nfixture".getBytes()); }
    PromotionService.Receipt apply() { return service.apply(OWNER,"004","001234","Fixture",report()); }
    @Test void persistsOwnerAndCampaignAndPreservesLeadingZeros() {
        var receipt=apply(); var rows=org.mockito.ArgumentCaptor.forClass(Document.class);
        verify(mongo).insert(rows.capture(),eq(PromotionService.COLLECTION));
        assertThat(rows.getValue()).containsEntry("userId",OWNER).containsEntry("promotionId","score-test")
                .containsEntry("accountNumber","001234").containsEntry("applicationId",receipt.applicationId())
                .containsEntry("reportType","application/pdf").doesNotContainKeys("originalFilename");
        verify(storage).put(eq("promotion-reports/"+receipt.applicationId()),any(),eq("application/pdf"));
        verify(guards).execute(eq(OWNER),any());
    }
    @Test void duplicatePrecheckDoesNotUpload() {
        when(mongo.exists(any(Query.class),eq(PromotionService.COLLECTION))).thenReturn(true);
        assertThatThrownBy(this::apply).isInstanceOfSatisfying(PromotionFailure.class,f -> assertThat(f.code).isEqualTo("PROMOTION_ALREADY_APPLIED"));
        verifyNoInteractions(storage);
    }
    @Test void raceLosingObjectIsCleanedButUnknownCommitIsNot() {
        when(mongo.insert(any(Document.class),eq(PromotionService.COLLECTION))).thenThrow(new DuplicateKeyException("fixture"));
        when(mongo.findById(anyString(),eq(Document.class),eq(PromotionService.COLLECTION))).thenReturn(new Document("applicationId","winner"));
        assertThatThrownBy(this::apply).isInstanceOf(PromotionFailure.class); verify(storage).delete(anyString());
        reset(storage);
        when(mongo.insert(any(Document.class),eq(PromotionService.COLLECTION))).thenThrow(new RuntimeException("unknown commit private data"));
        assertThatThrownBy(this::apply).isInstanceOfSatisfying(PromotionFailure.class,f -> assertThat(f.getMessage()).isEqualTo("PROMOTION_UNAVAILABLE"));
        verify(storage,never()).delete(anyString());
    }
    @Test void receptionBoundariesAndInvalidAccount() {
        config.setClosesAt(now);
        assertThatThrownBy(this::apply).isInstanceOfSatisfying(PromotionFailure.class,f -> assertThat(f.code).isEqualTo("PROMOTION_CLOSED"));
        config.setClosesAt(now.plusSeconds(60)); config.setOpensAt(now.plusSeconds(1));
        assertThatThrownBy(this::apply).isInstanceOf(PromotionFailure.class);
        config.setOpensAt(now);
        assertThatThrownBy(() -> service.apply(OWNER,"004","12-34","Fixture",report())).isInstanceOf(PromotionFailure.class);
        verifyNoInteractions(storage);
    }
    @Test void fileTypesSizesAndSpoofing() {
        assertThat(PromotionService.fileType(new byte[]{(byte)255,(byte)216,(byte)255,0})).isEqualTo("image/jpeg");
        assertThat(PromotionService.fileType(new byte[]{(byte)137,80,78,71,13,10,26,10})).isEqualTo("image/png");
        assertThatThrownBy(() -> PromotionService.fileType("not a pdf".getBytes())).isInstanceOf(PromotionFailure.class);
        assertThatThrownBy(() -> PromotionService.read(new MockMultipartFile("report",new byte[PromotionService.MAX_BYTES+1])))
                .isInstanceOfSatisfying(PromotionFailure.class,f -> assertThat(f.status).isEqualTo(413));
        assertThat(PromotionService.read(new MockMultipartFile("report",new byte[PromotionService.MAX_BYTES]))).hasSize(PromotionService.MAX_BYTES);
        assertThatThrownBy(() -> PromotionService.read(new MockMultipartFile("report",new byte[0]))).isInstanceOf(PromotionFailure.class);
    }
    @Test void privateStorageFailureDoesNotWriteDbAndConfigurationFailsClosed() {
        doThrow(new RuntimeException("private provider body")).when(storage).put(anyString(),any(),anyString());
        assertThatThrownBy(this::apply).isInstanceOf(PromotionFailure.class);
        verify(mongo,never()).insert(any(Document.class),anyString());
        config.validate(); config.setClosesAt(null);
        assertThatThrownBy(config::validate).isInstanceOf(IllegalStateException.class);
    }
}
