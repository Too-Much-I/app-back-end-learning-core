package web.tosunsaeng.domain.exams.reanswer;

import java.time.Instant;
import java.util.UUID;

/** Immutable embedded evidence; strings preserve Billing's nanosecond precision in BSON. */
public record ReanswerApproval(String type, String revision, String userId, String checkedAt, String validUntil) {
    public static final String TYPE = "COMPLETED_EXAM_REANSWER_ENTITLEMENT";
    // Explicit implementation revision: not a claim that the draft is jointly frozen.
    public static final String REVISION = "lc-billing-feedback-reanswer-eligibility-r1-draft-5";

    public boolean valid() {
        try {
            var checked = Instant.parse(checkedAt);
            var until = Instant.parse(validUntil);
            return TYPE.equals(type) && REVISION.equals(revision)
                    && UUID.fromString(userId).toString().equals(userId)
                    && checked.isBefore(until) && !until.isAfter(checked.plusSeconds(5));
        } catch (RuntimeException invalid) { return false; }
    }
}
