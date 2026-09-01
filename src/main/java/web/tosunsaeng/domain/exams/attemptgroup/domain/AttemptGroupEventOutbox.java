package web.tosunsaeng.domain.exams.attemptgroup.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.Instant;

@Document(collection = "attempt_group_event_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AttemptGroupEventOutbox {

    @Id
    private String eventId;
    private String eventType;
    private int schemaVersion;
    private String producer;
    private Instant occurredAt;
    private String userId;
    private String attemptGroupId;
    private String sessionId;
    private AttemptGroupEventTarget targetStatus;
    private AttemptGroupCompletionEvidence evidence;
    private AttemptGroupFailureCode failureCode;
    private AttemptGroupEventSlot eventSlot;
    private String canonicalPayload;
    private String payloadDigest;
    private AttemptGroupOutboxStatus status;
    private int attemptCount;
    private Instant nextAttemptAt;
    private String leaseOwner;
    private String leaseToken;
    private Instant leaseUntil;
    private String lastFailureCategory;
    private Instant deliveredAt;
    private Instant deadLetterAt;
    private Instant expiresAt;
    private String traceId;
    private String parentSpanId;
    private String traceFlags;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;

    public static AttemptGroupEventOutbox pending(
            AttemptGroupEventPayload payload,
            AttemptGroupEventSlot slot,
            String canonicalPayload,
            String payloadDigest,
            String traceId,
            String parentSpanId,
            String traceFlags,
            Instant now
    ) {
        return new AttemptGroupEventOutbox(
                payload.eventId(), payload.eventType(), payload.schemaVersion(), payload.producer(),
                payload.occurredAt(), payload.userId(), payload.attemptGroupId(), payload.sessionId(),
                payload.targetStatus(), payload.evidence(), payload.failureCode(), slot,
                canonicalPayload, payloadDigest, AttemptGroupOutboxStatus.PENDING, 0, now,
                null, null, null, null, null, null, null,
                traceId, parentSpanId, traceFlags, now, now, null
        );
    }

    public void delivered(Instant now, Duration retention) {
        status = AttemptGroupOutboxStatus.DELIVERED;
        deliveredAt = now;
        expiresAt = now.plus(retention);
        clearLease(now);
    }

    public void retryAt(Instant next, String category, Instant now) {
        status = AttemptGroupOutboxStatus.PENDING;
        nextAttemptAt = next;
        lastFailureCategory = category;
        clearLease(now);
    }

    public void deadLetter(String category, Instant now, Duration retention) {
        status = AttemptGroupOutboxStatus.DEAD_LETTER;
        lastFailureCategory = category;
        deadLetterAt = now;
        expiresAt = now.plus(retention);
        clearLease(now);
    }

    public void blockedAuth(String category, Instant now) {
        status = AttemptGroupOutboxStatus.BLOCKED_AUTH;
        lastFailureCategory = category;
        expiresAt = null;
        clearLease(now);
    }

    private void clearLease(Instant now) {
        leaseOwner = null;
        leaseToken = null;
        leaseUntil = null;
        updatedAt = now;
    }
}
