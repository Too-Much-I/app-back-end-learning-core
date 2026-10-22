package web.tosunsaeng.domain.exams.attemptgroup.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttemptGroupEventPayload(
        String eventId,
        String eventType,
        int schemaVersion,
        String producer,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Instant occurredAt,
        String userId,
        String attemptGroupId,
        String sessionId,
        AttemptGroupEventTarget targetStatus,
        AttemptGroupCompletionEvidence evidence,
        AttemptGroupFailureCode failureCode
) {
    public AttemptGroupEventPayload {
        // Billing accepts millisecond precision. Normalize before canonical bytes/digest are frozen,
        // never while publishing or retrying an already persisted event.
        if (occurredAt != null) {
            occurredAt = occurredAt.truncatedTo(ChronoUnit.MILLIS);
        }
    }
}
