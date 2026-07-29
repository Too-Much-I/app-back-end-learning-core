package web.tosunsaeng.global.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.grading")
public record GradingProperties(
        @NotNull Duration pendingTimeout,
        @NotNull Duration processingTimeout,
        @Min(1) int maxDispatchAttempts,
        @NotNull Duration aiConnectTimeout,
        @NotNull Duration aiReadTimeout,
        @Min(1) int summaryDispatchThreads,
        @Min(1) int summaryDispatchQueueCapacity
) {

    @AssertTrue(message = "app.grading.pending-timeout must be positive")
    public boolean isPendingTimeoutPositive() {
        return pendingTimeout != null && !pendingTimeout.isZero() && !pendingTimeout.isNegative();
    }

    @AssertTrue(message = "app.grading.processing-timeout must be positive")
    public boolean isProcessingTimeoutPositive() {
        return processingTimeout != null && !processingTimeout.isZero() && !processingTimeout.isNegative();
    }

    @AssertTrue(message = "app.grading.ai-connect-timeout must be positive")
    public boolean isAiConnectTimeoutPositive() {
        return aiConnectTimeout != null && !aiConnectTimeout.isZero() && !aiConnectTimeout.isNegative();
    }

    @AssertTrue(message = "app.grading.ai-read-timeout must be positive")
    public boolean isAiReadTimeoutPositive() {
        return aiReadTimeout != null && !aiReadTimeout.isZero() && !aiReadTimeout.isNegative();
    }
}
