package web.tosunsaeng.domain.exams.application;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Keep until MEMBER/lifecycle/rollout gates and public integration are implemented and verified. */
@Component
public class PaidExamActivationGate implements InitializingBean {
    private final boolean enabled;
    public PaidExamActivationGate(@Value("${app.billing.paid-exam.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }
    @Override public void afterPropertiesSet() {
        if (enabled) throw new IllegalStateException("Paid exam activation requires remaining MEMBER/lifecycle and rollout gates");
    }
}
