package web.tosunsaeng.domain.challenge;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestController
public class ChallengeCallbackController {
    public static final String PATH = "/internal/v1/challenges/grading/callback";
    private final ObjectProvider<ChallengeCallbackService> services;
    public ChallengeCallbackController(ObjectProvider<ChallengeCallbackService> services) { this.services = services; }
    @PostMapping(value = PATH, consumes = "application/json")
    public ResponseEntity<Void> callback(HttpServletRequest request,
                                       @RequestHeader(value = "X-Challenge-Contract-Version", required = false) String version) throws IOException {
        byte[] bytes = request.getInputStream().readNBytes(16_385);
        services.getObject().accept(ChallengeCallback.parse(bytes, version));
        return ResponseEntity.noContent().build();
    }
}
