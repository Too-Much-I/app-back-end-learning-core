package web.tosunsaeng.domain.notifications.provider;

import java.util.List;

public record PushTicketBatchResult(List<PushTicketResult> results) {

    public PushTicketBatchResult {
        results = List.copyOf(results);
    }
}
