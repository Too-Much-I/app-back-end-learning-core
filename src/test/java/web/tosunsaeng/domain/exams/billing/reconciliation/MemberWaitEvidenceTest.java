package web.tosunsaeng.domain.exams.billing.reconciliation;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class MemberWaitEvidenceTest {
    private static final Instant NOW=Instant.parse("2026-10-14T00:00:00Z");

    @Test void pureWaitingTimeExcludesOnlyObservedIntervals() {
        var wait=MemberWaitEvidence.first("op","digest",NOW);
        wait=wait.leave(MemberWaitEvidence.Phase.INTERRUPTED,NOW.plusSeconds(60),false);
        assertThat(wait.excludedAt(NOW.plusSeconds(600))).isEqualTo(60_000);
        wait=wait.observed(NOW.plusSeconds(600));
        assertThat(wait.excludedAt(NOW.plusSeconds(610))).isEqualTo(70_000);
        assertThat(wait.waitingSince()).isEqualTo(NOW);
    }

    @Test void retryExhaustedAloneAndWrongBindingNeverAuthorizeReview() {
        var wait=MemberWaitEvidence.first("op","digest",NOW);
        assertThat(wait.reviewEligible("op","digest","retry_exhausted")).isFalse();
        assertThat(wait.bound("other","digest")).isFalse();
        assertThat(wait.bound("op","other")).isFalse();
    }

    @Test void mixedCauseOrMissingFirstAttemptIsNotAuditedPureExhaustion() {
        for (boolean mixed:new boolean[]{false,true}) {
            var proof=new MemberWaitEvidence.Exhaustion("op","digest",NOW,NOW,NOW,
                    "MEMBER_WAIT_BUDGET_EXHAUSTED",mixed,200,mixed?NOW:null);
            var wait=new MemberWaitEvidence("op","digest",NOW,NOW,MemberWaitEvidence.Phase.WAITING,NOW,0,proof,null);
            assertThat(wait.reviewEligible("op","digest","retry_exhausted")).isFalse();
        }
    }

    @Test void resumedProofIsKeptButCannotAuthorizeAnotherReview() {
        var proof=new MemberWaitEvidence.Exhaustion("op","digest",NOW,NOW,NOW,
                "MEMBER_WAIT_BUDGET_EXHAUSTED",false,200,NOW);
        var wait=new MemberWaitEvidence("op","digest",NOW,NOW,MemberWaitEvidence.Phase.WAITING,NOW,0,proof,null);
        assertThat(wait.reviewEligible("op","digest","retry_exhausted")).isTrue();
        wait=wait.leave(MemberWaitEvidence.Phase.RESUMING,NOW,true).observed(NOW.plusSeconds(1));
        assertThat(wait.reviewEligible("op","digest","retry_exhausted")).isFalse();
        assertThat(wait.exhaustion()).isEqualTo(proof);
    }
}
