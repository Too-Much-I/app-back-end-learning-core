package web.tosunsaeng.domain.exams.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.entity.PaidExamSelection.StartReason;
import web.tosunsaeng.domain.exams.domain.enums.BillingReservationKind;
import web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationSnapshot;
import web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationStatus;

import java.util.Objects;
import java.util.UUID;

/** Paid wire validation only. Does not select entitlement or finalize a Session. */
public final class PaidReservationProtocol {
    private PaidReservationProtocol() { }
    public enum Source { EXAM_PASS, SUBSCRIPTION }
    public record Transition(String previousAttemptGroupId, String previousSessionId) {
        public Transition { uuid(previousAttemptGroupId, true); opaque(previousSessionId); }
    }
    public record ConfirmRejection(String code) {
        public ConfirmRejection { require("ENTITLEMENT_REVOKED".equals(code)); }
    }
    public record RestartContext(String previousAttemptGroupId, String previousSessionId, StartReason startReason) {
        public RestartContext { uuid(previousAttemptGroupId, true); opaque(previousSessionId); require(startReason != null); }
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(String userId, String sessionId, String mockExamId, RestartContext restartContext) {
        public Request {
            uuid(userId, false); opaque(sessionId); opaque(mockExamId);
            require(restartContext == null || !sessionId.equals(restartContext.previousSessionId()));
        }
    }

    public record PhoneRequest(String userId, String sessionId, String mockExamId,
            web.tosunsaeng.domain.exams.domain.enums.BillingContinuationReason continuationReason,
            String continuationId, String expectedAttemptGroupId) {
        public PhoneRequest {
            uuid(userId, false); opaque(sessionId); opaque(mockExamId); uuid(continuationId, true);
            uuid(expectedAttemptGroupId, true);
            require(continuationReason == web.tosunsaeng.domain.exams.domain.enums.BillingContinuationReason.PHONE_REJOIN);
        }
    }

    public static PhoneRequest phoneRequest(ExamCreationOperation op) {
        require(op != null && op.getPaidSelection() != null && op.isPhoneContinuation());
        require(op.getPaidLocalSourceOrigin() == null);
        uuid(op.getOperationId(), true);
        require(op.getReplacementSourceSessionId() == null && op.getPaidSelection().startReason() == null
                && Objects.equals(op.getExpectedMockExamId(), op.getMockExamId()));
        return new PhoneRequest(op.getUserId(), op.getSessionId(), op.getMockExamId(),
                op.getContinuationReason(), op.getContinuationId(), op.getExpectedAttemptGroupId());
    }

    public static void matchesPhone(String operationId, PhoneRequest request, ReservationSnapshot s) {
        snapshot(s);
        require(Objects.equals(operationId, s.operationId()) && request.sessionId().equals(s.sessionId())
                && request.mockExamId().equals(s.mockExamId()) && request.continuationReason() == s.continuationReason()
                && request.continuationId().equals(s.continuationId()) && s.transition() == null);
        require(s.reservationKind() == BillingReservationKind.INITIAL && s.entitlementSource() == Source.SUBSCRIPTION
                && !request.expectedAttemptGroupId().equals(s.attemptGroupId())
                || s.reservationKind() == BillingReservationKind.REPLACEMENT && s.entitlementSource() == Source.EXAM_PASS
                && request.expectedAttemptGroupId().equals(s.attemptGroupId()));
    }

