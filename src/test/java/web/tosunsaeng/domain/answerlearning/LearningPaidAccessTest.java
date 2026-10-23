package web.tosunsaeng.domain.answerlearning;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import web.tosunsaeng.domain.exams.reanswer.*;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static web.tosunsaeng.domain.answerlearning.LearningFixtures.*;

class LearningPaidAccessTest {
    ReanswerEligibilityClient client=mock(ReanswerEligibilityClient.class);
    Clock clock=mock(Clock.class);
    AtomicLong nanos=new AtomicLong();
    LearningPaidAccess access=new LearningPaidAccess(client,clock,nanos::get,t -> {});
    ReanswerApproval proof(Instant checked,Instant until) {
        return new ReanswerApproval(ReanswerApproval.TYPE,ReanswerApproval.REVISION,OWNER,checked.toString(),until.toString());
    }
    @Test void freshLookupEveryRequestAndUseTimeExpiry() {
        when(clock.instant()).thenReturn(NOW);
        when(client.check(OWNER)).thenReturn(proof(NOW,NOW.plusSeconds(5)));
        Runnable first=access.prepare(OWNER); access.prepare(OWNER).run();
        verify(client,times(2)).check(OWNER);
        when(clock.instant()).thenReturn(NOW.plusSeconds(5));
        assertThatThrownBy(first::run).hasMessage("BILLING_TEMPORARILY_UNAVAILABLE");
    }
    @Test void futureClockAndMonotonicExpiryAreDistinct() {
        when(clock.instant()).thenReturn(NOW);
        when(client.check(OWNER)).thenReturn(proof(NOW.plusNanos(1),NOW.plusSeconds(5)));
        assertThatThrownBy(() -> access.prepare(OWNER)).hasMessage("COMMON500");
        when(client.check(OWNER)).thenReturn(proof(NOW,NOW.plusSeconds(5)));
        Runnable check=access.prepare(OWNER); nanos.set(5_000_000_000L);
        assertThatThrownBy(check::run).hasMessage("BILLING_TEMPORARILY_UNAVAILABLE");
    }
    @Test void paidDenialWithdrawalPendingAndContractErrorsKeepTheirStatus() {
        for (var status:java.util.List.of(ErrorStatus._ENTITLEMENT_INSUFFICIENT,ErrorStatus._ACCOUNT_WITHDRAWN,
                ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE)) {
            doThrow(new ExamsException(status)).when(client).check(OWNER);
            assertThatThrownBy(() -> access.prepare(OWNER)).isInstanceOfSatisfying(LearningFailure.class,
                    f -> assertThat(f.status).isEqualTo(status.getReasonHttpStatus().getHttpStatus().value()));
        }
        doThrow(new IllegalStateException("synthetic")).when(client).check(OWNER);
        assertThatThrownBy(() -> access.prepare(OWNER)).hasMessage("COMMON500");
    }
}
