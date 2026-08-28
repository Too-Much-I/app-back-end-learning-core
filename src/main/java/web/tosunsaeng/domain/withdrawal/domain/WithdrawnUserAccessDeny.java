package web.tosunsaeng.domain.withdrawal.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "withdrawn_user_access_denies")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawnUserAccessDeny {

    @Id
    private String userId;
    private String sourceEventId;
    private Instant withdrawnAt;
    private Instant blockedUntil;
    private Instant expireAt;
    private Instant createdAt;

    public boolean isActiveAt(Instant now) {
        return now.isBefore(blockedUntil);
    }
}
