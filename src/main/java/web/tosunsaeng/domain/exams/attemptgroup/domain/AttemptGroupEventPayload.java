package web.tosunsaeng.domain.exams.attemptgroup.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttemptGroupEventPayload(
        String eventId,
        String eventType,
        int schemaVersion,
        String producer,
        Instant occurredAt,
        String userId,
        String attemptGroupId,
        String sessionId,
        AttemptGroupEventTarget targetStatus,
        AttemptGroupCompletionEvidence evidence,
        AttemptGroupFailureCode failureCode
) {
}
