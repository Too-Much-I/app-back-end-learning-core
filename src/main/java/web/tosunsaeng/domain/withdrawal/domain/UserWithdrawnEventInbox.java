package web.tosunsaeng.domain.withdrawal.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "user_withdrawn_event_inbox")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserWithdrawnEventInbox {

    @Id
    private String eventId;
    private int schemaVersion;
    private String payloadDigest;
    private String userId;
    private Instant withdrawnAt;
    private Instant receivedAt;
    private Instant processedAt;
    private UserWithdrawnInboxStatus status;
    private Instant cleanupAt;
}