    public static Request request(ExamCreationOperation op) {
        require(op != null && op.getPaidSelection() != null && !op.isPhoneContinuation());
        require(op.getContinuationId() == null && op.getContinuationReason() == null);
        uuid(op.getOperationId(), true);
        RestartContext restart = null;
        if (op.getExpectedAttemptGroupId() != null) {
            require(op.getPaidLocalSourceOrigin() == null);
            restart = new RestartContext(op.getExpectedAttemptGroupId(), op.getReplacementSourceSessionId(),
                    op.getPaidSelection().startReason());
            require(op.getExpectedMockExamId() != null);
            if (restart.startReason() == StartReason.RESTART_INCOMPLETE)
                require(Objects.equals(op.getExpectedMockExamId(), op.getMockExamId()));
        } else {
            // Legacy original target is local-only; never create a retrospective Billing group/transition.
            if (op.getPaidLegacySource() != null || op.getPaidLocalSourceOrigin() != null) {
                if (op.getPaidLocalSourceOrigin() != null)
                    require(web.tosunsaeng.domain.exams.domain.entity.ExamSession.TRANSITION_FREE_V1.equals(op.getPaidLocalSourceOrigin())
                            && op.getPaidLegacySource() == null);
                require(op.getPaidDeletedSource() == null && op.getReplacementSourceSessionId() != null
                        && op.getExpectedMockExamId() != null && op.getPaidSelection().startReason() != null);
                if (op.getPaidSelection().startReason() == StartReason.RESTART_INCOMPLETE)
                    require(op.getExpectedMockExamId().equals(op.getMockExamId()));
            } else require(op.getReplacementSourceSessionId() == null && op.getPaidSelection().startReason() == null);
        }
        return new Request(op.getUserId(), op.getSessionId(), op.getMockExamId(), restart);
    }

    public static void snapshot(ReservationSnapshot s) {
        require(s != null && s.entitlementSource() != null && s.reservationKind() != null && s.reservationStatus() != null);
        uuid(s.operationId(), true); uuid(s.reservationId(), true); uuid(s.attemptGroupId(), true);
        opaque(s.sessionId()); opaque(s.mockExamId()); require(s.expiresAt() != null);
        if (s.reservationStatus() != ReservationStatus.RESERVED) require(s.terminalAt() != null);
        else require(s.terminalAt() == null);
        if (s.reservationStatus() == ReservationStatus.CONFIRMED) require(s.attemptGroupStatus() != null);
        if (s.confirmRejection() != null) require(s.reservationStatus() == ReservationStatus.CANCELED && s.terminalAt() != null);
        if (s.transition() != null) require(s.reservationKind() == BillingReservationKind.INITIAL);
    }

    public static void matches(String operationId, Request request, ReservationSnapshot s) {
        snapshot(s);
        require(Objects.equals(operationId, s.operationId()) && request.sessionId().equals(s.sessionId())
                && request.mockExamId().equals(s.mockExamId()));
        require(s.continuationId() == null && s.continuationReason() == null);
        RestartContext old = request.restartContext();
        if (old == null) {
            require(s.reservationKind() == BillingReservationKind.INITIAL && s.transition() == null);
        } else if (s.reservationKind() == BillingReservationKind.REPLACEMENT) {
            require(old.startReason() == StartReason.RESTART_INCOMPLETE
                    && old.previousAttemptGroupId().equals(s.attemptGroupId()) && s.transition() == null);
        } else {
            require(!old.previousAttemptGroupId().equals(s.attemptGroupId())
                    && new Transition(old.previousAttemptGroupId(), old.previousSessionId()).equals(s.transition()));
        }
    }

    public static void matches(ExamCreationOperation op, ReservationSnapshot s) {
        if (op.isPhoneContinuation()) matchesPhone(op.getOperationId(), phoneRequest(op), s);
        else matches(op.getOperationId(), request(op), s);
        if (op.getPaidDeletedSource() != null) require(s.reservationKind() == BillingReservationKind.INITIAL
                && s.transition() != null && !op.getExpectedAttemptGroupId().equals(s.attemptGroupId()));
        if (op.getReservationId() != null) require(op.getReservationId().equals(s.reservationId()));
        if (op.getAttemptGroupId() != null) require(op.getAttemptGroupId().equals(s.attemptGroupId()));
        if (op.getReservationKind() != null) require(op.getReservationKind() == s.reservationKind());
    }

    static void uuid(String value, boolean v4) {
        require(value != null);
        try {
            UUID id = UUID.fromString(value);
            require(id.toString().equals(value) && (!v4 || id.version() == 4 && id.variant() == 2));
        } catch (IllegalArgumentException invalid) { throw contract(); }
    }
    static void opaque(String value) {
        require(value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= 128
                && value.chars().noneMatch(Character::isISOControl));
    }
    public static void require(boolean valid) { if (!valid) throw contract(); }
    private static BillingClientException contract() { return new BillingClientException(BillingClientException.Category.CONTRACT_ERROR, null); }
}
