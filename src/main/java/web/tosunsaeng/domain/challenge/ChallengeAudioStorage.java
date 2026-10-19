package web.tosunsaeng.domain.challenge;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import static web.tosunsaeng.domain.challenge.ChallengeModels.Attempt;

public class ChallengeAudioStorage {
    public static final int MAX_BYTES = 2_097_152;
    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    public ChallengeAudioStorage(S3Client s3, S3Presigner presigner, String bucket) {
        this.s3 = s3; this.presigner = presigner; this.bucket = bucket;
    }
    public ChallengeViews.Upload upload(Attempt a, Instant now) {
        Duration remaining = Duration.between(now, a.submissionDeadlineAt);
        if (remaining.isNegative() || remaining.isZero()) throw new ChallengeFailure(410, "CHALLENGE_ATTEMPT_EXPIRED");
        Duration ttl = remaining.compareTo(Duration.ofMinutes(5)) < 0 ? remaining : Duration.ofMinutes(5);
        String url = presigner.presignPutObject(PutObjectPresignRequest.builder().signatureDuration(ttl)
                .putObjectRequest(PutObjectRequest.builder().bucket(bucket).key(a.uploadKey).contentType("audio/mp4").build()).build()).url().toString();
        return new ChallengeViews.Upload("PUT", url, now.plus(ttl), "audio/mp4", MAX_BYTES);
    }
    public void validate(String key) {
        try {
            HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key)
                    .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(10))).build());
            metadata(head.contentType(), head.contentLength());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) throw new ChallengeFailure(409, "CHALLENGE_AUDIO_NOT_UPLOADED");
            throw ChallengeFailure.internal();
        } catch (SdkException e) { throw ChallengeFailure.internal(); }
    }
    public byte[] read(String key) {
        try (ResponseInputStream<GetObjectResponse> stream = s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key)
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(10))).build())) {
            try {
                metadata(stream.response().contentType(), stream.response().contentLength());
                byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
                metadata("audio/mp4", (long) bytes.length);
                return bytes;
            } catch (RuntimeException | IOException failure) {
                // Apache S3 streams may drain the whole object on close. Abort rejected bodies instead.
                stream.abort(); throw failure;
            }
        } catch (IOException | SdkException e) { throw ChallengeFailure.internal(); }
    }
    static void metadata(String type, Long size) {
        if (!"audio/mp4".equals(type)) throw new ChallengeFailure(415, "CHALLENGE_AUDIO_FORMAT_UNSUPPORTED");
        if (size == null || size <= 0) throw new ChallengeFailure(409, "CHALLENGE_AUDIO_NOT_UPLOADED");
        if (size > MAX_BYTES) throw new ChallengeFailure(413, "CHALLENGE_AUDIO_TOO_LARGE");
    }
}
