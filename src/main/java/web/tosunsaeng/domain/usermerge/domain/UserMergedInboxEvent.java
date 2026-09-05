package web.tosunsaeng.domain.usermerge.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "user_merged_inbox_events")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserMergedInboxEvent {

    @Id
    private String eventId;
    private int schemaVersion;
    private String payloadDigest;
    private String sourceUserId;
    private String targetUserId;
    private Instant occurredAt;
    private Instant receivedAt;
    private Instant processedAt;
    private UserMergedInboxStatus status;
}
