package web.tosunsaeng.global.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradingPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void positiveTimeoutsAndAttemptLimitAreValid() {
        GradingProperties properties = new GradingProperties(
                Duration.ofMinutes(1),
                Duration.ofMinutes(3),
                3,
                URI.create("http://test-ai:8000"),
                Duration.ofSeconds(3),
                Duration.ofSeconds(30),
                2,
                100
        );

        assertTrue(validator.validate(properties).isEmpty());
    }

    @Test
    void nonPositiveTimeoutsAndAttemptLimitAreRejected() {
        GradingProperties properties = new GradingProperties(
                Duration.ZERO,
                Duration.ofSeconds(-1),
                0,
                URI.create("ftp://test-ai:8000"),
                Duration.ZERO,
                Duration.ofSeconds(-1),
                0,
                0
        );

        assertEquals(8, validator.validate(properties).size());
    }
}
