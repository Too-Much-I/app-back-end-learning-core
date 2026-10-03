package web.tosunsaeng.domain.learningrecorddeletion.infrastructure;

import org.springframework.data.redis.core.RedisTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import web.tosunsaeng.domain.learningrecorddeletion.domain.DeletionTarget;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** No wildcard keys, no FLUSHDB, no bucket mutation, no arbitrary caller-supplied prefix. */
public final class S3RedisDeletionStorage implements DeletionStorage {
    private final S3Client s3;
    private final RedisTemplate<String, String> redis;
    private final String bucket;
    public S3RedisDeletionStorage(S3Client s3, RedisTemplate<String, String> redis, String bucket) {
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("Deletion bucket is required");
        this.s3 = s3; this.redis = redis; this.bucket = bucket;
    }
    @Override public boolean sweep(DeletionTarget target) {
        // Revalidate fields read from persistence as well as fields constructed by the application.
        String prefix = DeletionTarget.draining(target.getDeletionId(), target.getUserId(), target.getTargetType(), target.getAggregateId()).s3Prefix();
        var versioning = s3.getBucketVersioning(GetBucketVersioningRequest.builder().bucket(bucket)
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(5))).build());
        List<ObjectIdentifier> objects = new ArrayList<>();
        if (versioning.status() == BucketVersioningStatus.UNKNOWN_TO_SDK_VERSION)
            throw new IllegalStateException("Unknown S3 versioning state");
        if (versioning.status() == BucketVersioningStatus.ENABLED || versioning.status() == BucketVersioningStatus.SUSPENDED) {
            var page = s3.listObjectVersions(ListObjectVersionsRequest.builder().bucket(bucket).prefix(prefix).maxKeys(1000)
                    .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(5))).build());
            page.versions().forEach(v -> objects.add(identifier(prefix, v.key(), v.versionId())));
            page.deleteMarkers().forEach(v -> objects.add(identifier(prefix, v.key(), v.versionId())));
            if (objects.isEmpty() && Boolean.TRUE.equals(page.isTruncated())) throw new IllegalStateException("Empty truncated version page");
        } else {
            var page = s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).maxKeys(1000)
                    .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(5))).build());
            page.contents().forEach(v -> objects.add(identifier(prefix, v.key(), null)));
            if (objects.isEmpty() && Boolean.TRUE.equals(page.isTruncated())) throw new IllegalStateException("Empty truncated object page");
        }
        if (objects.isEmpty()) return true;
        if (objects.size() > 1000) throw new IllegalStateException("Deletion page exceeds limit");
        var result = s3.deleteObjects(DeleteObjectsRequest.builder().bucket(bucket)
                .delete(Delete.builder().objects(objects).quiet(true).build())
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(5))).build());
        if (!result.errors().isEmpty()) throw new IllegalStateException("S3 deletion page has unresolved errors");
        // The next pass lists from the beginning; never reuse a pagination marker after deleting that page.
        return false;
    }
    private ObjectIdentifier identifier(String prefix, String key, String version) {
        if (key == null || !key.startsWith(prefix)) throw new IllegalStateException("Storage returned an out-of-scope object");
        return ObjectIdentifier.builder().key(key).versionId(version).build();
    }
    @Override public boolean clearCache(DeletionTarget target) {
        if (target.getTargetType() == DeletionTarget.Type.CHALLENGE) return true;
        String key = DeletionTarget.draining(target.getDeletionId(), target.getUserId(), target.getTargetType(), target.getAggregateId()).redisKey();
        redis.delete(key);
        return Boolean.FALSE.equals(redis.hasKey(key));
    }
}
