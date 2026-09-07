package web.tosunsaeng.domain.challenge;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;

@Getter @Setter
@ConfigurationProperties("app.challenge")
public class ChallengeProperties {
    private boolean enabled = false;
    private URI aiEndpoint;
    private String outboundCredential;
    private String callbackCredential;
    private long pollMs = 1000;
    private long expiryPollMs = 10000;
    public void validate(boolean productionLike) {
        if (!enabled) return;
        if (pollMs <= 0 || expiryPollMs <= 0) throw new IllegalStateException("Challenge poll intervals must be positive");
        if (aiEndpoint == null || aiEndpoint.getHost() == null || aiEndpoint.getUserInfo() != null
                || aiEndpoint.getQuery() != null || aiEndpoint.getFragment() != null
                || !"/v1/challenges/evaluations".equals(aiEndpoint.getPath())
                || !("https".equals(aiEndpoint.getScheme()) || (!productionLike && "http".equals(aiEndpoint.getScheme()))))
            throw new IllegalStateException("Challenge AI endpoint is invalid");
        if (!validCredential(outboundCredential) || !validCredential(callbackCredential) || outboundCredential.equals(callbackCredential))
            throw new IllegalStateException("Challenge requires distinct direction-specific credentials");
    }
    private static boolean validCredential(String value) {
        return value != null && !value.isBlank() && value.length() <= 4096 && value.chars().allMatch(c -> c > 32 && c < 127);
    }
}
