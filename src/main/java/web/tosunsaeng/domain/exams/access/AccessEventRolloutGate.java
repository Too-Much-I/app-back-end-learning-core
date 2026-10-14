package web.tosunsaeng.domain.exams.access;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Wiring is implemented, but incomplete legacy/owner/purge rollout must not be enabled by a flag alone. */
@Component
public final class AccessEventRolloutGate implements InitializingBean {
    private final boolean enabled;
    public AccessEventRolloutGate(@Value("${app.billing.access-events.receiver-enabled:false}") boolean enabled){this.enabled=enabled;}
    @Override public void afterPropertiesSet(){
        if(enabled) throw new IllegalStateException("Billing access activation requires legacy lifecycle and V07 rollout verification");
    }
}
