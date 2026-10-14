package web.tosunsaeng.domain.exams.application;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Remove only after P2/P3 wire and confirm-first finalization are implemented and verified. */
@Component
public class PaidExamActivationGate implements InitializingBean {
    private final boolean enabled;
    public PaidExamActivationGate(@Value("${app.billing.paid-exam.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }
    @Override public void afterPropertiesSet() {
        if (enabled) throw new IllegalStateException("Paid exam activation requires P2/P3; P1 preparation only");
    }
}
