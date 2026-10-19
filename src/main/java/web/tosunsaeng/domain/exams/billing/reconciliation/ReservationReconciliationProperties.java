package web.tosunsaeng.domain.exams.billing.reconciliation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.billing.reconciliation")
public class ReservationReconciliationProperties {
    private boolean enabled;
    // Deliberately no invented operating defaults: required only when enabling the worker.
    private Duration alertMaxAge;
    private Integer alertMaxAttempts;
    private Duration poll = Duration.ofSeconds(10);
    private int batchSize = 20;
    private int concurrency = 2;
    private Duration lease = Duration.ofSeconds(30);
    private Duration attemptTimeout = Duration.ofSeconds(20);
    private Duration precommitStale = Duration.ofMinutes(2);
    private Duration maxAge = Duration.ofHours(24);
    private int maxAttempts = 200;
    private Duration authProbeInterval = Duration.ofMinutes(15);

    public void validate() {
        if (alertMaxAge == null || alertMaxAge.isNegative() || alertMaxAge.isZero()
                || alertMaxAttempts == null || alertMaxAttempts < 1) {
            throw new IllegalStateException("Reservation alert retry limits require explicit operator configuration");
        }
        validateExecutionLimits();
    }

    public void validateExecutionLimits() {
        if (poll == null || poll.isNegative() || poll.isZero()
                || batchSize < 1 || batchSize > 100 || concurrency < 1 || concurrency > batchSize
                || attemptTimeout == null || attemptTimeout.isNegative() || attemptTimeout.isZero()
                || lease == null || lease.compareTo(attemptTimeout.plusSeconds(5)) < 0
                || precommitStale == null || precommitStale.isNegative() || precommitStale.isZero()
                || maxAge == null || maxAge.isNegative() || maxAge.isZero() || maxAttempts < 1
                || authProbeInterval == null || authProbeInterval.isNegative() || authProbeInterval.isZero()) {
            throw new IllegalStateException("Invalid Reservation reconciliation limits");
        }
    }
}
