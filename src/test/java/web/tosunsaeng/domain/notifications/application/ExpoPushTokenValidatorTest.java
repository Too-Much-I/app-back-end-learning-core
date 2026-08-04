package web.tosunsaeng.domain.notifications.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpoPushTokenValidatorTest {

    private final ExpoPushTokenValidator validator = new ExpoPushTokenValidator();

    @Test
    void supportsBothExpoTokenPrefixes() {
        assertTrue(validator.isValid("ExponentPushToken[placeholder-value]"));
        assertTrue(validator.isValid("ExpoPushToken[placeholder-value]"));
    }

    @Test
    void rejectsBlankUnclosedUnsupportedAndEmptyTokens() {
        assertFalse(validator.isValid(""));
        assertFalse(validator.isValid("ExpoPushToken[placeholder-value"));
        assertFalse(validator.isValid("pushToken[placeholder-value]"));
        assertFalse(validator.isValid("ExpoPushToken[]"));
        assertFalse(validator.isValid("ExpoPushToken[has whitespace]"));
    }

    @Test
    void rejectsOverlongTokenWithoutDependingOnProviderInternals() {
        String token = "ExpoPushToken[" + "a".repeat(ExpoPushTokenValidator.MAX_TOKEN_LENGTH) + "]";
        assertFalse(validator.isValid(token));
    }
}
