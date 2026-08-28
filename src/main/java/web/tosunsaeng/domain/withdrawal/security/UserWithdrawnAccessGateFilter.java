package web.tosunsaeng.domain.withdrawal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import web.tosunsaeng.domain.withdrawal.repository.WithdrawnUserAccessDenyRepository;
import web.tosunsaeng.domain.withdrawal.application.UserWithdrawnMetrics;
import web.tosunsaeng.global.config.security.SecurityErrorResponseHandler;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.io.IOException;
import java.time.Clock;

@Slf4j
public class UserWithdrawnAccessGateFilter extends OncePerRequestFilter {

    private static final String CALLBACK_PREFIX = "/api/v1/exams/callback/";
    private static final String INTERNAL_ENDPOINT = "/internal/v1/events/withdrawn";

    private final WithdrawnUserAccessDenyRepository repository;
    private final Clock clock;
    private final SecurityErrorResponseHandler responseHandler;
    private final UserWithdrawnMetrics metrics;

    public UserWithdrawnAccessGateFilter(
            WithdrawnUserAccessDenyRepository repository,
            Clock clock,
            SecurityErrorResponseHandler responseHandler,
            UserWithdrawnMetrics metrics) {
        this.repository = repository;
        this.clock = clock;
        this.responseHandler = responseHandler;
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith(CALLBACK_PREFIX) || INTERNAL_ENDPOINT.equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            var marker = repository.findById(jwt.getSubject());
            if (marker.isPresent() && marker.orElseThrow().isActiveAt(clock.instant())) {
                metrics.recordGate("DENIED");
                log.warn("탈퇴 사용자 요청 거절 event=withdrawal.deny_gate outcome=denied errorCode={}",
                        ErrorStatus._ACCOUNT_WITHDRAWN.getCode());
                SecurityContextHolder.clearContext();
                responseHandler.writeErrorResponse(response, ErrorStatus._ACCOUNT_WITHDRAWN);
                return;
            }
        } catch (RuntimeException storeFailure) {
            metrics.recordGate("STORE_UNAVAILABLE");
            log.error("탈퇴 계정 확인 실패 event=withdrawal.deny_gate outcome=store_unavailable "
                            + "errorCode={} errorType={}",
                    ErrorStatus._WITHDRAWAL_DENY_GATE_UNAVAILABLE.getCode(),
                    storeFailure.getClass().getName());
            responseHandler.writeErrorResponse(
                    response,
                    ErrorStatus._WITHDRAWAL_DENY_GATE_UNAVAILABLE
            );
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken
                && authentication.isAuthenticated()) {
            return jwtAuthenticationToken.getToken();
        }
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }
}
