package web.tosunsaeng.domain.learningrecorddeletion.api;

import web.tosunsaeng.domain.learningrecorddeletion.domain.DeletionOperation;
import java.time.Instant;

/** Allowlisted public projection. Does not expose owner, target, inventory or Billing identifiers. */
public record DeletionView(String deletionId, String status, boolean canStartLearning,
                           @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) Instant requestedAt,
                           @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) Instant physicalDeletionTargetAt,
                           @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) Instant completedAt) {
    public static DeletionView from(DeletionOperation operation) {
        if (operation == null) return new DeletionView(null, "not_requested", true, null, null, null);
        String status = switch (operation.getStatus()) {
            case PROCESSING -> "processing";
            case CLEANUP_DELAYED -> "cleanup_delayed";
            case NEEDS_REVIEW -> "needs_review";
            case COMPLETED -> "completed";
        };
        return new DeletionView(operation.getDeletionId(), status, !operation.isWriteBlocked(),
                operation.getRequestedAt(), operation.getTargetCompletionAt(), operation.getCompletedAt());
    }
}
