package web.tosunsaeng.domain.exams.billing;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventProperties;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

class BillingWorkloadStartupTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({BillingSagaProperties.class, AttemptGroupEventProperties.class})
    static class Properties {}

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(Properties.class, BillingWorkloadConfiguration.class)
                .withPropertyValues("app.billing.workload-jwt.environment=test",
                        "app.billing.workload-jwt.private-jwk=" + WorkloadTestKeys.properties().getPrivateJwk(),
                        "app.billing.base-url=https://billing.test", "app.attempt-group-events.billing-base-url=https://billing.test/",
                        "app.attempt-group-events.grading-deadline=PT30M", "app.attempt-group-events.poll-interval=PT1S",
                        "app.attempt-group-events.batch-size=20", "app.attempt-group-events.lease-duration=PT30S",
                        "app.attempt-group-events.auth-probe-interval=PT15M", "app.attempt-group-events.connect-timeout=PT2S",
                        "app.attempt-group-events.read-timeout=PT5S", "app.attempt-group-events.delivered-retention=P30D",
                        "app.attempt-group-events.dead-letter-retention=P90D");
    }

    @org.junit.jupiter.api.TestFactory
    Stream<DynamicTest> actualBoundContextChecks64FlagCombinations() {
        return IntStream.range(0, 64).mapToObj(bits -> DynamicTest.dynamicTest("flags-" + bits, () -> {
            boolean j=(bits&32)!=0, s=(bits&16)!=0, h=(bits&8)!=0, r=(bits&4)!=0, w=(bits&2)!=0, p=(bits&1)!=0;
            boolean allowed=(!h||s)&&(!r||s)&&(j||(!s&&!p));
            runner().withPropertyValues("app.billing.workload-jwt.sender-enabled="+j,
                    "app.billing.creation-saga-enabled="+s, "app.billing.phone-continuation-enabled="+h,
                    "app.billing.reconciliation.enabled="+r, "app.attempt-group-events.writer-enabled="+w,
                    "app.attempt-group-events.publisher-enabled="+p).run(context -> {
                if (allowed) assertThat(context).hasNotFailed().hasSingleBean(BillingWorkloadTokenIssuer.class);
                else assertThat(context).hasFailed();
            });
        }));
    }

    @Test void disabledIgnoresInvalidKeyWhileEnabledRejectsIt() {
        runner().withPropertyValues("app.billing.workload-jwt.private-jwk=invalid")
                .run(c -> assertThat(c).hasNotFailed());
        runner().withPropertyValues("app.billing.workload-jwt.sender-enabled=true", "app.billing.workload-jwt.private-jwk=invalid")
                .run(c -> assertThat(c).hasFailed());
    }

    @Test void originsAndTimeoutsAreCheckedBeforeAnySender() {
        for (String invalid : new String[]{"app.billing.base-url=http://billing.test",
                "app.attempt-group-events.billing-base-url=https://other.test", "app.billing.read-timeout=PT0S",
                "app.billing.base-url=https://billing.test/path", "app.attempt-group-events.connect-timeout=PT0S"}) {
            runner().withPropertyValues("app.billing.workload-jwt.sender-enabled=true",
                    "app.billing.creation-saga-enabled=true", "app.attempt-group-events.publisher-enabled=true", invalid)
                    .run(c -> assertThat(c).hasFailed());
        }
    }
}
