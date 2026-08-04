package web.tosunsaeng.domain.notifications.provider;

import java.util.List;

public interface PushReceiptClient {

    PushReceiptBatchResult getReceipts(List<String> ticketIds);
}
