package web.tosunsaeng.domain.withdrawal.application;

import web.tosunsaeng.domain.withdrawal.api.UserWithdrawnEventRequest;
import web.tosunsaeng.domain.withdrawal.config.UserWithdrawnConsumerProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

final class UserWithdrawnEventNormalizer {

    private static final String DIGEST_DOMAIN = "tosunsaeng:user-withdrawn";
    private static final int SCHEMA_VERSION = 1;

    private UserWithdrawnEventNormalizer() {
    }

    static NormalizedUserWithdrawnEvent normalize(
            UserWithdrawnEventRequest request,
            Instant now,
            UserWithdrawnConsumerProperties properties) {
        if (request == null || request.schemaVersion() == null
                || request.schemaVersion() != SCHEMA_VERSION) {
            throw invalid();
        }
        String eventId = canonicalUuid(request.eventId());
        String userId = canonicalUuid(request.userId());
        Instant withdrawnAt = parseInstant(request.withdrawnAt());
        try {
            if (withdrawnAt.isAfter(now.plus(properties.getMaximumFutureEventSkew()))) {
                throw invalid();
            }
            Instant blockedUntil = withdrawnAt
                    .plus(properties.getMaxAcceptedAccessTokenLifetime())
                    .plus(properties.getAllowedVerifierClockSkew());
            Instant cleanupAt = now.plus(properties.getInboxRetention());
            String digest = digest(userId, withdrawnAt);
            return new NormalizedUserWithdrawnEvent(
                    eventId,
                    SCHEMA_VERSION,
                    digest,
                    userId,
                    withdrawnAt,
                    now,
                    blockedUntil,
                    cleanupAt
            );
        } catch (DateTimeException exception) {
            throw new UserWithdrawnEventException(
                    UserWithdrawnEventException.Reason.INVALID_PAYLOAD,
                    exception
            );
        }
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

    private static String digest(String userId, Instant withdrawnAt) {
        String input = DIGEST_DOMAIN + '\0' + SCHEMA_VERSION + '\0'
                + userId + '\0' + withdrawnAt;
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(input.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static UserWithdrawnEventException invalid() {
        return new UserWithdrawnEventException(UserWithdrawnEventException.Reason.INVALID_PAYLOAD);
    }
}
