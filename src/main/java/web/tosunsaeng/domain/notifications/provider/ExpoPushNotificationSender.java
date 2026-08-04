package web.tosunsaeng.domain.notifications.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationErrorCode;
import web.tosunsaeng.global.config.NotificationPushProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExpoPushNotificationSender implements PushNotificationSender {

    private static final int EXPO_BATCH_LIMIT = 100;

    private final RestTemplate restTemplate;
    private final NotificationPushProperties properties;

    public ExpoPushNotificationSender(
            RestTemplate restTemplate,
            NotificationPushProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public PushTicketBatchResult send(List<PushMessage> messages) {
        if (messages.isEmpty()) {
            return new PushTicketBatchResult(List.of());
        }
        if (messages.size() > EXPO_BATCH_LIMIT) {
            throw new IllegalArgumentException("Expo push batch must contain at most 100 messages");
        }

        List<Map<String, Object>> body = messages.stream().map(this::toRequest).toList();
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    properties.expo().sendUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(body, ExpoProviderSupport.headers(properties)),
                    JsonNode.class
            );
            return parseResponse(response.getBody(), messages.size());
        } catch (RestClientException providerFailure) {
            throw ExpoProviderSupport.sanitizedException(providerFailure);
        }
    }

    private Map<String, Object> toRequest(PushMessage message) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("to", message.expoPushToken());
        request.put("sound", message.sound());
        request.put("channelId", message.channelId());
        request.put("title", message.title());
        request.put("body", message.body());
        request.put("data", message.data());
        return request;
    }

    private PushTicketBatchResult parseResponse(JsonNode root, int expectedSize) {
        JsonNode data = root == null ? null : root.get("data");
        List<JsonNode> tickets = new ArrayList<>();
        if (data != null && data.isArray()) {
            data.forEach(tickets::add);
        } else if (data != null && data.isObject() && expectedSize == 1) {
            tickets.add(data);
        }
        if (tickets.size() != expectedSize) {
            return repeatedFailure(expectedSize, NotificationErrorCode.PROVIDER_RESPONSE_INVALID);
        }

        List<PushTicketResult> results = new ArrayList<>(expectedSize);
        for (JsonNode ticket : tickets) {
            String status = textValue(ticket, "status");
            if ("ok".equals(status)) {
                String ticketId = textValue(ticket, "id");
                results.add(ticketId == null || ticketId.isBlank()
                        ? PushTicketResult.failure(NotificationErrorCode.PROVIDER_RESPONSE_INVALID)
                        : PushTicketResult.ticket(ticketId));
                continue;
            }
            String providerError = ticket.path("details").path("error").isTextual()
                    ? ticket.path("details").path("error").textValue()
                    : null;
            results.add(PushTicketResult.failure(ExpoProviderSupport.mapProviderError(providerError)));
        }
        return new PushTicketBatchResult(results);
    }

    private PushTicketBatchResult repeatedFailure(int size, NotificationErrorCode errorCode) {
        List<PushTicketResult> results = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            results.add(PushTicketResult.failure(errorCode));
        }
        return new PushTicketBatchResult(results);
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }
}
