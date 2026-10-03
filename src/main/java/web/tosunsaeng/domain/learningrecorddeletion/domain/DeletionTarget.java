package web.tosunsaeng.domain.learningrecorddeletion.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document("learning_record_deletion_targets")
@Getter
@NoArgsConstructor
public class DeletionTarget {
    public enum Type { EXAM, CHALLENGE }
    public enum CallbackFence { DRAINING, SEALED }
    @Id private String id;
    @Version private Long version;
    private String deletionId;
    private String userId;
    private Type targetType;
    private String aggregateId;
    private CallbackFence callbackFence;
    private boolean referencesInventoried;
    private Instant callbackSealedAt;
    private Instant expiresAt;
    private boolean prepared;
    private boolean mongoCleared;
    private boolean cacheCleared;
    private Instant storageSweptAt;
    private Instant verifiedAt;

    public void markVerified(Instant now) {
        if (!prepared || !mongoCleared || !cacheCleared || storageSweptAt == null)
            throw new IllegalStateException("Unfinished target");
        verifiedAt = now;
    }

    public void markPrepared() {
        if (callbackFence != CallbackFence.SEALED || !referencesInventoried) throw new IllegalStateException("Unsealed target");
        prepared = true;
    }
    public void markMongoCleared() { if (!prepared) throw new IllegalStateException("Unprepared target"); mongoCleared = true; }
    public void markCacheCleared() { cacheCleared = true; }
    public void markStorageSwept(Instant now) { storageSweptAt = now; }
    public void retainUntil(Instant expiry) { expiresAt = expiry; }

    public static DeletionTarget draining(String deletionId, String userId, Type type, String aggregateId) {
        DeletionTarget target = new DeletionTarget();
        target.deletionId = DeletionIdentifiers.uuidV4(deletionId);
        target.userId = DeletionIdentifiers.userId(userId);
        // Path-safe IDs only: never accept an arbitrary storage prefix.
        if (type == null || aggregateId == null || !aggregateId.matches("[A-Za-z0-9_-]{1,128}")
                || (type == Type.EXAM && "challenges".equals(aggregateId))) {
            throw new IllegalArgumentException("Invalid deletion target");
        }
        if (type == Type.CHALLENGE) DeletionIdentifiers.uuidV4(aggregateId);
        target.targetType = type;
        target.aggregateId = aggregateId;
        target.id = deletionId + ":" + type + ":" + aggregateId;
        target.callbackFence = CallbackFence.DRAINING;
        return target;
    }

    public String s3Prefix() {
        return (targetType == Type.EXAM ? "temp/" : "temp/challenges/") + aggregateId + "/";
    }

    public String redisKey() {
        return targetType == Type.EXAM ? "exam:status:" + aggregateId : null;
    }

    public void sealCallbacks(boolean referencesVerified, boolean coordinationResolved, Instant now) {
        if (!referencesVerified || !coordinationResolved || now == null) {
            throw new IllegalStateException("Callback seal requires references and coordination evidence");
        }
        if (callbackFence == CallbackFence.SEALED) return;
        referencesInventoried = true;
        callbackFence = CallbackFence.SEALED;
        callbackSealedAt = now;
    }
}
