package web.tosunsaeng.domain.usermerge.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserMergedWorkloadJwtValidatorTest {

    private static final Instant NOW = Instant.parse("2026-09-04T01:00:00Z");
    private final UserMergedWorkloadJwtValidator validator = new UserMergedWorkloadJwtValidator();

    @Test
    void acceptsExactHeaderLifetimeAndIdentityClaims() {
        assertThat(validator.validate(jwt(NOW, NOW, NOW.plusSeconds(120), "JWT", "key-1",
                "00000000-0000-4000-8000-000000000110")).hasErrors()).isFalse();
    }

    @Test
    void rejectsMissingNbfLongLifetimeAndInvalidHeaderOrJti() {
        assertThat(validator.validate(jwt(NOW, null, NOW.plusSeconds(120), "JWT", "key-1",
                "00000000-0000-4000-8000-000000000110")).hasErrors()).isTrue();
        assertThat(validator.validate(jwt(NOW, NOW, NOW.plusSeconds(121), "JWT", "key-1",
                "00000000-0000-4000-8000-000000000110")).hasErrors()).isTrue();
        assertThat(validator.validate(jwt(NOW, NOW, NOW.plusSeconds(120), "jwt", "key-1",
                "00000000-0000-4000-8000-000000000110")).hasErrors()).isTrue();
        assertThat(validator.validate(jwt(NOW, NOW, NOW.plusSeconds(120), "JWT", " ",
                "00000000-0000-4000-8000-000000000110")).hasErrors()).isTrue();
        assertThat(validator.validate(jwt(NOW, NOW, NOW.plusSeconds(120), "JWT", "key-1",
                "not-a-uuid")).hasErrors()).isTrue();
    }

    private static Jwt jwt(
            Instant issuedAt,
            Instant notBefore,
            Instant expiresAt,
            String typ,
            String kid,
            String jti
    ) {
        Jwt.Builder builder = Jwt.withTokenValue("redacted")
                .header("alg", "RS256")
                .header("typ", typ)
                .header("kid", kid)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("jti", jti);
        if (notBefore != null) {
            builder.notBefore(notBefore);
        }
        return builder.build();
    }
}
