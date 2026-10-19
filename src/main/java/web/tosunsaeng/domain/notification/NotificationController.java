package web.tosunsaeng.domain.notification;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.SuccessStatus;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/notifications/devices")
public class NotificationController {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ObjectProvider<NotificationStore> stores;
    private final ObjectProvider<NotificationTransactions> transactions;
    private final NotificationProperties properties;
    private final Clock clock;
    public NotificationController(ObjectProvider<NotificationStore> stores, ObjectProvider<NotificationTransactions> transactions,
                                  NotificationProperties properties, Clock clock) {
        this.stores = stores; this.transactions = transactions; this.properties = properties; this.clock = clock;
    }
    private JwtAuthenticationToken actor() {
        if (!properties.isApiEnabled()) throw new NotificationFailure(403);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token) || !token.isAuthenticated()
                || !Set.of("MEMBER", "GUEST").contains(Objects.toString(token.getToken().getClaims().get("account_type"), "")))
            throw new NotificationFailure(403);
        try { if (!UUID.fromString(token.getToken().getSubject()).toString().equals(token.getToken().getSubject())) throw new IllegalArgumentException(); }
        catch (RuntimeException invalid) { throw new NotificationFailure(403); }
        return token;
    }
    @PutMapping(consumes = "application/json")
    public BaseResponse<Object> register(@RequestHeader("X-Installation-Secret") String proof, HttpServletRequest request) {
        var actor = actor();
        JsonNode body;
        try {
            byte[] bytes = request.getInputStream().readNBytes(8193);
            if (bytes.length > 8192) throw new NotificationFailure(400);
            body = JSON.readTree(bytes);
            if (body == null || !body.isObject()) throw new NotificationFailure(400);
            var fields = body.fieldNames();
            while (fields.hasNext()) if (!Set.of("installationId", "platform", "pushToken", "permission", "permissionObservedAt").contains(fields.next())) throw new NotificationFailure(400);
        } catch (Exception e) { throw new NotificationFailure(400); }
        String id = text(body, "installationId"), platform = text(body, "platform"), token = text(body, "pushToken");
        String permission = body.has("permission") ? text(body, "permission") : "UNKNOWN";
        Instant observed;
        try { observed = body.has("permissionObservedAt") ? Instant.parse(text(body, "permissionObservedAt")) : clock.instant(); }
        catch (RuntimeException e) { throw new NotificationFailure(400); }
        if (!permission.equals("UNKNOWN") && !body.has("permissionObservedAt")) throw new NotificationFailure(400);
        transactions.getObject().run(() -> {
            stores.getObject().register(actor.getToken().getSubject(), actor.getToken().getClaimAsString("account_type"), id, proof, platform, token, permission, observed);
            return null;
        });
        return BaseResponse.onSuccess(SuccessStatus.OK, null);
    }
    @DeleteMapping("/{installationId}")
    public BaseResponse<Object> unregister(@PathVariable String installationId, @RequestHeader("X-Installation-Secret") String proof) {
        var actor = actor();
        transactions.getObject().run(() -> { stores.getObject().unregister(actor.getToken().getSubject(), installationId, proof); return null; });
        return BaseResponse.onSuccess(SuccessStatus.OK, null);
    }
    private static String text(JsonNode body, String key) { if (!body.path(key).isTextual()) throw new NotificationFailure(400); return body.path(key).textValue(); }
}
