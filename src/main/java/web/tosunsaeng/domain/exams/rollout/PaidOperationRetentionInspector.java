package web.tosunsaeng.domain.exams.rollout;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import web.tosunsaeng.domain.exams.billing.reconciliation.ReservationRecovery;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import java.time.Instant;
import java.util.ArrayList;

/** Read-only single-operation review. No scheduler, TTL arming, deletion, or production CLI. */
public final class PaidOperationRetentionInspector {
    public enum Outcome {
        NOT_FOUND, LEGACY_OR_MISSING_EVIDENCE, UNSAFE_TTL, UNRESOLVED, CLOCK_UNTRUSTED,
        RESTORE_UNVERIFIED, BEFORE_BOUNDARY, HOLD_OR_LINKED_WORK, RECOVERY_OR_ALERT_PENDING,
        PURPOSE_RETENTION_UNAPPROVED, REVIEW_CANDIDATE
    }
    /** Explicit external attestations, not automated proof of Billing, legal holds, or backup coverage. */
    public record Preconditions(boolean clockTrusted, boolean restoreCoverageVerified,
            boolean holdsCleared, boolean linkedWorkResolved, boolean purposeRetentionApproved) {}
    public record Result(Outcome outcome, Instant earliestReviewAt) {}
    private final MongoTemplate mongo;
    public PaidOperationRetentionInspector(MongoTemplate mongo) { this.mongo=mongo; }

    public Result inspect(String commandId, Instant now, Preconditions checks) {
        var collection=mongo.getCollection("exam_creation_operations")
                .withReadConcern(ReadConcern.MAJORITY).withReadPreference(ReadPreference.primary());
        // A newly introduced TTL on another paid field could erase held/unresolved evidence.
        for (Document index:collection.listIndexes().into(new ArrayList<>())) {
            if (index.containsKey("expireAfterSeconds")
                    && !new Document("purgeAt",1).equals(index.get("key"))) return result(Outcome.UNSAFE_TTL,null);
        }
        Document raw=collection.find(new Document("_id",commandId)).first();
        if(raw==null) return result(Outcome.NOT_FOUND,null);
        if(raw.get("purgeAt")!=null) return result(Outcome.UNSAFE_TTL,null);
        return evaluate(mongo.getConverter().read(ExamCreationOperation.class,raw),now,checks);
    }

    public static Result evaluate(ExamCreationOperation op, Instant now, Preconditions checks) {
        if(op==null) return result(Outcome.NOT_FOUND,null);
        if(op.getPaidSelection()==null || op.getFirstAcceptedAt()==null || op.getPaidRequestDigest()==null)
            return result(Outcome.LEGACY_OR_MISSING_EVIDENCE,null);
        if(op.getPurgeAt()!=null) return result(Outcome.UNSAFE_TTL,null);
        if(!op.isTerminal() || !Boolean.FALSE.equals(op.getActiveGuard()) || op.getTerminalAt()==null)
            return result(Outcome.UNRESOLVED,null);
        Instant boundary=op.getDetailPurgeEligibleAt();
        Instant required=op.getTerminalAt().plus(ExamCreationOperation.PAID_RETENTION);
        if(op.getTerminalAt().isBefore(op.getFirstAcceptedAt()) || boundary==null
                || boundary.isBefore(required) || boundary.isBefore(op.getGuaranteeUntil()))
            return result(Outcome.LEGACY_OR_MISSING_EVIDENCE,null);
        if(checks==null || !checks.clockTrusted() || now==null || now.isBefore(op.getTerminalAt()))
            return result(Outcome.CLOCK_UNTRUSTED,boundary);
        if(!checks.restoreCoverageVerified()) return result(Outcome.RESTORE_UNVERIFIED,boundary);
        if(now.isBefore(boundary)) return result(Outcome.BEFORE_BOUNDARY,boundary);
        if(!checks.holdsCleared() || !checks.linkedWorkResolved()) return result(Outcome.HOLD_OR_LINKED_WORK,boundary);
        var r=op.getRecovery();
        if(r==null || r.getStatus()!=ReservationRecovery.Status.DONE || r.getLeaseToken()!=null
                || r.getLeaseUntil()!=null || r.getAlertLeaseToken()!=null || r.getAlertLeaseUntil()!=null
                || pendingAlert(r.getAlertStatus()) || r.getEarlyAlert()!=null
                && (pendingAlert(r.getEarlyAlert().getAlertStatus()) || r.getEarlyAlert().getAlertLeaseToken()!=null
                    || r.getEarlyAlert().getAlertLeaseUntil()!=null))
            return result(Outcome.RECOVERY_OR_ALERT_PENDING,boundary);
        if(!checks.purposeRetentionApproved()) return result(Outcome.PURPOSE_RETENTION_UNAPPROVED,boundary);
        // This is NOT deletion permission: final purge must recheck version, holds, and purpose evidence atomically.
        return result(Outcome.REVIEW_CANDIDATE,boundary);
    }
    private static boolean pendingAlert(String status) {
        return status!=null && !"SDK_ACCEPTED".equals(status);
    }
    private static Result result(Outcome outcome,Instant at) { return new Result(outcome,at); }
}
