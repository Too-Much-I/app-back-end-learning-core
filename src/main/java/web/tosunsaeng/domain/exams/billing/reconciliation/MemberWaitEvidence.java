package web.tosunsaeng.domain.exams.billing.reconciliation;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Durable MEMBER observations, not user credentials or a claim that Identity evidence is now present. */
public record MemberWaitEvidence(String operationId, String requestDigest, Instant waitingSince,
        Instant lastObservedAt, Phase phase, Instant pausedSince, long excludedMillis, Exhaustion exhaustion,
        Instant resumedFromReviewAt) {
    public enum Phase { WAITING, RESUMING, INTERRUPTED, RESOLVED }
    /** Historical, audited causal evidence. Generic retry_exhausted is deliberately insufficient. */
    public record Exhaustion(String operationId,String requestDigest,Instant waitingSince,Instant lastMemberObservation,
            Instant quarantinedAt,String cause,boolean mixedCauses,int chargedAttempts,Instant firstAttemptAt) { }

    public static MemberWaitEvidence first(String operation,String digest,Instant now) {
        return new MemberWaitEvidence(operation,digest,now,now,Phase.WAITING,now,0,null,null);
    }
    public boolean bound(String operation,String digest) {
        return Objects.equals(operationId,operation) && Objects.equals(requestDigest,digest)
                && waitingSince!=null && lastObservedAt!=null && !lastObservedAt.isBefore(waitingSince) && excludedMillis>=0;
    }
    public MemberWaitEvidence observed(Instant now) {
        return new MemberWaitEvidence(operationId,requestDigest,waitingSince,now,Phase.WAITING,
                pausedSince==null?now:pausedSince,excludedMillis,exhaustion,resumedFromReviewAt);
    }
    public long excludedAt(Instant now) {
        return excludedMillis+(pausedSince==null?0:Math.max(0,Duration.between(pausedSince,now).toMillis()));
    }
    public MemberWaitEvidence leave(Phase next,Instant now,boolean review) {
        return new MemberWaitEvidence(operationId,requestDigest,waitingSince,lastObservedAt,next,null,excludedAt(now),exhaustion,
                review?now:resumedFromReviewAt);
    }
    public boolean reviewEligible(String operation,String digest,String failureCode) {
        return bound(operation,digest) && exhaustion!=null && resumedFromReviewAt==null
                && "retry_exhausted".equals(failureCode) && !exhaustion.mixedCauses()
                && "MEMBER_WAIT_BUDGET_EXHAUSTED".equals(exhaustion.cause())
                && Objects.equals(operationId,exhaustion.operationId()) && Objects.equals(requestDigest,exhaustion.requestDigest())
                && Objects.equals(waitingSince,exhaustion.waitingSince()) && exhaustion.lastMemberObservation()!=null
                && exhaustion.quarantinedAt()!=null && !exhaustion.lastMemberObservation().isBefore(waitingSince)
                && exhaustion.firstAttemptAt()!=null && !exhaustion.firstAttemptAt().isBefore(waitingSince)
                && !exhaustion.lastMemberObservation().isBefore(exhaustion.firstAttemptAt())
                && !lastObservedAt.isBefore(exhaustion.lastMemberObservation()) && exhaustion.chargedAttempts()>0
                && !exhaustion.quarantinedAt().isBefore(exhaustion.lastMemberObservation());
    }
}
