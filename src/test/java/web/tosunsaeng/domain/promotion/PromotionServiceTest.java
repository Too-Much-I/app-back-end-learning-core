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
    PromotionService.Receipt apply() { return service.apply(OWNER," 커피 교환권 "," fixture+gift@example.com ",report()); }
    @Test void gifticonRejectsBlankControlAndOversizeWithoutUpload() {
        for (String name: new String[]{null,"","  ","커피\n쿠폰","가".repeat(101)})
            assertThatThrownBy(() -> service.apply(OWNER,name,"fixture@example.com",report())).isInstanceOf(PromotionFailure.class);
        verifyNoInteractions(storage);
    }
    @Test void emailRejectsMalformedControlAndOversizeWithoutUpload() {
        for (String email: new String[]{null,"","  ","not-email","a@","a@localhost","a b@example.com",
                "a..b@example.com","a@-example.com","a@example.com\n","a".repeat(65)+"@example.com","a".repeat(255)})
            assertThatThrownBy(() -> service.apply(OWNER,"커피",email,report())).isInstanceOf(PromotionFailure.class);
        verifyNoInteractions(storage);
        verify(mongo,never()).insert(any(Document.class),anyString());
    }
    @Test void persistsOwnerCampaignGiftAndEmailWithoutBankFields() {
        var receipt=apply(); var rows=org.mockito.ArgumentCaptor.forClass(Document.class);
        verify(mongo).insert(rows.capture(),eq(PromotionService.COLLECTION));
        assertThat(rows.getValue()).containsEntry("userId",OWNER).containsEntry("promotionId","score-test")
                .containsEntry("email","fixture+gift@example.com").containsEntry("applicationId",receipt.applicationId())
                .containsEntry("reportType","application/pdf").containsEntry("gifticon","커피 교환권")
                .doesNotContainKeys("originalFilename","bankCode","bankName","accountNumber","accountHolder");
        verify(storage).put(eq("promotion-reports/"+receipt.applicationId()),any(),eq("application/pdf"));
        verify(guards).execute(eq(OWNER),any());
    }
    @Test void fourOccupiedSlotsRejectWithoutUpload() {
        when(mongo.exists(any(Query.class),eq(PromotionService.COLLECTION))).thenReturn(true);
        assertThatThrownBy(this::apply).isInstanceOfSatisfying(PromotionFailure.class,f -> assertThat(f.code).isEqualTo("PROMOTION_ALREADY_APPLIED"));
        verifyNoInteractions(storage);
    }
    @Test void existingApplicationUsesSecondSlotAndKeepsLegacyKey() {
        when(mongo.exists(any(Query.class),eq(PromotionService.COLLECTION))).thenReturn(true,false);
        apply();
        var row=org.mockito.ArgumentCaptor.forClass(Document.class);
        verify(mongo).insert(row.capture(),eq(PromotionService.COLLECTION));
        String legacy=PromotionService.applicationKey("score-test",OWNER);
        assertThat(PromotionService.slotKey(legacy,1)).isEqualTo(legacy);
        assertThat(row.getValue().getString("_id")).isEqualTo(legacy+":2");
    }
    @Test void occupiedSlotRaceContinuesWithoutUploadingAgain() {
        when(mongo.insert(any(Document.class),eq(PromotionService.COLLECTION)))
                .thenThrow(new DuplicateKeyException("fixture")).thenAnswer(i -> i.getArgument(0));
        when(mongo.findById(anyString(),eq(Document.class),eq(PromotionService.COLLECTION)))
                .thenReturn(new Document("applicationId","winner"));
        assertThat(apply().applicationId()).isNotBlank();
        verify(storage,times(1)).put(anyString(),any(),anyString());
        verify(storage,never()).delete(anyString());
        verify(guards,times(2)).execute(eq(OWNER),any());
    }
    @Test void ambiguousDuplicateDoesNotAdvanceOrDelete() {
        when(mongo.insert(any(Document.class),eq(PromotionService.COLLECTION)))
                .thenThrow(new DuplicateKeyException("fixture"));
        assertThatThrownBy(this::apply).isInstanceOfSatisfying(PromotionFailure.class,
                f -> assertThat(f.code).isEqualTo("PROMOTION_UNAVAILABLE"));
        verify(mongo,times(1)).insert(any(Document.class),eq(PromotionService.COLLECTION));
        verify(storage,never()).delete(anyString());
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
    @Test void receptionBoundaries() {
        config.setClosesAt(now);
        assertThatThrownBy(this::apply).isInstanceOfSatisfying(PromotionFailure.class,f -> assertThat(f.code).isEqualTo("PROMOTION_CLOSED"));
        config.setClosesAt(now.plusSeconds(60)); config.setOpensAt(now.plusSeconds(1));
        assertThatThrownBy(this::apply).isInstanceOf(PromotionFailure.class);
        config.setOpensAt(now);
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

    static byte[] ftyp(String major, String... compatible) {
        var bytes=java.nio.ByteBuffer.allocate(16+compatible.length*4);
        bytes.putInt(bytes.capacity()).put("ftyp".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        bytes.put(major.getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(0);
        for (String brand:compatible) bytes.put(brand.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return bytes.array();
    }
    @Test void heicUsesMajorOrCompatibleBrandsAndStoresOriginalBytes() {
        for (String brand:new String[]{"heic","heix","heim","heis"}) {
            assertThat(PromotionService.fileType(ftyp(brand))).isEqualTo("image/heic");
            assertThat(PromotionService.fileType(ftyp("mif1",brand))).isEqualTo("image/heic");
        }
        byte[] bytes=ftyp("mif1","heic");
        var receipt=service.apply(OWNER,"커피","fixture@example.com",
                new MockMultipartFile("report","untrusted.bin","application/octet-stream",bytes));
        verify(storage).put(eq("promotion-reports/"+receipt.applicationId()),eq(bytes),eq("image/heic"));
        var row=org.mockito.ArgumentCaptor.forClass(Document.class);
        verify(mongo).insert(row.capture(),eq(PromotionService.COLLECTION));
        assertThat(row.getValue()).containsEntry("reportType","image/heic");
        var extended=java.nio.ByteBuffer.allocate(24).putInt(1).put("ftyp".getBytes())
                .putLong(24).put("heic".getBytes()).putInt(0).array();
        assertThat(PromotionService.fileType(extended)).isEqualTo("image/heic");
    }
    @Test void malformedAndUnrelatedContainersAreRejected() {
        byte[] oversized=ftyp("heic"); java.nio.ByteBuffer.wrap(oversized).putInt(1000);
        byte[] zeroSize=ftyp("heic"); java.nio.ByteBuffer.wrap(zeroSize).putInt(0);
        byte[] minorOnly=ftyp("mif1"); System.arraycopy("heic".getBytes(),0,minorOnly,12,4);
        byte[] trailing=java.util.Arrays.copyOf(ftyp("mif1"),20); System.arraycopy("heic".getBytes(),0,trailing,16,4);
        byte[] misaligned=java.util.Arrays.copyOf(ftyp("heic"),17); java.nio.ByteBuffer.wrap(misaligned).putInt(17);
        for (byte[] bytes:new byte[][]{ftyp("avif"),ftyp("mp42"),ftyp("mif1"),ftyp("hevc"),
                oversized,zeroSize,minorOnly,trailing,misaligned,java.util.Arrays.copyOf(ftyp("heic"),12)})
            assertThatThrownBy(() -> PromotionService.fileType(bytes)).isInstanceOfSatisfying(PromotionFailure.class,
                    f -> assertThat(f.status).isEqualTo(415));
        assertThatThrownBy(() -> service.apply(OWNER,"커피","fixture@example.com",
                new MockMultipartFile("report","spoof.heic","image/heic","not heic".getBytes())))
                .isInstanceOf(PromotionFailure.class);
        verifyNoInteractions(storage);
    }
}
