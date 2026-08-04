package web.tosunsaeng.domain.notifications.application;

import org.springframework.stereotype.Component;
import web.tosunsaeng.global.config.NotificationPushProperties;

import java.time.Duration;

@Component
public class NotificationBackoffPolicy {

    private final Duration initialBackoff;
    private final Duration maxBackoff;

    public NotificationBackoffPolicy(NotificationPushProperties properties) {
        this.initialBackoff = properties.initialBackoff();
        this.maxBackoff = properties.maxBackoff();
    }

    public Duration delayForAttempt(int attemptCount) {
        int exponent = Math.max(0, Math.min(attemptCount - 1, 30));
        long multiplier = 1L << exponent;
        Duration candidate;
        try {
            candidate = initialBackoff.multipliedBy(multiplier);
        } catch (ArithmeticException overflow) {
            return maxBackoff;
        }
        return candidate.compareTo(maxBackoff) > 0 ? maxBackoff : candidate;
    }
}
