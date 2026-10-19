package web.tosunsaeng.domain.answerlearning;

import org.bson.Document;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import java.util.Map;
import static web.tosunsaeng.domain.answerlearning.LearningModels.*;

public class LearningStorage {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private final S3Client s3;
    private final S3Presigner signer;
    private final LearningProperties config;
    private final Clock clock;
    public LearningStorage(S3Client s3, S3Presigner signer, LearningProperties config, Clock clock) {
        this.s3 = s3; this.signer = signer; this.config = config; this.clock = clock;
    }
    static String stagingKey(String id) { return "answer-learning-uploads/" + id + "/recording.m4a"; }
    static String frozenKey(String id) { return "answer-learning-submissions/" + id + "/recording.m4a"; }
    public UploadUrl upload(Submission s) {
        // Expiry is tied to the guarded issuance write, not a potentially delayed HTTP response.
        Instant expires = s.updatedAt.plusSeconds(300);
        Duration remaining = Duration.between(clock.instant(), expires);
        if (remaining.isNegative() || remaining.isZero()) throw new LearningFailure(409, "UPLOAD_CLOSED");
        var url = signer.presignPutObject(PutObjectPresignRequest.builder().signatureDuration(remaining)
                .putObjectRequest(PutObjectRequest.builder().bucket(config.getBucket()).key(stagingKey(s.id))
                        .contentType("audio/mp4").contentLength(s.uploadSize).build()).build());
        return new UploadUrl(s.id, url.url().toString(), expires, MAX_BYTES);
    }
    public Document media(Document row) {
        String key = row.getString("key");
        if (!config.getBucket().equals(row.getString("bucket")) || key == null
                || !key.startsWith("answer-learning/" + config.getDatasetId() + "/" + config.getContentVersion() + "/media/"))
            throw LearningFailure.unavailable();
        var get = GetObjectRequest.builder().bucket(config.getBucket()).key(key);
        if (row.getString("versionId") != null) get.versionId(row.getString("versionId"));
        String url = signer.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(Duration.ofHours(1))
                .getObjectRequest(get.build()).build()).url().toString();
        Document wire = new Document("id", row.getString("_id")).append("url", url)
                .append("expiresAt", clock.instant().plusSeconds(3600).toString());
        if (row.containsKey("width")) { wire.put("width", row.get("width")); wire.put("height", row.get("height")); }
        return wire;
    }
    /** PUT URLs target staging only. Conditional create makes the worker's separate key immutable. */
    public Audio freeze(Submission s) {
        Audio existing = existingFrozen(s.id);
        if (existing != null) return existing;
        if (s.uploadSize < 1 || s.uploadSize > MAX_BYTES) throw new LearningFailure(409, "AUDIO_NOT_UPLOADED");
        try {
            HeadObjectResponse head = head(stagingKey(s.id));
            check(head.contentType(), head.contentLength(), s.uploadSize);
            byte[] bytes;
            try (var stream = s3.getObject(GetObjectRequest.builder().bucket(config.getBucket()).key(stagingKey(s.id)).ifMatch(head.eTag())
                    .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(20))).build())) {
                try {
                    bytes = stream.readNBytes(MAX_BYTES + 1);
                    check(stream.response().contentType(), (long)bytes.length, s.uploadSize);
                } catch (RuntimeException | IOException e) { stream.abort(); throw e; }
            }
            String hash = sha(bytes);
            // A writer cannot resume after the deletion worker's capability drain window.
            if (s.externalWriteUntil == null || !clock.instant().isBefore(s.externalWriteUntil.minusSeconds(20)))
                throw new LearningFailure(409, "UPLOAD_CLOSED");
            try {
                var put = s3.putObject(PutObjectRequest.builder().bucket(config.getBucket()).key(frozenKey(s.id)).ifNoneMatch("*")
                        .contentType("audio/mp4").serverSideEncryption(ServerSideEncryption.AES256)
                        .metadata(Map.of("sha256", hash)).overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(20))).build(), RequestBody.fromBytes(bytes));
                return new Audio(config.getBucket(), frozenKey(s.id), cleanEtag(put.eTag()), hash);
            } catch (S3Exception e) {
                if (e.statusCode() != 412 && e.statusCode() != 409) throw e;
                Audio winner = existingFrozen(s.id);
                if (winner == null) throw LearningFailure.unavailable();
                return winner;
            }
        } catch (S3Exception e) {
            if (e.statusCode() == 404) throw new LearningFailure(409, "AUDIO_NOT_UPLOADED");
            if (e.statusCode() == 412) throw new LearningFailure(409, "AUDIO_CHANGED");
            throw LearningFailure.unavailable();
        } catch (IOException e) { throw LearningFailure.unavailable(); }
    }
    private HeadObjectResponse head(String key) {
        return s3.headObject(HeadObjectRequest.builder().bucket(config.getBucket()).key(key)
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(10))).build());
    }
    private Audio existingFrozen(String id) {
        try {
            var h = head(frozenKey(id));
            String sha = h.metadata().get("sha256");
            if (sha == null || !sha.matches("[a-f0-9]{64}")) throw LearningFailure.unavailable();
            check(h.contentType(), h.contentLength(), h.contentLength());
            return new Audio(config.getBucket(), frozenKey(id), cleanEtag(h.eTag()), sha);
        } catch (S3Exception e) {
            // Without ListBucket, HEAD of an absent key is 403. Do not grant bucket listing:
            // proceed to the conditional create, which can never replace an existing file.
            if (e.statusCode() == 404 || e.statusCode() == 403) return null;
            throw LearningFailure.unavailable();
        }
    }
    static void check(String type, Long size, long expected) {
        if (!"audio/mp4".equals(type)) throw new LearningFailure(415, "INVALID_AUDIO");
        if (size == null || size < 1 || size > MAX_BYTES || size != expected) throw new LearningFailure(422, "INVALID_AUDIO");
    }
    static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private static String cleanEtag(String etag) { return etag.replace("\"", ""); }
}
