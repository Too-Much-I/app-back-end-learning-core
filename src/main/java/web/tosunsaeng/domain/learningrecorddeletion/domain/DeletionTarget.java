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
    public enum Type { EXAM, CHALLENGE, LEARNING_SUBMISSION, LEARNING_COMPLETION;
        public String rootCollection() {
            return switch (this) {
                case EXAM -> "exam_sessions";
                case CHALLENGE -> "challenge_10s_attempts";
                case LEARNING_SUBMISSION -> "learning_submissions";
                case LEARNING_COMPLETION -> "learning_completions";
            };
        }
    }
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
        // The learning submission API accepts canonical UUIDs, including existing non-v4 IDs.
        if (type == Type.LEARNING_SUBMISSION && !java.util.UUID.fromString(aggregateId).toString().equals(aggregateId))
            throw new IllegalArgumentException("Invalid learning submission ID");
        target.targetType = type;
        target.aggregateId = aggregateId;
        target.id = deletionId + ":" + type + ":" + aggregateId;
        target.callbackFence = CallbackFence.DRAINING;
        return target;
    }

    public String s3Prefix() {
        return switch (targetType) {
            case EXAM -> "temp/" + aggregateId + "/";
            case CHALLENGE -> "temp/challenges/" + aggregateId + "/";
            case LEARNING_SUBMISSION -> "answer-learning-submissions/" + aggregateId + "/";
            case LEARNING_COMPLETION -> throw new IllegalStateException("Completion has no audio");
        };
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
