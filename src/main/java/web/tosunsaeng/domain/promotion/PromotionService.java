package web.tosunsaeng.domain.promotion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.*;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.multipart.MultipartFile;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;

public class PromotionService {
    static final String COLLECTION = "promotion_applications";
    // Below the existing 10 MiB multipart request cap, leaving room for form overhead.
    static final int MAX_BYTES = 9 * 1024 * 1024;
    private final MongoTemplate mongo;
    private final PromotionReportStorage storage;
    private final PromotionProperties config;
    private final UserOwnedTransactionExecutor guards;
    private final Clock clock;
    public PromotionService(MongoTemplate mongo, PromotionReportStorage storage, PromotionProperties config,
            UserOwnedTransactionExecutor guards, Clock clock) {
        this.mongo=mongo; this.storage=storage; this.config=config; this.guards=guards; this.clock=clock;
    }
    public record Receipt(String applicationId) {}

    public Receipt apply(String owner, String bankName, String accountNumber, String accountHolder, MultipartFile report) {
        try { if (!UUID.fromString(owner).toString().equals(owner)) throw PromotionFailure.invalid(); }
        catch (IllegalArgumentException | NullPointerException e) { throw PromotionFailure.invalid(); }
        guards.requirePublicWrite(owner);
        guards.requireUploadUrlAccess(owner);
        var now = clock.instant();
        if (now.isBefore(config.getOpensAt()) || !now.isBefore(config.getClosesAt()))
            throw new PromotionFailure(409,"PROMOTION_CLOSED");
        if (bankName == null || bankName.isBlank() || bankName.length() > 100
                || bankName.chars().anyMatch(Character::isISOControl) || accountNumber == null
                || !accountNumber.matches("[0-9]{1,32}") || accountHolder == null
                || accountHolder.isBlank() || accountHolder.length() > 100
                || accountHolder.chars().anyMatch(Character::isISOControl)) throw PromotionFailure.invalid();
        String id = applicationKey(config.getCampaignId(), owner);
        if (mongo.exists(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("_id").is(id)), COLLECTION))
            throw new PromotionFailure(409,"PROMOTION_ALREADY_APPLIED");
        byte[] bytes = read(report);
        String type = fileType(bytes);
        String receipt = UUID.randomUUID().toString();
        String key = "promotion-reports/" + receipt;
        try { storage.put(key, bytes, type); }
        catch (RuntimeException e) { throw PromotionFailure.unavailable(); }
        Document row = new Document("_id",id).append("applicationId",receipt)
                .append("promotionId",config.getCampaignId()).append("userId",owner)
                .append("bankName",bankName.trim()).append("accountNumber",accountNumber).append("accountHolder",accountHolder.trim())
                .append("reportKey",key).append("reportBucket",config.getBucket()).append("reportType",type)
                .append("reportBytes",bytes.length).append("createdAt",Date.from(now));
        try {
            guards.execute(owner, () -> mongo.insert(row,COLLECTION));
        } catch (DuplicateKeyException e) {
            // Read the winner before cleanup: a prior unknown commit may reference this object.
            Document winner = mongo.findById(id,Document.class,COLLECTION);
            if (winner != null && receipt.equals(winner.getString("applicationId"))) return new Receipt(receipt);
            if (winner != null) {
                try { storage.delete(key); } catch (RuntimeException ignored) { /* private orphan: manual cleanup */ }
                throw new PromotionFailure(409,"PROMOTION_ALREADY_APPLIED");
            }
            throw PromotionFailure.unavailable();
        } catch (web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardException
                | web.tosunsaeng.domain.learningrecorddeletion.application.DeletionFailure e) {
            try { storage.delete(key); } catch (RuntimeException ignored) { /* private orphan: manual cleanup */ }
            throw e;
        } catch (RuntimeException e) {
            // Unknown commit is not proof of failure. Never delete a possibly referenced report.
            throw PromotionFailure.unavailable();
        }
        return new Receipt(receipt);
    }
    static String applicationKey(String campaign, String owner) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((campaign + "\n" + owner).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static byte[] read(MultipartFile report) {
        if (report == null || report.isEmpty()) throw PromotionFailure.invalid();
        if (report.getSize() > MAX_BYTES) throw new PromotionFailure(413,"PROMOTION_REPORT_TOO_LARGE");
        try (var input = report.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new PromotionFailure(413,"PROMOTION_REPORT_TOO_LARGE");
            if (bytes.length == 0) throw PromotionFailure.invalid();
            return bytes;
        } catch (IOException e) { throw PromotionFailure.invalid(); }
    }
    static String fileType(byte[] bytes) {
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes,8), new byte[]{(byte)137,80,78,71,13,10,26,10})) return "image/png";
        if (bytes.length >= 3 && bytes[0]==(byte)255 && bytes[1]==(byte)216 && bytes[2]==(byte)255) return "image/jpeg";
        if (bytes.length >= 5 && new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-")) return "application/pdf";
        throw new PromotionFailure(415,"PROMOTION_REPORT_UNSUPPORTED");
    }
}
