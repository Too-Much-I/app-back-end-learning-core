package web.tosunsaeng.global.config.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

import java.util.UUID;

public class JwtSubjectValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_SUBJECT = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "The subject must be a UUID",
            null
    );

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String subject = jwt.getSubject();
        if (!StringUtils.hasText(subject)) {
            return OAuth2TokenValidatorResult.failure(INVALID_SUBJECT);
        }

        try {
            UUID.fromString(subject);
            return OAuth2TokenValidatorResult.success();
        } catch (IllegalArgumentException exception) {
            return OAuth2TokenValidatorResult.failure(INVALID_SUBJECT);
        }
    }
}
