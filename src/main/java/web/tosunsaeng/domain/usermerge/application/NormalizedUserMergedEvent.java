package web.tosunsaeng.domain.usermerge.application;

import java.time.Instant;

public record NormalizedUserMergedEvent(
        String eventId,
        int schemaVersion,
        String payloadDigest,
        String sourceUserId,
        String targetUserId,
        Instant occurredAt,
        Instant receivedAt
) {
}
