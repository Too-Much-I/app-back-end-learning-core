package web.tosunsaeng.domain.exams.rollout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import web.tosunsaeng.domain.exams.application.PaidExamActivationGate;
import web.tosunsaeng.domain.exams.access.AccessEventRolloutGate;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaidExamTestActivationTest {
    private final PaidExamTestIndexCheck indexes = mock(PaidExamTestIndexCheck.class);

    private ApplicationContextRunner base() {
        return new ApplicationContextRunner().withUserConfiguration(
                PaidExamTestActivation.class, PaidExamActivationGate.class, AccessEventRolloutGate.class)
                .withBean(PaidExamTestIndexCheck.class, () -> indexes);
    }

    private ApplicationContextRunner approved(boolean withManifest, boolean ownerEnabled) {
        var owner = mock(UserOwnedTransactionExecutor.class);
        when(owner.enabled()).thenReturn(ownerEnabled);
        var runner = base().withInitializer(c -> c.getEnvironment().setActiveProfiles("test"))
                .withBean(UserOwnedTransactionExecutor.class, () -> owner)
                .withPropertyValues("app.billing.paid-exam.enabled=true",
                        "app.billing.paid-exam.test-activation-approved=true",
                        "app.billing.creation-saga-enabled=true", "app.billing.workload-jwt.sender-enabled=true",
                        "app.billing.access-events.receiver-enabled=true", "app.user-merged.writer-enabled=true",
                        "app.billing.workload-jwt.environment=test", "app.billing.paid-exam.legacy.environment=test",
                        "app.billing.access-events.environment=test");
        return withManifest ? runner.withBean(LegacyExamRuntime.class, () -> new LegacyExamRuntime(
                LegacyExamManifest.build("test", "isolated", "gate-1", List.of()))) : runner;
    }

    @Test void offDoesNotRequireProfilesManifestOwnerOrIndexReads() {
        base().withPropertyValues("app.billing.paid-exam.test-activation-approved=invalid")
                .run(c -> assertThat(c).hasNotFailed());
        verifyNoInteractions(indexes);
    }

    @Test void explicitlyApprovedTestPassesBothGatesWithoutChangingFeatureSettings() {
        approved(true, true).run(c -> assertThat(c).hasNotFailed());
        verify(indexes, times(2)).verify();
    }

    @ParameterizedTest @ValueSource(strings = {"", "local", "staging", "prod", "production", "test,prod", "test,local", "test,staging"})
    void copiedApprovalNeverEnablesNonTestOrMixedProfiles(String profiles) {
        approved(true, true).withInitializer(c -> c.getEnvironment().setActiveProfiles(
                profiles.isEmpty() ? new String[0] : profiles.split(",")))
                .run(c -> assertThat(c).hasFailed());
        verifyNoInteractions(indexes);
    }

    @ParameterizedTest @ValueSource(strings = {"app.billing.paid-exam.test-activation-approved",
            "app.billing.paid-exam.enabled", "app.billing.creation-saga-enabled",
            "app.billing.workload-jwt.sender-enabled", "app.billing.access-events.receiver-enabled",
            "app.user-merged.writer-enabled"})
    void partialEnablementCannotPass(String flag) {
        approved(true, true).withPropertyValues(flag + "=false").run(c -> assertThat(c).hasFailed());
        verifyNoInteractions(indexes);
    }

    @ParameterizedTest @ValueSource(strings = {"app.billing.workload-jwt.environment",
            "app.billing.paid-exam.legacy.environment", "app.billing.access-events.environment"})
    void environmentMismatchFailsBeforeIndexes(String property) {
        approved(true, true).withPropertyValues(property + "=production").run(c -> assertThat(c).hasFailed());
        verifyNoInteractions(indexes);
    }

    @Test void approvalCannotReplaceVerifiedInventory() {
        approved(false, true).run(c -> assertThat(c).hasFailed());
        verifyNoInteractions(indexes);
    }

    @Test void writerFlagCannotReplaceEnabledOwnerExecutor() {
        approved(true, false).run(c -> assertThat(c).hasFailed());
        verifyNoInteractions(indexes);
    }

    @Test void requiredIndexFailureStillStopsStartup() {
        doThrow(new IllegalStateException("required indexes unavailable")).when(indexes).verify();
        approved(true, true).run(c -> assertThat(c).hasFailed());
    }
}
