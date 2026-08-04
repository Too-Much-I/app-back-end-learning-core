package web.tosunsaeng.domain.notifications.provider;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationErrorCode;
import web.tosunsaeng.global.config.NotificationPushProperties;

final class ExpoProviderSupport {

    private ExpoProviderSupport() {
    }

    static HttpHeaders headers(NotificationPushProperties properties) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        String accessToken = properties.expo().accessToken();
        if (accessToken != null && !accessToken.isBlank()) {
            headers.setBearerAuth(accessToken);
        }
        return headers;
    }

    static PushProviderException sanitizedException(RestClientException exception) {
        if (exception instanceof HttpStatusCodeException statusException) {
            if (statusException.getStatusCode().value() == 429) {
                return new PushProviderException(NotificationErrorCode.PROVIDER_RATE_LIMITED);
            }
            if (statusException.getStatusCode().value() == 401
                    || statusException.getStatusCode().value() == 403) {
                return new PushProviderException(NotificationErrorCode.INVALID_CREDENTIALS);
            }
            if (statusException.getStatusCode().is5xxServerError()) {
                return new PushProviderException(NotificationErrorCode.PROVIDER_UNAVAILABLE);
            }
            return new PushProviderException(NotificationErrorCode.MALFORMED_REQUEST);
        }
        if (exception instanceof ResourceAccessException) {
            return new PushProviderException(NotificationErrorCode.PROVIDER_TIMEOUT);
        }
        return new PushProviderException(NotificationErrorCode.PROVIDER_UNAVAILABLE);
    }

    static NotificationErrorCode mapProviderError(String providerError) {
        if (providerError == null || providerError.isBlank()) {
            return NotificationErrorCode.PROVIDER_RESPONSE_INVALID;
        }
        return switch (providerError) {
            case "DeviceNotRegistered" -> NotificationErrorCode.DEVICE_NOT_REGISTERED;
            case "MessageRateExceeded" -> NotificationErrorCode.MESSAGE_RATE_EXCEEDED;
            case "InvalidCredentials" -> NotificationErrorCode.INVALID_CREDENTIALS;
            case "MismatchSenderId" -> NotificationErrorCode.MISMATCH_SENDER_ID;
            case "MessageTooBig", "DeveloperError" -> NotificationErrorCode.MALFORMED_REQUEST;
            default -> NotificationErrorCode.UNKNOWN_PROVIDER_ERROR;
        };
    }
}
