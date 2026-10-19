package web.tosunsaeng.domain.exams.rollout;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import web.tosunsaeng.domain.exams.billing.BillingReservationClient.*;
import web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.Source;
import web.tosunsaeng.domain.exams.domain.entity.*;
import web.tosunsaeng.domain.exams.domain.enums.*;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LegacyExamRuntimeTest {
    private final LegacyExamRuntime runtime = new LegacyExamRuntime(
            LegacyExamManifest.build("test", "isolated", "reader-1", List.of("legacy")));
    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");
    private static final String OP = "10000000-0000-4000-8000-000000000001";
    private static final String RES = "10000000-0000-4000-8000-000000000002";
    private static final String GROUP = "10000000-0000-4000-8000-000000000003";

    @Test void exactLegacyIsReadableWithoutInventingSourceOrStatus() {
        var session = ExamSession.builder().examId("legacy").build();
        runtime.requireKnown(session);
        assertNull(session.getStatus());
        assertNull(session.getPaidReservation());
    }

    @Test void missingSourceOutsideManifestIsNotLegacy() {
        assertThrows(IllegalStateException.class, () -> runtime.requireKnown(
                ExamSession.builder().examId("new").build()));
    }

    @Test void explicitFreeOriginIsReadableAfterSwitchOffWithoutManifestMutation() {
        var free = free();
        runtime.requireKnown(free);
        free.closeSubmissions(OP,NOW);
        runtime.requireKnown(free);
        assertFalse(free.isActive());
        assertNull(free.getAttemptGroupId());
        assertThrows(IllegalStateException.class, () -> runtime.requireLegacy(free));
    }

    @Test void unknownMixedOrUncoveredFreeOriginsAreRejected() {
        for (String field : List.of("creationOrigin", "creationOperationId", "billingReservationId", "attemptGroupId")) {
            var free = free();
            ReflectionTestUtils.setField(free,field,"unexpected");
            assertThrows(IllegalStateException.class, () -> runtime.requireKnown(free),field);
        }
        var free = free();
        ReflectionTestUtils.setField(free,"submissionEvidenceVersion",null);
        assertThrows(IllegalStateException.class, () -> runtime.requireKnown(free));
        var paid = paid();
        ReflectionTestUtils.setField(paid,"creationOrigin",ExamSession.TRANSITION_FREE_V1);
        assertThrows(IllegalStateException.class, () -> runtime.requireKnown(paid));
    }

    private ExamSession free() {
        return ExamSession.builder().examId("free").creationOrigin(ExamSession.TRANSITION_FREE_V1)
                .submissionEvidenceVersion(1).requiredQuestionNumbers(List.of(1,2))
                .status(ExamSessionStatus.IN_PROGRESS).active(true).build();
    }

    @Test void manifestMemberCannotBeReinterpretedAsPaid() {
        var session = paid();
        ReflectionTestUtils.setField(session, "examId", "legacy");
        assertThrows(IllegalStateException.class, () -> runtime.requireKnown(session));
    }

    @Test void confirmedEvidenceNeedsNoOperationRepositoryAndAllowsCurrentOwnerMigration() {
        var session = paid();
        ReflectionTestUtils.setField(session, "userId", "new-owner");
        runtime.requireKnown(session);
    }

    @Test void everySessionLinkAndConfirmationTimeMustMatch() {
        for (String field : List.of("examId", "mockExamId", "creationOperationId", "billingReservationId", "attemptGroupId")) {
            var session = paid();
            ReflectionTestUtils.setField(session, field, "different");
            assertThrows(IllegalStateException.class, () -> runtime.requireKnown(session), field);
        }
        var session = paid();
        ReflectionTestUtils.setField(session, "entitlementConfirmedAt", NOW.plusSeconds(1));
        assertThrows(IllegalStateException.class, () -> runtime.requireKnown(session));
    }

    @Test void pendingEvidenceCannotBackAnActiveSession() {
        var session = pending();
        runtime.requireKnown(session);
        ReflectionTestUtils.setField(session, "status", ExamSessionStatus.IN_PROGRESS);
        assertThrows(IllegalStateException.class, () -> runtime.requireKnown(session));
    }

    @Test void classificationDoesNotRestoreClosedOrRevokedSession() {
        var session = paid();
        session.closeSubmissions(OP, NOW.plusSeconds(1));
        ReflectionTestUtils.setField(session, "accessRevoked", true);
        runtime.requireKnown(session);
        assertFalse(session.isActive());
        assertTrue(session.isAccessRevoked());
        assertNotNull(session.getSubmissionsClosedAt());
    }

    private ExamSession paid() {
        var session = pending();
        session.observePaidReservation(new PaidReservationEvidence(snapshot(ReservationStatus.CONFIRMED)));
        session.activatePaid(NOW);
        return session;
    }

    private ExamSession pending() {
        return ExamSession.builder().examId("new").mockExamId("mock").userId("original-owner")
                .creationOperationId(OP).billingReservationId(RES).attemptGroupId(GROUP)
                .billingReservationKind(BillingReservationKind.INITIAL)
                .entitlementState(ExamEntitlementState.CONFIRMING)
                .status(ExamSessionStatus.ENTITLEMENT_CONFIRMING).active(false)
                .paidReservation(new PaidReservationEvidence(snapshot(ReservationStatus.RESERVED))).build();
    }

    private ReservationSnapshot snapshot(ReservationStatus status) {
        return new ReservationSnapshot(OP, RES, BillingReservationKind.INITIAL, status, GROUP,
                AttemptGroupStatus.OPEN, "new", "mock", null, null, NOW.plusSeconds(60),
                status == ReservationStatus.RESERVED ? null : NOW, Source.EXAM_PASS, null, null);
    }
}
