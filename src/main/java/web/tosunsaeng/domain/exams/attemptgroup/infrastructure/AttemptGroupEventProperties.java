package web.tosunsaeng.domain.exams.attemptgroup.infrastructure;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.attempt-group-events")
public record AttemptGroupEventProperties(
        boolean writerEnabled,
        boolean publisherEnabled,
        String billingBaseUrl,
        String awsRegion,
        @NotNull Duration gradingDeadline,
        @NotNull Duration pollInterval,
        @Min(1) int batchSize,
        @NotNull Duration leaseDuration,
        @NotNull Duration authProbeInterval,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration deliveredRetention,
        @NotNull Duration deadLetterRetention
) {
    @AssertTrue(message = "attempt-group event durations must be positive")
    public boolean validDurations() {
        return positive(gradingDeadline) && positive(pollInterval) && positive(leaseDuration)
                && positive(authProbeInterval) && positive(connectTimeout) && positive(readTimeout)
                && positive(deliveredRetention) && positive(deadLetterRetention);
    }

    private static boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }
}
