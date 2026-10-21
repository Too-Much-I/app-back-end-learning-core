package web.tosunsaeng.domain.exams.reanswer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import web.tosunsaeng.domain.exams.billing.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReanswerConfigurationTest {
    final ApplicationContextRunner context=new ApplicationContextRunner().withUserConfiguration(ReanswerConfiguration.class);
    @Test void disabledDoesNotRequireCredentialsOrParseAddress() {
        context.withPropertyValues("app.billing.saga.base-url=not a uri").run(c-> {
            assertThat(c).hasNotFailed(); assertThat(c).doesNotHaveBean(ReanswerEligibilityClient.class);
        });
    }
    @Test void enabledRequiresSenderAndOwnerTransactions() {
        var jwt=new WorkloadJwtProperties();
        var saga=new BillingSagaProperties(); saga.setBaseUrl("https://billing.invalid");
        var enabled=context.withPropertyValues("app.billing.reanswer.enabled=true")
                .withBean(WorkloadJwtProperties.class,()->jwt).withBean(BillingSagaProperties.class,()->saga)
                .withBean(BillingWorkloadTokenIssuer.class,()->mock(BillingWorkloadTokenIssuer.class));
        enabled.run(c->assertThat(c).hasFailed());
        jwt.setSenderEnabled(true);
        enabled.run(c->assertThat(c).hasFailed());
        enabled.withPropertyValues("app.user-merged.writer-enabled=true").run(c-> {
            assertThat(c).hasNotFailed(); assertThat(c).hasSingleBean(ReanswerEligibilityClient.class);
        });
        saga.setBaseUrl("http://billing.invalid");
        enabled.withPropertyValues("app.user-merged.writer-enabled=true").run(c->assertThat(c).hasFailed());
    }
}
