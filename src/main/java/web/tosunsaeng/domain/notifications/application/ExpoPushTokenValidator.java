package web.tosunsaeng.domain.notifications.application;

import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.notifications.exception.NotificationException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

@Component
public class ExpoPushTokenValidator {

    static final int MAX_TOKEN_LENGTH = 1024;
    private static final String LEGACY_PREFIX = "ExponentPushToken[";
    private static final String CURRENT_PREFIX = "ExpoPushToken[";

    public void validate(String token) {
        if (!isValid(token)) {
            throw new NotificationException(ErrorStatus._NOTIFICATION_DEVICE_INVALID_REQUEST);
        }
    }

    public boolean isValid(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return false;
        }
        if (!token.equals(token.trim()) || !token.endsWith("]")) {
            return false;
        }
        int prefixLength;
        if (token.startsWith(LEGACY_PREFIX)) {
            prefixLength = LEGACY_PREFIX.length();
        } else if (token.startsWith(CURRENT_PREFIX)) {
            prefixLength = CURRENT_PREFIX.length();
        } else {
            return false;
        }
        String value = token.substring(prefixLength, token.length() - 1);
        if (value.isBlank() || value.indexOf('[') >= 0 || value.indexOf(']') >= 0) {
            return false;
        }
        return value.chars().noneMatch(character -> Character.isWhitespace(character)
                || Character.isISOControl(character));
    }
}
