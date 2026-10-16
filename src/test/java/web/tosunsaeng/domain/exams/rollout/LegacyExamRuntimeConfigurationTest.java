package web.tosunsaeng.domain.exams.rollout;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LegacyExamRuntimeConfigurationTest {
    @Test void offDoesNotParseInvalidCountOrReadMongo() {
        var mongo=mock(MongoTemplate.class);
        new ApplicationContextRunner().withUserConfiguration(LegacyExamRuntimeConfiguration.class)
                .withBean(MongoTemplate.class,()->mongo)
                .withPropertyValues("app.billing.paid-exam.enabled=false","app.billing.paid-exam.legacy.count=invalid")
                .run(context->{ assertThat(context).hasNotFailed().doesNotHaveBean(LegacyExamRuntime.class);
                    verify(mongo,never()).getDb(); verify(mongo,never()).getCollection(anyString()); });
    }
    @Test void missingApprovedInventoryCannotStartOn() {
        var mongo=mock(MongoTemplate.class);
        new ApplicationContextRunner().withUserConfiguration(LegacyExamRuntimeConfiguration.class)
                .withBean(MongoTemplate.class,()->mongo)
                .withPropertyValues("app.billing.paid-exam.enabled=true")
                .run(context->assertThat(context).hasFailed());
    }
    @Test void wrongEnvironmentFailsBeforeAnyInventoryRead() {
        var mongo=mock(MongoTemplate.class);
        new ApplicationContextRunner().withUserConfiguration(LegacyExamRuntimeConfiguration.class)
                .withBean(MongoTemplate.class,()->mongo)
                .withPropertyValues("app.billing.paid-exam.enabled=true","app.billing.workload-jwt.environment=test",
                        "app.billing.paid-exam.legacy.environment=production")
                .run(context->{ assertThat(context).hasFailed(); verify(mongo,never()).getDb(); });
    }
}
