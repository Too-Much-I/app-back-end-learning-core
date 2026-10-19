package web.tosunsaeng.domain.exams.billing;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.exams.domain.entity.*;
import web.tosunsaeng.domain.exams.domain.entity.PaidExamSelection.StartReason;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PaidReservationProtocolTest {
    static final String USER="00000002-1111-4111-8111-111111111111";
    static final String KEY="00000005-1111-4111-8111-111111111111";
    static final String GROUP="00000003-1111-4111-8111-111111111111";
    ExamCreationOperation operation(String group, String mock, StartReason reason) {
        var op=ExamCreationOperation.prepared(USER,KEY,"new",mock,1,"old",group,"mock-a",Instant.EPOCH);
        op.fixPaidSelection(new PaidExamSelection(reason,List.of(1)));
        return op;
    }
    @Test void requestUsesOnlyFrozenSingleMockAndThreeFieldRestart() {
        var op=operation(GROUP,"mock-b",StartReason.NEW_AFTER_SUBMISSION);
        var request=PaidReservationProtocol.request(op);
        assertThat(request.mockExamId()).isEqualTo("mock-b");
        assertThat(request.restartContext()).isEqualTo(new PaidReservationProtocol.RestartContext(GROUP,"old",StartReason.NEW_AFTER_SUBMISSION));
        op.fixPaidRequestDigest("a".repeat(64));
        op.fixPaidRequestDigest("a".repeat(64));
        assertThatThrownBy(()->op.fixPaidRequestDigest("b".repeat(64))).isInstanceOf(BillingClientException.class);
    }
    @Test void missingGroupDoesNotInferLegacyAndIncompleteCannotSwitchMock() {
        assertThatThrownBy(()->PaidReservationProtocol.request(operation(null,"mock-a",StartReason.RESTART_INCOMPLETE)))
                .isInstanceOf(BillingClientException.class);
        assertThatThrownBy(()->PaidReservationProtocol.request(operation(GROUP,"mock-b",StartReason.RESTART_INCOMPLETE)))
                .isInstanceOf(BillingClientException.class);
    }
    @Test void partialRestartInvalidIdsAndOldSessionReuseFailBeforeSending() {
        assertThatThrownBy(()->new PaidReservationProtocol.RestartContext(GROUP,null,StartReason.NEW_AFTER_COMPLETION))
                .isInstanceOf(BillingClientException.class);
        assertThatThrownBy(()->new PaidReservationProtocol.RestartContext(GROUP,"old",null))
                .isInstanceOf(BillingClientException.class);
        assertThatThrownBy(()->new PaidReservationProtocol.RestartContext("bad","old",StartReason.RESTART_INCOMPLETE))
                .isInstanceOf(BillingClientException.class);
        assertThatThrownBy(()->new PaidReservationProtocol.Request(USER,"old","mock-a",
                new PaidReservationProtocol.RestartContext(GROUP,"old",StartReason.RESTART_INCOMPLETE)))
                .isInstanceOf(BillingClientException.class);
    }
    @Test void phoneRequestDoesNotEnterGeneralRestartProtocol() {
        var op=ExamCreationOperation.prepared(USER,KEY,"new","mock-a",1,null,GROUP,"mock-a",
                web.tosunsaeng.domain.exams.domain.enums.BillingContinuationReason.PHONE_REJOIN,KEY,Instant.EPOCH);
        op.fixPaidSelection(new PaidExamSelection(null,List.of(1)));
        assertThatThrownBy(()->PaidReservationProtocol.request(op)).isInstanceOf(BillingClientException.class);
    }
}
