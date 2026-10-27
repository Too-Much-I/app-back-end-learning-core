package web.tosunsaeng.domain.promotion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class PromotionConfigurationTest {
    @Test void defaultOffDoesNotRequireDatabaseOrS3() {
        new ApplicationContextRunner().withUserConfiguration(PromotionConfiguration.class,PromotionController.class)
                .run(context -> { assertThat(context).hasNotFailed(); assertThat(context).doesNotHaveBean(PromotionService.class);
                    assertThat(context).doesNotHaveBean(PromotionController.class); });
    }
    @Test void enabledWithoutDependenciesFailsStartup() {
        new ApplicationContextRunner().withUserConfiguration(PromotionConfiguration.class)
                .withPropertyValues("app.promotion.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
