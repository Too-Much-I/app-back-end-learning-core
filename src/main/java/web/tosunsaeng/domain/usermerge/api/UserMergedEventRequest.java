package web.tosunsaeng.domain.usermerge.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserMergedEventRequest(
        String eventId,
        Integer schemaVersion,
        String sourceUserId,
        String targetUserId,
        String occurredAt
) {
}
