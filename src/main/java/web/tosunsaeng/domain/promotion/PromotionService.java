package web.tosunsaeng.domain.promotion;

import java.io.IOException;
import java.nio.ByteBuffer;
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
    private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile(
            "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@"
            + "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
            + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+");
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

    public Receipt apply(String owner, String gifticon, String email, MultipartFile report) {
        try { if (!UUID.fromString(owner).toString().equals(owner)) throw PromotionFailure.invalid(); }
        catch (IllegalArgumentException | NullPointerException e) { throw PromotionFailure.invalid(); }
        guards.requirePublicWrite(owner);
        guards.requireUploadUrlAccess(owner);
        var now = clock.instant();
        if (now.isBefore(config.getOpensAt()) || !now.isBefore(config.getClosesAt()))
            throw new PromotionFailure(409,"PROMOTION_CLOSED");
        if (gifticon == null || gifticon.isBlank() || gifticon.length() > 100
                || gifticon.chars().anyMatch(Character::isISOControl) || email == null
                || email.length() > 254 || email.chars().anyMatch(Character::isISOControl))
            throw PromotionFailure.invalid();
        gifticon = gifticon.strip();
        email = email.strip();
        if (!EMAIL.matcher(email).matches() || email.indexOf('@') > 64) throw PromotionFailure.invalid();
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
                .append("gifticon",gifticon).append("email",email)
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
        if (isHeic(bytes)) return "image/heic";
        throw new PromotionFailure(415,"PROMOTION_REPORT_UNSUPPORTED");
    }

    // Inspect the bounded ISO-BMFF ftyp box, not filename/MIME or arbitrary payload text.
    // HEVC still-image brands only; generic HEIF, AVIF and video are not sufficient.
    private static boolean isHeic(byte[] bytes) {
        if (bytes.length < 16 || !new String(bytes,4,4,StandardCharsets.US_ASCII).equals("ftyp")) return false;
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long size = Integer.toUnsignedLong(buffer.getInt(0));
        int header = 8;
        if (size == 1) {
            if (bytes.length < 24) return false;
            size = buffer.getLong(8);
            header = 16;
        }
        if (size < header + 8 || size > bytes.length || (size - header - 8) % 4 != 0) return false;
        for (int offset = header; offset < size; offset += 4) {
            if (offset == header + 4) continue; // minor version is not a brand
            String brand = new String(bytes,offset,4,StandardCharsets.US_ASCII);
            if (Set.of("heic","heix","heim","heis").contains(brand)) return true;
        }
        return false;
    }
}
