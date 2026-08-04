package web.tosunsaeng.domain.notifications.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationBackoffPolicyTest {

    @Test
    void retryDelayDoublesAndCapsAtConfiguredMaximum() {
        NotificationBackoffPolicy policy = new NotificationBackoffPolicy(
                NotificationOutboxWorkerTest.properties()
        );

        assertEquals(Duration.ofSeconds(30), policy.delayForAttempt(1));
        assertEquals(Duration.ofMinutes(1), policy.delayForAttempt(2));
        assertEquals(Duration.ofMinutes(2), policy.delayForAttempt(3));
        assertEquals(Duration.ofMinutes(4), policy.delayForAttempt(4));
        assertEquals(Duration.ofMinutes(30), policy.delayForAttempt(20));
    }
}
