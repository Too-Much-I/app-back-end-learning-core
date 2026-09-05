package web.tosunsaeng.domain.usermerge.security;

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
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;
import web.tosunsaeng.global.config.security.SecurityErrorResponseHandler;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.io.IOException;

@Slf4j
public final class MergedUserAccessGateFilter extends OncePerRequestFilter {

    private static final String CALLBACK_PREFIX = "/api/v1/exams/callback/";
    private static final String INTERNAL_PREFIX = "/internal/";

    private final UserOwnershipGuardRepository repository;
    private final SecurityErrorResponseHandler responseHandler;

    public MergedUserAccessGateFilter(
            UserOwnershipGuardRepository repository,
            SecurityErrorResponseHandler responseHandler
    ) {
        this.repository = repository;
        this.responseHandler = responseHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith(CALLBACK_PREFIX) || path.startsWith(INTERNAL_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Jwt jwt = extractJwt(SecurityContextHolder.getContext().getAuthentication());
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            if (repository.findById(jwt.getSubject())
                    .filter(guard -> guard.isMerged())
                    .isPresent()) {
                SecurityContextHolder.clearContext();
                responseHandler.writeErrorResponse(
                        response,
                        ErrorStatus._ACCOUNT_MERGED_TOKEN_REJECTED
                );
                return;
            }
        } catch (RuntimeException storeFailure) {
            log.error("병합 계정 확인 실패 event=user_merged.deny_gate outcome=store_unavailable "
                            + "errorCode={} errorType={}",
                    ErrorStatus._USER_MERGED_DENY_GATE_UNAVAILABLE.getCode(),
                    storeFailure.getClass().getName());
            responseHandler.writeErrorResponse(
                    response,
                    ErrorStatus._USER_MERGED_DENY_GATE_UNAVAILABLE
            );
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token && authentication.isAuthenticated()) {
            return token.getToken();
        }
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }
}
