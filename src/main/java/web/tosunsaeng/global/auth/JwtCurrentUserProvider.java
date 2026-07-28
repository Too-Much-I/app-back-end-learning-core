package web.tosunsaeng.global.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "app.auth", name = "mode", havingValue = "jwt")
public class JwtCurrentUserProvider implements CurrentUserProvider {

    @Override
    public String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("JWT authentication is required");
        }

        Jwt jwt = extractJwt(authentication);
        String subject = jwt.getSubject();
        if (!StringUtils.hasText(subject)) {
            throw new BadCredentialsException("JWT subject is required");
        }

        try {
            return UUID.fromString(subject).toString();
        } catch (IllegalArgumentException exception) {
            throw new BadCredentialsException("JWT subject must be a UUID", exception);
        }
    }

    private Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken();
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new AuthenticationCredentialsNotFoundException("JWT principal is required");
    }
}
