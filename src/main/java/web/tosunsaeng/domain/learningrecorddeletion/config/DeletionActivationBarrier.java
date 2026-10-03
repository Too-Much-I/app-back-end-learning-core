package web.tosunsaeng.domain.learningrecorddeletion.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Partial rollback may stop commands, but must not disable the existing read/write fences. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DeletionActivationBarrier.Flags.class)
public class DeletionActivationBarrier implements InitializingBean {
    private final Flags flags;

    public DeletionActivationBarrier(Flags flags) { this.flags = flags; }

    @Override public void afterPropertiesSet() {
        boolean any = flags.commandEnabled() || flags.readFenceEnabled() || flags.writerFenceEnabled()
                || flags.workerEnabled() || flags.billingContinuationEnabled() || flags.aggregateEnabled();
        if (any && (!flags.readFenceEnabled() || !flags.writerFenceEnabled() || !flags.billingContinuationEnabled()))
            throw new IllegalStateException("Deletion requires read/writer fences and Billing evidence preservation together");
        if (flags.commandEnabled() && !flags.workerEnabled())
            throw new IllegalStateException("Deletion commands require a cleanup worker");
    }

    @ConfigurationProperties("app.learning-record-deletion")
    public record Flags(boolean commandEnabled, boolean readFenceEnabled, boolean writerFenceEnabled,
                        boolean workerEnabled, boolean billingContinuationEnabled, boolean aggregateEnabled) {}
}
