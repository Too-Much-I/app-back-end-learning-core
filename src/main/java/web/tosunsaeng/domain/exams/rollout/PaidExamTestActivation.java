package web.tosunsaeng.domain.exams.rollout;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import java.util.Arrays;
import java.util.List;

/** Explicit test-only rollout permission. Never grants staging/production permission or installs data. */
@Component
public final class PaidExamTestActivation {
    private final Environment environment;
    private final ObjectProvider<LegacyExamRuntime> legacy;
    private final ObjectProvider<UserOwnedTransactionExecutor> owners;
    private final PaidExamTestIndexCheck indexes;

    public PaidExamTestActivation(Environment environment, ObjectProvider<LegacyExamRuntime> legacy,
            ObjectProvider<UserOwnedTransactionExecutor> owners, PaidExamTestIndexCheck indexes) {
        this.environment = environment;
        this.legacy = legacy;
        this.owners = owners;
        this.indexes = indexes;
    }

    public void requireReady() {
        // Reject defaults, mixed profiles and all non-test names, even when approval was accidentally copied.
        if (!Arrays.equals(environment.getActiveProfiles(), new String[]{"test"})) throw denied();
        for (String flag : List.of("app.billing.paid-exam.test-activation-approved",
                "app.billing.paid-exam.enabled", "app.billing.creation-saga-enabled",
                "app.billing.workload-jwt.sender-enabled", "app.billing.access-events.receiver-enabled",
                "app.user-merged.writer-enabled")) {
            if (!"true".equals(environment.getProperty(flag))) throw denied();
        }
        for (String property : List.of("app.billing.workload-jwt.environment",
                "app.billing.paid-exam.legacy.environment", "app.billing.access-events.environment")) {
            if (!"test".equals(environment.getProperty(property))) throw denied();
        }
        // The runtime bean is created only after verifySealed(expected); approval alone cannot substitute for it.
        if (legacy.getIfAvailable() == null) throw denied();
        var owner = owners.getIfAvailable();
        if (owner == null || !owner.enabled()) throw denied();
        indexes.verify();
    }

    private static IllegalStateException denied() {
        return new IllegalStateException("Paid exam test activation prerequisites are not satisfied");
    }
}
