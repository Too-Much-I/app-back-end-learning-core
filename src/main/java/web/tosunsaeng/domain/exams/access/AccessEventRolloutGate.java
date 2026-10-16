package web.tosunsaeng.domain.exams.access;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Default closed; shared readiness permits an explicit test-only exception, not a general rollout. */
@Component
public final class AccessEventRolloutGate implements InitializingBean {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private web.tosunsaeng.domain.exams.rollout.PaidExamTestActivation testActivation;
    private final boolean enabled;
    public AccessEventRolloutGate(@Value("${app.billing.access-events.receiver-enabled:false}") boolean enabled){this.enabled=enabled;}
    @Override public void afterPropertiesSet(){
        if(enabled && testActivation!=null){testActivation.requireReady();return;}
        if(enabled) throw new IllegalStateException("Billing access activation requires legacy lifecycle and V07 rollout verification");
    }
}
