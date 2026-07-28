package web.tosunsaeng.global.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtCurrentUserProviderTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000042";

    private final JwtCurrentUserProvider currentUserProvider = new JwtCurrentUserProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsCanonicalUuidFromJwtAuthenticationTokenSubject() {
        SecurityContextHolder.getContext().setAuthentication(
                authenticatedJwt(USER_ID.toUpperCase())
        );

        assertEquals(USER_ID, currentUserProvider.getCurrentUserId());
    }

    @Test
    void alsoSupportsJwtPrincipal() {
        Jwt jwt = jwt(USER_ID);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(jwt, jwt.getTokenValue(), List.of())
        );

        assertEquals(USER_ID, currentUserProvider.getCurrentUserId());
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThrows(
                AuthenticationCredentialsNotFoundException.class,
                currentUserProvider::getCurrentUserId
        );
    }

    @Test
    void rejectsNonUuidSubject() {
        SecurityContextHolder.getContext().setAuthentication(authenticatedJwt("not-a-uuid"));

        assertThrows(BadCredentialsException.class, currentUserProvider::getCurrentUserId);
    }

    private Jwt jwt(String subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    private JwtAuthenticationToken authenticatedJwt(String subject) {
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt(subject), List.of());
        authentication.setAuthenticated(true);
        return authentication;
    }
}
