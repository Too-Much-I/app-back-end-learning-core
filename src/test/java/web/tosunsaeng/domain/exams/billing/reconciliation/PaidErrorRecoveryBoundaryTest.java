package web.tosunsaeng.domain.exams.billing.reconciliation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;
import web.tosunsaeng.domain.exams.billing.BillingClientException;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PaidErrorRecoveryBoundaryTest {
    @Test void unsupportedPaidLifecycleEvidenceQuarantinesWithoutCircuitRetryOrGuardRelease() {
        var store = mock(ReservationRecoveryStore.class);
        var circuit = mock(ReservationAuthCircuit.class);
        var service = new BillingReservationReconciliationService(null,store,circuit,null,null,null,null,null,
                new StaticListableBeanFactory().getBeanProvider(io.micrometer.tracing.Tracer.class));
        for (var category : List.of(BillingClientException.Category.MEMBER_EVIDENCE_PENDING,
                BillingClientException.Category.PRE_RESERVE_REJECTED,BillingClientException.Category.ENTITLEMENT_REVOKED,
                BillingClientException.Category.TRANSITION_CONTEXT_CONFLICT)) {
            String outcome = ReflectionTestUtils.invokeMethod(service,"handleBilling",null,new BillingClientException(category,1));
            assertThat(outcome).isEqualTo("needs_review");
        }
        String outcome = ReflectionTestUtils.invokeMethod(service,"handleBilling",null,
                new BillingClientException(BillingClientException.Category.OWNER_BLOCKED,null));
        assertThat(outcome).isEqualTo("blocked_owner");
        verify(store,times(4)).quarantine(null,ReservationRecovery.Status.NEEDS_REVIEW,"state_inconsistent");
        verify(store).quarantine(null,ReservationRecovery.Status.BLOCKED_OWNER,"owner_blocked");
        verifyNoMoreInteractions(store); verifyNoInteractions(circuit);
    }
}
