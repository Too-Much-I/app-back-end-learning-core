package web.tosunsaeng.domain.notifications.provider;

import web.tosunsaeng.domain.notifications.domain.enums.NotificationErrorCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DisabledPushReceiptClient implements PushReceiptClient {

    @Override
    public PushReceiptBatchResult getReceipts(List<String> ticketIds) {
        Map<String, PushReceiptResult> results = new LinkedHashMap<>();
        ticketIds.forEach(ticketId -> results.put(
                ticketId,
                PushReceiptResult.failure(NotificationErrorCode.PUSH_DISABLED)
        ));
        return new PushReceiptBatchResult(results);
    }
}
