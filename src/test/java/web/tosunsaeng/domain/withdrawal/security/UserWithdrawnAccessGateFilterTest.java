package web.tosunsaeng.domain.withdrawal.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import web.tosunsaeng.domain.withdrawal.domain.WithdrawnUserAccessDeny;
import web.tosunsaeng.domain.withdrawal.repository.WithdrawnUserAccessDenyRepository;
import web.tosunsaeng.domain.withdrawal.application.UserWithdrawnMetrics;
import web.tosunsaeng.global.config.security.SecurityErrorResponseHandler;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserWithdrawnAccessGateFilterTest {

    private static final Instant NOW = Instant.parse("2026-08-27T10:00:00Z");
    private static final String USER_ID = "00000000-0000-0000-0000-000000000042";
    private WithdrawnUserAccessDenyRepository repository;
    private FilterChain chain;
    private UserWithdrawnAccessGateFilter filter;

    @BeforeEach
    void setUp() {
        repository = mock(WithdrawnUserAccessDenyRepository.class);
        chain = mock(FilterChain.class);
        filter = new UserWithdrawnAccessGateFilter(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecurityErrorResponseHandler(new ObjectMapper()),
                new UserWithdrawnMetrics(new SimpleMeterRegistry())
        );
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt(),
                        java.util.List.of(new SimpleGrantedAuthority("SCOPE_learning:read"))
                )
        );
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeMarkerRejectsBeforeChain() throws Exception {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(marker(NOW.plusSeconds(1))));
        MockHttpServletResponse response = response("/api/v1/exams");

        assertEquals(401, response.getStatus());
        assertEquals("ACCOUNT_WITHDRAWN", new ObjectMapper().readTree(response.getContentAsString()).path("code").asText());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void exactBoundaryAndExpiredTtlDocumentAreAllowed() throws Exception {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(marker(NOW)));

        MockHttpServletResponse response = response("/api/v1/exams");

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void storeFailureIsFailClosed503() throws Exception {
        when(repository.findById(USER_ID)).thenThrow(new IllegalStateException("store unavailable"));

        MockHttpServletResponse response = response("/api/v1/exams");

        assertEquals(503, response.getStatus());
        assertEquals("WITHDRAWAL_DENY_GATE_UNAVAILABLE",
                new ObjectMapper().readTree(response.getContentAsString()).path("code").asText());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void callbackAndInternalEndpointDoNotReadMarker() throws Exception {
        response("/api/v1/exams/callback/feedback");
        response("/internal/v1/events/withdrawn");

        verify(repository, never()).findById(any());
        verify(chain, org.mockito.Mockito.times(2)).doFilter(any(), any());
    }

    @Test
    void missingAuthenticationDoesNotReadMarker() throws Exception {
        SecurityContextHolder.clearContext();

        response("/api/v1/exams");

        verify(repository, never()).findById(any());
        verify(chain).doFilter(any(), any());
    }

    private MockHttpServletResponse response(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private static WithdrawnUserAccessDeny marker(Instant blockedUntil) {
        return new WithdrawnUserAccessDeny(
                USER_ID,
                "00000000-0000-0000-0000-000000000109",
                NOW.minusSeconds(30),
                blockedUntil,
                blockedUntil,
                NOW.minusSeconds(30)
        );
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("redacted")
                .header("alg", "RS256")
                .subject(USER_ID)
                .issuedAt(NOW.minusSeconds(30))
                .expiresAt(NOW.plusSeconds(300))
                .build();
    }
}
