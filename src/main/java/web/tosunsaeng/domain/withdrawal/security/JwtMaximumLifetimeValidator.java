package web.tosunsaeng.domain.withdrawal.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;

public final class JwtMaximumLifetimeValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2TokenValidatorResult FAILURE = OAuth2TokenValidatorResult.failure(
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Workload token lifetime is invalid", null)
    );

    private final Duration maximumLifetime;

    public JwtMaximumLifetimeValidator(Duration maximumLifetime) {
        this.maximumLifetime = maximumLifetime;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Instant issuedAt = token.getIssuedAt();
        Instant expiresAt = token.getExpiresAt();
        if (issuedAt == null || expiresAt == null || expiresAt.isBefore(issuedAt)) {
            return FAILURE;
        }
        Duration actual = Duration.between(issuedAt, expiresAt);
        return actual.compareTo(maximumLifetime) <= 0
                ? OAuth2TokenValidatorResult.success()
                : FAILURE;
    }
}
