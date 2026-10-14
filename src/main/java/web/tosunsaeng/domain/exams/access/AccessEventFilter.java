package web.tosunsaeng.domain.exams.access;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.function.Consumer;

/** Fixed ingress ordering; does not delegate accepted requests to a public/user controller. */
public final class AccessEventFilter extends OncePerRequestFilter {
    public static final String NAMESPACE="/internal/v1/entitlements/access";
    public static final String ROUTE=NAMESPACE+"/events";
    private final boolean enabled;
    private final int port;
    private final String scope;
    private final AccessWorkloadVerifier verifier;
    private final Consumer<AccessRevocationEvent> sink;
    public AccessEventFilter(boolean enabled,int port,String scope,AccessWorkloadVerifier verifier,Consumer<AccessRevocationEvent> sink) {
        if(enabled && (port<1 || port>65535 || scope==null || scope.isBlank() || verifier==null || sink==null))
            throw new IllegalStateException("Billing access receiver configuration is invalid");
        this.enabled=enabled; this.port=port; this.scope=scope; this.verifier=verifier; this.sink=sink;
    }
    public static boolean owns(String path) { return path!=null && (path.equals(NAMESPACE) || path.startsWith(NAMESPACE+"/")); }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !owns(request.getRequestURI()); }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException {
        response.setHeader("Cache-Control","no-store");
        if(!enabled || request.getLocalPort()!=port) { response.setStatus(404); return; }
        var authorization=Collections.list(request.getHeaders("Authorization"));
        if(request.getQueryString()!=null || authorization.size()!=1 || !authorization.get(0).startsWith("Bearer ")) {
            response.setStatus(401); return;
        }
        int auth=verifier.authenticate(authorization.get(0).substring(7));
        if(auth!=204) { response.setStatus(auth); return; }
        if(!"POST".equals(request.getMethod()) || !ROUTE.equals(request.getRequestURI())) { response.setStatus(403); return; }
        try {
            if(request.getContentLengthLong()>AccessRevocationEvent.MAX_BYTES) throw new AccessEventFailure(413,"INVALID_REQUEST");
            var raw=request.getInputStream().readNBytes(AccessRevocationEvent.MAX_BYTES+1);
            sink.accept(AccessRevocationEvent.decode(raw,scope));
            response.setStatus(204);
        } catch(AccessEventFailure e) { error(response,e.status(),e.code()); }
        catch(RuntimeException unavailable) { error(response,503,"LC_TEMPORARILY_UNAVAILABLE"); }
    }
    private static void error(HttpServletResponse response,int status,String code) throws IOException {
        response.setStatus(status); response.setContentType("application/json");
        response.getWriter().write("{\"code\":\""+code+"\"}");
    }
}
