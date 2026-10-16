package web.tosunsaeng.domain.learningrecorddeletion.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Durable request-local evidence. Never log payload or attach it to an exception. */
@Getter @NoArgsConstructor
public class BillingDeletionDelivery {
    public enum State { PENDING, ACKED, NEEDS_REVIEW }
    public enum Failure { AUTHENTICATION_FAILED, CONTRACT_ERROR, EVENT_ID_CONFLICT, TARGET_UNRESOLVED,
        RETRY_EXHAUSTED, RETRY_AFTER_REVIEW, STORAGE_UNAVAILABLE }
    private String environment;
    private String eventId;
    private Map<String, Object> payload;
    private String payloadDigest;
    private State state;
    private long attempts;
    private Instant firstAttemptAt, nextAttemptAt, ackedAt, leaseUntil, retryNotBefore;
    private String leaseToken;
    private Failure failureCode;

    public static BillingDeletionDelivery create(String environment, String actor, String deletionId,
            String group, String session, Instant deletedAt, Instant now) {
        if (environment == null || !Set.of("test", "staging", "production").contains(environment))
            throw new IllegalStateException("Deletion delivery environment required");
        DeletionIdentifiers.userId(actor); DeletionIdentifiers.uuidV4(deletionId); DeletionIdentifiers.uuidV4(group);
        if (session == null || !session.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("Invalid target");
        var d = new BillingDeletionDelivery();
        d.environment = environment; d.eventId = UUID.randomUUID().toString();
        String timestamp = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
                .format(deletedAt.truncatedTo(ChronoUnit.MILLIS));
        // Validated IDs are ASCII without escape characters; fixed field order is canonical.
        d.payload = Map.of("eventId",d.eventId,"schemaVersion",1,"userId",actor,"deletionOperationId",deletionId,
                "attemptGroupId",group,"sessionId",session,"deletedAt",timestamp);
        d.payloadDigest = DeletionIdentifiers.sha256(d.getPayload());
        d.state = State.PENDING; d.nextAttemptAt = now;
        return d;
    }
    /** Stored as a seven-field document, emitted in fixed canonical order. No mutable map is exposed. */
    public String getPayload() {
        if (payload == null || !payload.keySet().equals(Set.of("eventId","schemaVersion","userId","deletionOperationId","attemptGroupId","sessionId","deletedAt"))
                || !Integer.valueOf(1).equals(payload.get("schemaVersion"))) throw new IllegalStateException("Invalid delivery evidence");
        StringBuilder body = new StringBuilder("{");
        for (String name : List.of("eventId","schemaVersion","userId","deletionOperationId","attemptGroupId","sessionId","deletedAt")) {
            if (body.length() > 1) body.append(',');
            body.append('"').append(name).append("\":");
            if (name.equals("schemaVersion")) body.append('1');
            else {
                Object value = payload.get(name);
                if (!(value instanceof String s) || !s.matches("[A-Za-z0-9_.:-]{1,128}")) throw new IllegalStateException("Invalid delivery evidence");
                body.append('"').append(value).append('"');
            }
        }
        return body.append('}').toString();
    }

    public boolean claim(Instant now, String token) {
        if (state != State.PENDING || nextAttemptAt == null || nextAttemptAt.isAfter(now)
                || (leaseUntil != null && leaseUntil.isAfter(now))) return false;
        if (attempts >= 200 || (firstAttemptAt != null && !now.isBefore(firstAttemptAt.plus(Duration.ofHours(24))))) {
            review(Failure.RETRY_EXHAUSTED); return false;
        }
        if (retryNotBefore != null && now.isBefore(retryNotBefore)) { nextAttemptAt = retryNotBefore; return false; }
        if (firstAttemptAt == null) firstAttemptAt = now;
        attempts++; leaseToken = token; leaseUntil = now.plusSeconds(30); return true;
    }
    public boolean owned(String token, Instant now) {
        return state == State.PENDING && Objects.equals(leaseToken, token) && leaseUntil != null && now.isBefore(leaseUntil);
    }
    public boolean maySend(String token, Instant now) {
        return owned(token, now) && firstAttemptAt != null && now.isBefore(firstAttemptAt.plus(Duration.ofHours(24)))
                && (retryNotBefore == null || !now.isBefore(retryNotBefore));
    }
    public void acknowledge(Instant now) {
        state = State.ACKED; ackedAt = now; nextAttemptAt = null; leaseToken = null; leaseUntil = null; failureCode = null;
    }
    public void review(Failure failure) {
        state = State.NEEDS_REVIEW; failureCode = Objects.requireNonNull(failure);
        nextAttemptAt = null; leaseToken = null; leaseUntil = null;
    }
    public void retry(Instant now, Instant candidate, long jitterMillis) {
        if (candidate != null && (retryNotBefore == null || candidate.isAfter(retryNotBefore))) retryNotBefore = candidate;
        if (attempts >= 200) { review(Failure.RETRY_EXHAUSTED); return; }
        long seconds = Math.min(300, 1L << Math.min(Math.max(0, attempts - 1), 9));
        Instant due = now.plusSeconds(seconds).plusMillis(Math.floorMod(jitterMillis, 1000));
        if (retryNotBefore != null && retryNotBefore.isAfter(due)) due = retryNotBefore;
        if (!due.isBefore(firstAttemptAt.plus(Duration.ofHours(24)))) { review(Failure.RETRY_AFTER_REVIEW); return; }
        nextAttemptAt = due; leaseToken = null; leaseUntil = null; failureCode = null;
    }
}
