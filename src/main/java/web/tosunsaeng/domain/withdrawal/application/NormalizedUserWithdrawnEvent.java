package web.tosunsaeng.domain.withdrawal.application;

import java.time.Instant;

record NormalizedUserWithdrawnEvent(
        String eventId,
        int schemaVersion,
        String payloadDigest,
        String userId,
        Instant withdrawnAt,
        Instant receivedAt,
        Instant blockedUntil,
        Instant inboxCleanupAt
) {
}
