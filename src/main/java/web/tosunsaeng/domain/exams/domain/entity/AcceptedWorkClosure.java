package web.tosunsaeng.domain.exams.domain.entity;

import java.time.Instant;
import java.util.Objects;
import web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus;
import web.tosunsaeng.domain.exams.billing.PaidReservationProtocol.Source;
import web.tosunsaeng.domain.exams.domain.enums.ExamEntitlementState;

/** Server-only snapshot written with the restart fence; not a permission boolean or a public DTO. */
public record AcceptedWorkClosure(String examId, String userId, String mockExamId, String groupId,
        String reservationId, String creationOperationId, Source source, Instant confirmedAt,
        String closingOperationId, Instant closedAt) {
    public static AcceptedWorkClosure capture(ExamSession s, String closingOperation, Instant closedAt) {
        if (!confirmed(s) || s.isAccessRevoked() || !s.isInProgress() || s.getTerminalEventId()!=null
                || closingOperation==null || closingOperation.isBlank() || closedAt==null) return null;
        return new AcceptedWorkClosure(s.getExamId(),s.getUserId(),s.getMockExamId(),s.getAttemptGroupId(),
                s.getBillingReservationId(),s.getCreationOperationId(),s.getPaidReservation().snapshot().entitlementSource(),
                s.getEntitlementConfirmedAt(),closingOperation,closedAt);
    }

    public boolean matches(ExamSession s) {
        return confirmed(s) && Objects.equals(examId,s.getExamId()) && Objects.equals(userId,s.getUserId())
                && Objects.equals(mockExamId,s.getMockExamId()) && Objects.equals(groupId,s.getAttemptGroupId())
                && Objects.equals(reservationId,s.getBillingReservationId()) && Objects.equals(creationOperationId,s.getCreationOperationId())
                && source==s.getPaidReservation().snapshot().entitlementSource() && Objects.equals(confirmedAt,s.getEntitlementConfirmedAt())
                && closedAt!=null && Objects.equals(closedAt,s.getSubmissionsClosedAt())
                && closingOperationId!=null && !closingOperationId.isBlank()
                && Objects.equals(closingOperationId,s.getSubmissionsClosedByOperationId());
    }

    private static boolean confirmed(ExamSession s) {
        if (s==null || s.getPaidReservation()==null || s.getUserId()==null || s.getUserId().isBlank()
                || !Objects.equals(s.getUserId(),s.getEntitlementConfirmedUserId())
                || s.getEntitlementState()!=ExamEntitlementState.CONFIRMED || s.getEntitlementConfirmedAt()==null) return false;
        var p=s.getPaidReservation().snapshot();
        return p.reservationStatus()==ReservationStatus.CONFIRMED && p.confirmRejection()==null && p.entitlementSource()!=null
                && Objects.equals(p.terminalAt(),s.getEntitlementConfirmedAt()) && Objects.equals(p.sessionId(),s.getExamId())
                && Objects.equals(p.mockExamId(),s.getMockExamId()) && Objects.equals(p.attemptGroupId(),s.getAttemptGroupId())
                && Objects.equals(p.reservationId(),s.getBillingReservationId()) && Objects.equals(p.operationId(),s.getCreationOperationId())
                && p.reservationKind()==s.getBillingReservationKind();
    }
}
