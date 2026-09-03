package web.tosunsaeng.domain.exams.billing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BillingSagaConfigurationValidatorTest {

    @Test
    void disabledConfigurationDoesNotRequireEndpoint() {
        BillingSagaProperties properties = new BillingSagaProperties();
        assertDoesNotThrow(() -> new BillingSagaConfigurationValidator(
                properties, new MockEnvironment()).run(new DefaultApplicationArguments()));
    }

    @Test
    void phoneContinuationCannotBeEnabledWithoutCreationSaga() {
        BillingSagaProperties properties = new BillingSagaProperties();
        properties.setPhoneContinuationEnabled(true);

        assertThrows(
                IllegalStateException.class,
                () -> new BillingSagaConfigurationValidator(
                        properties, new MockEnvironment()
                ).run(new DefaultApplicationArguments())
        );
    }

    @Test
    void enabledStagingRequiresHttpsAndApprovedRegion() {
        BillingSagaProperties properties = enabledProperties("http://billing.internal");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("staging");

        assertThrows(
                IllegalStateException.class,
                () -> new BillingSagaConfigurationValidator(properties, environment)
                        .run(new DefaultApplicationArguments())
        );

        properties.setBaseUrl("https://billing.internal");
        properties.setRegion("us-east-1");
        assertThrows(
                IllegalStateException.class,
                () -> new BillingSagaConfigurationValidator(properties, environment)
                        .run(new DefaultApplicationArguments())
        );
    }

    @Test
    void enabledStagingAcceptsHttpsApprovedRegionAndPositiveTimeouts() {
        BillingSagaProperties properties = enabledProperties("https://billing.internal");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("staging");

        assertDoesNotThrow(() -> new BillingSagaConfigurationValidator(properties, environment)
                .run(new DefaultApplicationArguments()));
    }

    private static BillingSagaProperties enabledProperties(String baseUrl) {
        BillingSagaProperties properties = new BillingSagaProperties();
        properties.setCreationSagaEnabled(true);
        properties.setBaseUrl(baseUrl);
        properties.setRegion("ap-northeast-2");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(1));
        return properties;
    }
}
