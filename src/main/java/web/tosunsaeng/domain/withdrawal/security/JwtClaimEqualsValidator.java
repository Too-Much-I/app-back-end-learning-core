package web.tosunsaeng.domain.withdrawal.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtClaimEqualsValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2TokenValidatorResult FAILURE = OAuth2TokenValidatorResult.failure(
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Required workload principal is missing", null)
    );

    private final String claimName;
    private final String expectedValue;

    public JwtClaimEqualsValidator(String claimName, String expectedValue) {
        this.claimName = claimName;
        this.expectedValue = expectedValue;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String actual = token.getClaimAsString(claimName);
        return expectedValue.equals(actual) ? OAuth2TokenValidatorResult.success() : FAILURE;
    }
}
