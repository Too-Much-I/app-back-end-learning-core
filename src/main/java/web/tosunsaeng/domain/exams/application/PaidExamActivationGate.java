package web.tosunsaeng.domain.exams.application;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Default closed; only explicitly approved environment-specific readiness may pass. */
@Component
public class PaidExamActivationGate implements InitializingBean {
    private final boolean enabled;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private web.tosunsaeng.domain.exams.rollout.PaidExamTestActivation testActivation;
    public PaidExamActivationGate(@Value("${app.billing.paid-exam.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }
    @Override public void afterPropertiesSet() {
        if (enabled && testActivation != null) { testActivation.requireReady(); return; }
        if (enabled) throw new IllegalStateException("Paid exam activation requires remaining MEMBER/lifecycle and rollout gates");
    }
}
