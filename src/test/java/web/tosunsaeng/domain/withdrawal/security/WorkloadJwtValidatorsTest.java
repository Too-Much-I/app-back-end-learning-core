package web.tosunsaeng.domain.withdrawal.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkloadJwtValidatorsTest {

    private static final Instant NOW = Instant.parse("2026-08-27T10:00:00Z");

    @Test
    void principalMustMatchExactAllowlist() {
        JwtClaimEqualsValidator validator = new JwtClaimEqualsValidator("service", "identity");
        assertTrue(validator.validate(jwt("other", Duration.ofMinutes(1))).hasErrors());
        assertFalse(validator.validate(jwt("identity", Duration.ofMinutes(1))).hasErrors());
    }

    @Test
    void lifetimeMustNotExceedMaximum() {
        JwtMaximumLifetimeValidator validator = new JwtMaximumLifetimeValidator(Duration.ofMinutes(5));
        assertFalse(validator.validate(jwt("identity", Duration.ofMinutes(5))).hasErrors());
        assertTrue(validator.validate(jwt("identity", Duration.ofMinutes(6))).hasErrors());
    }

    private static Jwt jwt(String service, Duration lifetime) {
        return Jwt.withTokenValue("redacted")
                .header("alg", "RS256")
                .claim("service", service)
                .issuedAt(NOW)
                .expiresAt(NOW.plus(lifetime))
                .build();
    }
}
