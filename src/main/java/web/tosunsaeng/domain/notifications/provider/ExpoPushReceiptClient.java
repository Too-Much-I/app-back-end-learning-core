package web.tosunsaeng.domain.notifications.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationErrorCode;
import web.tosunsaeng.global.config.NotificationPushProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExpoPushReceiptClient implements PushReceiptClient {

    private final RestTemplate restTemplate;
    private final NotificationPushProperties properties;

    public ExpoPushReceiptClient(
            RestTemplate restTemplate,
            NotificationPushProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public PushReceiptBatchResult getReceipts(List<String> ticketIds) {
        if (ticketIds.isEmpty()) {
            return new PushReceiptBatchResult(Map.of());
        }
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    properties.expo().receiptUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("ids", List.copyOf(ticketIds)),
                            ExpoProviderSupport.headers(properties)),
                    JsonNode.class
            );
            return parseResponse(response.getBody(), ticketIds);
        } catch (RestClientException providerFailure) {
            throw ExpoProviderSupport.sanitizedException(providerFailure);
        }
    }

    private PushReceiptBatchResult parseResponse(JsonNode root, List<String> ticketIds) {
        JsonNode data = root == null ? null : root.get("data");
        Map<String, PushReceiptResult> results = new LinkedHashMap<>();
        for (String ticketId : ticketIds) {
            JsonNode receipt = data != null && data.isObject() ? data.get(ticketId) : null;
            if (receipt == null || !receipt.isObject()) {
                results.put(ticketId, PushReceiptResult.failure(NotificationErrorCode.RECEIPT_NOT_READY));
                continue;
            }
            String status = receipt.path("status").isTextual()
                    ? receipt.path("status").textValue()
                    : null;
            if ("ok".equals(status)) {
                results.put(ticketId, PushReceiptResult.sent());
                continue;
            }
            String providerError = receipt.path("details").path("error").isTextual()
                    ? receipt.path("details").path("error").textValue()
                    : null;
            results.put(ticketId, PushReceiptResult.failure(
                    ExpoProviderSupport.mapProviderError(providerError)
            ));
        }
        return new PushReceiptBatchResult(results);
    }
}
