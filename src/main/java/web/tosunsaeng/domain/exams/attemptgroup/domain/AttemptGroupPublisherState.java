package web.tosunsaeng.domain.exams.attemptgroup.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "attempt_group_publisher_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AttemptGroupPublisherState {
    public static final String SINGLETON_ID = "attempt-group-publisher";

    @Id
    private String id;
    private boolean authBlocked;
    private Instant nextAuthProbeAt;
    private String probeLeaseToken;
    private Instant probeLeaseUntil;
    private Instant updatedAt;
    @Version
    private Long version;

    public static AttemptGroupPublisherState open(Instant now) {
        return new AttemptGroupPublisherState(SINGLETON_ID, false, null, null, null, now, null);
    }
}
