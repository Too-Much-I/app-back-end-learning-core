package web.tosunsaeng.domain.challenge;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.SuccessStatus;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/challenges")
public class ChallengeController {
    private final ObjectProvider<ChallengeService> services;
    private final ChallengeProperties properties;
    public ChallengeController(ObjectProvider<ChallengeService> services, ChallengeProperties properties) {
        this.services = services; this.properties = properties;
    }
    static String member() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()
                || !"MEMBER".equals(token.getToken().getClaims().get("account_type"))) throw ChallengeFailure.forbidden();
        return ChallengeCatalog.uuid(token.getToken().getSubject());
    }
    private ChallengeService service() { if (!properties.isEnabled()) throw ChallengeFailure.forbidden(); return services.getObject(); }
    private static BaseResponse<Object> ok(Object value) { return BaseResponse.onSuccess(SuccessStatus.OK, value); }
    @GetMapping("/today")
    public BaseResponse<Object> today() { String owner = member(); return ok(service().today(owner)); }
    @GetMapping("/today/questions/{questionNumber}")
    public BaseResponse<Object> question(@PathVariable int questionNumber, @RequestHeader("X-Challenge-Date") String date) {
        String owner = member(); return ok(service().question(owner, questionNumber, date));
    }
    @PostMapping("/today/questions/{questionNumber}/attempt")
    public BaseResponse<Object> start(@PathVariable int questionNumber, @RequestHeader("X-Challenge-Date") String date, HttpServletRequest request) throws IOException {
        String owner = member(); noBody(request); return ok(service().start(owner, questionNumber, date));
    }
    @PostMapping("/attempts/{attemptId}/upload-url")
    public BaseResponse<Object> upload(@PathVariable String attemptId, HttpServletRequest request) throws IOException {
        String owner = member(); noBody(request); return ok(service().upload(owner, attemptId));
    }
    @PostMapping(value = "/today/questions/{questionNumber}/answer", consumes = "application/json")
    public BaseResponse<Object> submit(@PathVariable int questionNumber, @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) throws IOException {
        String owner = member(); byte[] bytes = request.getInputStream().readNBytes(1025);
        if (bytes.length > 1024) throw ChallengeFailure.badRequest();
        JsonNode body;
        try { body = ChallengeCallback.JSON.readTree(bytes); } catch (IOException e) { throw ChallengeFailure.badRequest(); }
        if (body == null || !body.isObject() || body.size() != 1 || !body.path("attemptId").isTextual()) throw ChallengeFailure.badRequest();
        return ok(service().submit(owner, questionNumber, body.path("attemptId").textValue(), key));
    }
    @GetMapping("/history")
    public BaseResponse<Object> history(@RequestParam(required = false) String yearMonth) { String owner = member(); return ok(service().history(owner, yearMonth)); }
    @GetMapping("/{challengeDate}/results")
    public BaseResponse<Object> results(@PathVariable String challengeDate, @RequestParam(required = false) Integer questionNumber) {
        String owner = member(); return ok(service().results(owner, challengeDate, questionNumber));
    }
    private static void noBody(HttpServletRequest request) throws IOException { if (request.getInputStream().read() != -1) throw ChallengeFailure.badRequest(); }
}
