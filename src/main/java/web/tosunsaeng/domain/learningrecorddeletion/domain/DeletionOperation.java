package web.tosunsaeng.domain.learningrecorddeletion.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Internal evidence, never an API DTO. Mutation and persistence must share the owner transaction. */
@Document("learning_record_deletion_operations")
@Getter
@NoArgsConstructor
public class DeletionOperation {
    public static final String SCOPE = "ALL_EXAMS_AND_CHALLENGES";
    public enum Stage {
        REQUESTED, FENCED, INVENTORYING, WAITING_COORDINATION, PREPARING_RESTART,
        SAFE_TO_START_LEARNING, DELETING_S3, DELETING_MONGO, CLEARING_CACHE, VERIFYING, COMPLETED
    }
    public enum Status { PROCESSING, CLEANUP_DELAYED, NEEDS_REVIEW, COMPLETED }
    public enum FailureCategory { COORDINATION_WAIT, TARGET_EVIDENCE_UNCERTAIN, CLEANUP_UNAVAILABLE, TRANSACTION_UNCERTAIN }

    @Id private String deletionId;
    @Version private Long version;
    private String userId;
    private String scopeVersion;
    private Instant cutoffAt;
    private Instant requestedAt;
    private Instant targetCompletionAt;
    private Instant presignedCapabilityExpiresAt;
    private Stage stage;
    private Status status;
    private boolean activeGuard;
    private boolean writeBlocked;
    private Instant safeToStartLearningAt;
    private Long sealedTargetCount;
    private String inventoryDigest;
    private Instant completedAt;
    private Instant expiresAt;
    private String leaseToken;
    private Instant leaseUntil;
    private Instant nextAttemptAt;
    private int attemptCount;
    private int retryCount;
    private FailureCategory lastFailureCategory;
    public void recordRetry(FailureCategory category) { retryCount++; lastFailureCategory = category; }
    private boolean retentionFinalized;

    public void holdRetention() { expiresAt = null; }
    public void finishRetention() {
        if (status != Status.COMPLETED) throw new IllegalStateException("Unfinished operation");
        retentionFinalized = true;
        expiresAt = completedAt.plus(Duration.ofDays(30));
    }
    private DeletionTarget.Type inventoryType = DeletionTarget.Type.EXAM;
    private String inventoryCursor;
    private long inventoriedCount;
    private String runningInventoryDigest = DeletionIdentifiers.sha256("");

    public static DeletionOperation requested(String id, String owner, Instant now) {
        DeletionOperation operation = new DeletionOperation();
        operation.deletionId = DeletionIdentifiers.uuidV4(id);
        operation.userId = DeletionIdentifiers.userId(owner);
        operation.scopeVersion = SCOPE;
        operation.cutoffAt = operation.requestedAt = Objects.requireNonNull(now);
        operation.targetCompletionAt = now.plus(Duration.ofHours(24));
        // Existing PUT capability is at most five minutes, plus one minute skew.
        operation.presignedCapabilityExpiresAt = now.plus(Duration.ofMinutes(6));
        operation.stage = Stage.REQUESTED;
        operation.status = Status.PROCESSING;
        operation.activeGuard = operation.writeBlocked = true;
        operation.nextAttemptAt = now;
        return operation;
    }

    public void inventoryProgress(DeletionTarget.Type type, String cursor, long count, String digest) {
        if (stage != Stage.INVENTORYING || inventoryDigest != null || count < inventoriedCount)
            throw new IllegalStateException("Inventory is already sealed");
        inventoryType = type; inventoryCursor = cursor; inventoriedCount = count; runningInventoryDigest = digest;
    }

    public void releaseLeaseUntil(Instant next) { leaseToken = null; leaseUntil = null; nextAttemptAt = next; }

    public void advance(Stage next) {
        if (status != Status.PROCESSING || next == null || next.ordinal() != stage.ordinal() + 1
                || next == Stage.SAFE_TO_START_LEARNING || next == Stage.COMPLETED
                || (next == Stage.WAITING_COORDINATION && inventoryDigest == null)) {
            throw new IllegalStateException("Deletion stage requires verified preceding evidence");
        }
        stage = next;
    }

    public void sealInventory(long count, String digest) {
        if (stage != Stage.INVENTORYING || status != Status.PROCESSING || count < 0
                || digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("Invalid inventory seal");
        }
        if (inventoryDigest != null && (!inventoryDigest.equals(digest) || sealedTargetCount != count)) {
            throw new IllegalStateException("Sealed inventory cannot be expanded or replaced");
        }
        sealedTargetCount = count;
        inventoryDigest = digest;
    }

    public void allowNewLearning(RestartEvidence evidence, Instant now) {
        Objects.requireNonNull(evidence);
        if (status != Status.PROCESSING || stage != Stage.PREPARING_RESTART
                || inventoryDigest == null || now.isBefore(requestedAt)
                || !inventoryDigest.equals(evidence.inventoryDigest())
                || sealedTargetCount != evidence.targetCount()
                || evidence.sealedCallbackCount() != sealedTargetCount
                || !evidence.readFenceVerified() || !evidence.billingResolved()
                || !evidence.challengeSlotsReleased() || !evidence.commitOutcomeKnown()) {
            throw new IllegalStateException("New learning requires a verified safety checkpoint");
        }
        safeToStartLearningAt = now;
        writeBlocked = false;
        stage = Stage.SAFE_TO_START_LEARNING;
    }

    public void requireIntervention(boolean safetyUncertain) {
        if (!activeGuard) {
            throw new IllegalStateException("Completed deletion cannot be reopened");
        }
        if (safetyUncertain || safeToStartLearningAt == null || writeBlocked) {
            status = Status.NEEDS_REVIEW;
            writeBlocked = true;
        } else {
            status = Status.CLEANUP_DELAYED;
            writeBlocked = false;
        }
        // The work stage and sealed inventory survive intervention. Never attach TTL here.
    }

    public void resumeCleanup() {
        if (status != Status.CLEANUP_DELAYED || writeBlocked || safeToStartLearningAt == null) {
            throw new IllegalStateException("Only verified cleanup delay may automatically resume");
        }
        status = Status.PROCESSING;
    }

    public void complete(CompletionEvidence evidence, Instant now) {
        Objects.requireNonNull(evidence);
        if (status != Status.PROCESSING || stage != Stage.VERIFYING || writeBlocked
                || safeToStartLearningAt == null || evidence.mongoRemaining() != 0
                || evidence.redisRemaining() != 0 || evidence.unresolvedCoordination() != 0
                || !evidence.s3Empty() || evidence.s3FinalSweepAt() == null
                || evidence.s3FinalSweepAt().isBefore(presignedCapabilityExpiresAt)
                || evidence.s3FinalSweepAt().isAfter(now) || now.isBefore(safeToStartLearningAt)) {
            throw new IllegalStateException("Physical deletion completion is not proven");
        }
        stage = Stage.COMPLETED;
        status = Status.COMPLETED;
        activeGuard = false;
        completedAt = now;
        expiresAt = now.plus(Duration.ofDays(30));
    }

    public record RestartEvidence(String inventoryDigest, long targetCount, long sealedCallbackCount,
                                  boolean readFenceVerified, boolean billingResolved,
                                  boolean challengeSlotsReleased, boolean commitOutcomeKnown) {}
    public record CompletionEvidence(long mongoRemaining, long redisRemaining, long unresolvedCoordination,
                                     boolean s3Empty, Instant s3FinalSweepAt) {}
}
