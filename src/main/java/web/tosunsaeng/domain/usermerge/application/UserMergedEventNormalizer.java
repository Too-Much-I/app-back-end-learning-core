package web.tosunsaeng.domain.usermerge.application;

import web.tosunsaeng.domain.usermerge.api.UserMergedEventRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

public final class UserMergedEventNormalizer {

    static final String DIGEST_DOMAIN = "tosunsaeng:user-merged";
    static final int SCHEMA_VERSION = 1;

    private UserMergedEventNormalizer() {
    }

    public static NormalizedUserMergedEvent normalize(
            UserMergedEventRequest request,
            Instant receivedAt
    ) {
        if (request == null || request.schemaVersion() == null
                || request.schemaVersion() != SCHEMA_VERSION) {
            throw invalid();
        }
        String eventId = canonicalUuid(request.eventId());
        String sourceUserId = canonicalUuid(request.sourceUserId());
        String targetUserId = canonicalUuid(request.targetUserId());
        if (sourceUserId.equals(targetUserId)) {
            throw invalid();
        }
        Instant occurredAt = parseInstant(request.occurredAt());
        return new NormalizedUserMergedEvent(
                eventId,
                SCHEMA_VERSION,
                digest(sourceUserId, targetUserId, occurredAt),
                sourceUserId,
                targetUserId,
                occurredAt,
                receivedAt
        );
    }

    private static String canonicalUuid(String value) {
        if (value == null || !value.equals(value.toLowerCase())) {
            throw invalid();
        }
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw invalid();
            }
            return parsed.toString();
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null) {
            throw invalid();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeException exception) {
            throw invalid();
        }
    }

    static String digest(String sourceUserId, String targetUserId, Instant occurredAt) {
        String input = DIGEST_DOMAIN + '\0' + SCHEMA_VERSION + '\0'
                + sourceUserId + '\0' + targetUserId + '\0' + occurredAt;
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(input.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static UserMergedEventException invalid() {
        return new UserMergedEventException(UserMergedEventException.Reason.INVALID_PAYLOAD);
    }
}
