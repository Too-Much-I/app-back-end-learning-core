package web.tosunsaeng.domain.usermerge.security;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import web.tosunsaeng.domain.usermerge.config.UserMergedProperties;

import java.util.function.Supplier;

public final class WorkloadPrincipalAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationDecision check(
            Supplier<Authentication> authentication,
            RequestAuthorizationContext context
    ) {
        Authentication current = authentication.get();
        boolean granted = current instanceof JwtAuthenticationToken jwtAuthentication
                && current.isAuthenticated()
                && UserMergedProperties.PRINCIPAL.equals(jwtAuthentication.getToken().getSubject());
        return new AuthorizationDecision(granted);
    }
}
