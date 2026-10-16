package web.tosunsaeng.domain.exams.rollout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import web.tosunsaeng.domain.exams.domain.entity.*;
import web.tosunsaeng.domain.exams.domain.enums.*;
import web.tosunsaeng.domain.exams.billing.reconciliation.ReservationRecovery;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static web.tosunsaeng.domain.exams.rollout.PaidOperationRetentionInspector.*;

class PaidOperationRetentionTest {
    static final Instant NOW=Instant.parse("2026-10-14T00:00:00.123456789Z");
    static final Instant FIRST=Instant.parse("2026-10-14T00:00:00.123Z");
    static final Duration D=Duration.ofDays(120);
    static final Preconditions SAFE=new Preconditions(true,true,true,true,true);
    static ExamCreationOperation paid() {
        var op=ExamCreationOperation.prepared("owner","key","session","mock",1,NOW);
        op.fixPaidSelection(new PaidExamSelection(null,List.of(1,2)));
        op.fixPaidRequestDigest("a".repeat(64));return op;
    }
    static ExamCreationOperation terminal() {
        var op=paid();op.markFailedTerminal("fixture",NOW.plusSeconds(10),NOW.plusSeconds(20));
        op.completeRecovery(NOW.plusSeconds(10));return op;
    }
    @Test void t01MillisecondFirstAcceptanceAndExclusiveBoundary() {
        var op=paid();var until=FIRST.plus(D);
        assertThat(op.getFirstAcceptedAt()).isEqualTo(FIRST);assertThat(op.getCreatedAt()).isEqualTo(FIRST);
        assertThat(op.withinPaidGuarantee(until.minusMillis(1))).isTrue();
        assertThat(op.withinPaidGuarantee(until)).isFalse();assertThat(op.withinPaidGuarantee(until.plusMillis(1))).isFalse();
        assertThat(op.withinPaidGuarantee(FIRST.minusMillis(1))).isFalse();
    }
    @Test void t02LeaseAndRecoveryDoNotExtendFirstAcceptanceOrTerminal() {
        var op=terminal();var at=op.getTerminalAt();var boundary=op.getDetailPurgeEligibleAt();
        op.getRecovery().setLeaseUntil(NOW.plus(D));op.completeRecovery(NOW.plus(D));
        assertThat(op.getFirstAcceptedAt()).isEqualTo(FIRST);assertThat(op.getTerminalAt()).isEqualTo(at);
        assertThat(op.getDetailPurgeEligibleAt()).isEqualTo(boundary);assertThat(op.getPurgeAt()).isNull();
        assertThatThrownBy(()->op.markFailedTerminal("again",NOW.plus(D),NOW.plus(D))).isInstanceOf(IllegalStateException.class);
    }
    @Test void t03ReviewBoundaryIsNotAutomaticDeletionPermission() {
        var op=terminal();var at=op.getDetailPurgeEligibleAt();
        assertThat(at).isEqualTo(FIRST.plusSeconds(10).plus(D));assertThat(op.getPurgeAt()).isNull();
        assertThat(evaluate(op,at.minusMillis(1),SAFE).outcome()).isEqualTo(Outcome.BEFORE_BOUNDARY);
        assertThat(evaluate(op,at,SAFE).outcome()).isEqualTo(Outcome.REVIEW_CANDIDATE);
        assertThat(evaluate(op,at.plusMillis(1),SAFE).outcome()).isEqualTo(Outcome.REVIEW_CANDIDATE);
    }
    @ParameterizedTest @EnumSource(value=ExamCreationState.class,names={"CANCELED","EXPIRED","FAILED_TERMINAL","SUCCEEDED"})
    void everyPaidTerminalWriterKeepsTtlUnarmed(ExamCreationState state) {
        var op=paid();
        switch(state) {
            case CANCELED -> op.markCanceled(NOW,NOW.plusSeconds(1));
            case EXPIRED -> op.markExpired(NOW,NOW.plusSeconds(1));
            case FAILED_TERMINAL -> op.markFailedTerminal("failure",NOW,NOW.plusSeconds(1));
            case SUCCEEDED -> {
                op.markReserved("r",BillingReservationKind.INITIAL,"g",NOW.plusSeconds(60),NOW);
                op.markSessionCommitted(NOW);op.markPaidSucceeded(NOW,NOW);
            }
            default -> throw new AssertionError();
        }
        op.completeRecovery(NOW.plusSeconds(100));
        assertThat(op.getPurgeAt()).isNull();assertThat(op.getDetailPurgeEligibleAt()).isEqualTo(FIRST.plus(D));
    }
    @Test void legacySevenDayRetentionIsNotSilentlyBackfilled() {
        var op=ExamCreationOperation.prepared("o","k","s","m",1,NOW);
        op.markFailedTerminal("failure",NOW,NOW.plus(Duration.ofDays(7)));op.completeRecovery(NOW);
        assertThat(op.getFirstAcceptedAt()).isNull();assertThat(op.getDetailPurgeEligibleAt()).isNull();
        assertThat(op.getPurgeAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }
    @ParameterizedTest @EnumSource(value=ReservationRecovery.Status.class,names={"NEEDS_REVIEW","BLOCKED_AUTH","BLOCKED_OWNER","IN_FLIGHT","READY"})
    void unresolvedAndQuarantinedWorkNeverBecomesCandidate(ReservationRecovery.Status status) {
        var op=paid();op.getRecovery().setStatus(status);
        assertThat(evaluate(op,NOW.plus(D).plus(D),SAFE).outcome()).isEqualTo(Outcome.UNRESOLVED);
        assertThat(op.getPurgeAt()).isNull();assertThat(op.getActiveGuard()).isTrue();
        var done=terminal();done.getRecovery().setStatus(status);
        assertThat(evaluate(done,NOW.plus(D).plus(D),SAFE).outcome()).isEqualTo(Outcome.RECOVERY_OR_ALERT_PENDING);
    }
    @Test void allExternalChecksAreRequiredAndNeverImpliedByAge() {
        var op=terminal();var now=NOW.plus(D).plus(D);
        assertThat(evaluate(op,now,new Preconditions(false,true,true,true,true)).outcome()).isEqualTo(Outcome.CLOCK_UNTRUSTED);
        assertThat(evaluate(op,now,new Preconditions(true,false,true,true,true)).outcome()).isEqualTo(Outcome.RESTORE_UNVERIFIED);
        assertThat(evaluate(op,now,new Preconditions(true,true,false,true,true)).outcome()).isEqualTo(Outcome.HOLD_OR_LINKED_WORK);
        assertThat(evaluate(op,now,new Preconditions(true,true,true,false,true)).outcome()).isEqualTo(Outcome.HOLD_OR_LINKED_WORK);
        assertThat(evaluate(op,now,new Preconditions(true,true,true,true,false)).outcome()).isEqualTo(Outcome.PURPOSE_RETENTION_UNAPPROVED);
    }
    @Test void pendingAlertsLeaseAndUnsafeTtlBlockReview() {
        var op=terminal();var now=NOW.plus(D).plus(D);
        op.getRecovery().setAlertStatus("PENDING");
        assertThat(evaluate(op,now,SAFE).outcome()).isEqualTo(Outcome.RECOVERY_OR_ALERT_PENDING);
        op.getRecovery().setAlertStatus("SDK_ACCEPTED");
        assertThat(evaluate(op,now,SAFE).outcome()).isEqualTo(Outcome.REVIEW_CANDIDATE);
        op.getRecovery().setAlertStatus("UNSUBMITTED");
        assertThat(evaluate(op,now,SAFE).outcome()).isEqualTo(Outcome.RECOVERY_OR_ALERT_PENDING);
        op.getRecovery().setAlertStatus(null);op.getRecovery().setLeaseUntil(NOW);
        assertThat(evaluate(op,now,SAFE).outcome()).isEqualTo(Outcome.RECOVERY_OR_ALERT_PENDING);
        op.getRecovery().setLeaseUntil(null);ReflectionTestUtils.setField(op,"purgeAt",now);
        assertThat(evaluate(op,now,SAFE).outcome()).isEqualTo(Outcome.UNSAFE_TTL);
    }
    @Test void missingAcceptanceEvidenceDoesNotGainCoverageFromCreatedAt() {
        var op=terminal();ReflectionTestUtils.setField(op,"firstAcceptedAt",null);
        assertThat(op.withinPaidGuarantee(NOW)).isFalse();
        assertThat(evaluate(op,NOW.plus(D).plus(D),SAFE).outcome()).isEqualTo(Outcome.LEGACY_OR_MISSING_EVIDENCE);
    }
}
