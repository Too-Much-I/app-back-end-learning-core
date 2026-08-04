package web.tosunsaeng.global.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.notification.push")
public record NotificationPushProperties(
        boolean enabled,
        @NotBlank String provider,
        @Valid @NotNull Expo expo,
        @NotNull Duration workerDelay,
        @NotNull Duration receiptDelay,
        @NotNull Duration leaseDuration,
        @Min(1) int maxAttempts,
        @NotNull Duration initialBackoff,
        @NotNull Duration maxBackoff,
        @Min(1) @Max(100) int batchSize,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {

    public record Expo(
            @NotBlank String sendUrl,
            @NotBlank String receiptUrl,
            String accessToken,
            boolean accessTokenRequired
    ) {
    }

    @AssertTrue(message = "app.notification.push.provider must be expo when push is enabled")
    public boolean isSupportedProvider() {
        return !enabled || "expo".equals(provider);
    }

    @AssertTrue(message = "Expo access token is required by the active push configuration")
    public boolean isRequiredAccessTokenPresent() {
        return !enabled || !expo.accessTokenRequired()
                || (expo.accessToken() != null && !expo.accessToken().isBlank());
    }

    @AssertTrue(message = "Expo endpoints must use HTTP(S)")
    public boolean areExpoEndpointsValid() {
        return isHttpUrl(expo.sendUrl()) && isHttpUrl(expo.receiptUrl());
    }

    @AssertTrue(message = "Notification durations must be positive")
    public boolean areDurationsPositive() {
        return positive(workerDelay)
                && positive(receiptDelay)
                && positive(leaseDuration)
                && positive(initialBackoff)
                && positive(maxBackoff)
                && positive(connectTimeout)
                && positive(readTimeout)
                && maxBackoff.compareTo(initialBackoff) >= 0;
    }

    private static boolean isHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            String scheme = URI.create(value).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException invalidUri) {
            return false;
        }
    }

    private static boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }
}
