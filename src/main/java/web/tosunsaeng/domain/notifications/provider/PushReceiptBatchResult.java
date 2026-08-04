package web.tosunsaeng.domain.notifications.provider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record PushReceiptBatchResult(Map<String, PushReceiptResult> results) {

    public PushReceiptBatchResult {
        results = Collections.unmodifiableMap(new LinkedHashMap<>(results));
    }
}
