package web.tosunsaeng.domain.usermerge.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "user_ownership_guards")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserOwnershipGuard {

    @Id
    private String userId;
    private OwnershipGuardState state;
    private long revision;
    private String targetUserId;
    private Instant mergedAt;
    private String eventId;
    private Instant createdAt;
    private Instant updatedAt;

    public static UserOwnershipGuard active(String userId, Instant now) {
        return new UserOwnershipGuard(
                userId,
                OwnershipGuardState.ACTIVE,
                0L,
                null,
                null,
                null,
                now,
                now
        );
    }

    public boolean isMerged() {
        return state == OwnershipGuardState.MERGED;
    }
}
