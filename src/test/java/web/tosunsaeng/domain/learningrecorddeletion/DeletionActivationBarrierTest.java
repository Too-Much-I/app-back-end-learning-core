package web.tosunsaeng.domain.learningrecorddeletion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import web.tosunsaeng.domain.learningrecorddeletion.config.DeletionActivationBarrier;
import static org.assertj.core.api.Assertions.assertThat;

class DeletionActivationBarrierTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(DeletionActivationBarrier.class);

    @Test void absentFlagsAreOffWithoutAnyExternalDependency() {
        context.run(c -> assertThat(c).hasNotFailed());
    }

    @Test void incompleteRolloutCannotBeEnabledByEnvironmentConfiguration() {
        for (String flag : new String[]{"command-enabled", "read-fence-enabled", "writer-fence-enabled",
                "worker-enabled", "billing-continuation-enabled", "aggregate-enabled"}) {
            context.withPropertyValues("app.learning-record-deletion." + flag + "=true")
                    .run(c -> assertThat(c).hasFailed());
        }
    }
}
