package web.tosunsaeng.domain.usermerge.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import web.tosunsaeng.domain.usermerge.config.UserMergedProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class UserMergedWorkloadJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2TokenValidatorResult FAILURE = OAuth2TokenValidatorResult.failure(
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "UserMerged workload token is invalid", null)
    );

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Instant issuedAt = token.getIssuedAt();
        Instant notBefore = token.getNotBefore();
        Instant expiresAt = token.getExpiresAt();
        String jti = token.getId();
        Object typ = token.getHeaders().get("typ");
        Object kid = token.getHeaders().get("kid");
        if (issuedAt == null || notBefore == null || expiresAt == null
                || !issuedAt.equals(notBefore)
                || !expiresAt.isAfter(issuedAt)
                || Duration.between(issuedAt, expiresAt)
                .compareTo(UserMergedProperties.MAX_TOKEN_LIFETIME) > 0
                || !"JWT".equals(typ)
                || !(kid instanceof String kidText) || kidText.isBlank()
                || !isCanonicalUuid(jti)) {
            return FAILURE;
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
