package web.tosunsaeng.domain.answerlearning;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;

@Getter @Setter
@ConfigurationProperties("app.answer-learning")
public class LearningProperties {
    private boolean enabled;
    private boolean gradingEnabled;
    private String datasetId = "";
    private String contentVersion = "";
    private String bucket = "";
    private String lambdaUrl = "";
    private String region = "ap-northeast-2";
    private int dailySubmissions = 300;
    private boolean paidAccessEnabled;
    private java.util.Map<Integer, String> freeUnits = new java.util.HashMap<>();
    private long pollMs = 1500;
    public void validate() {
        if (datasetId.isBlank() || contentVersion.isBlank() || bucket.isBlank()
                || dailySubmissions < 1 || dailySubmissions > 1000 || pollMs < 1000
                || !"ap-northeast-2".equals(region)) throw new IllegalStateException("Invalid answer learning configuration");
        if (gradingEnabled) validateLambdaUrl();
        if (paidAccessEnabled && (!freeUnits.keySet().equals(java.util.Set.of(1,2,3,4,5))
                || freeUnits.values().stream().anyMatch(id -> id == null || id.isBlank())
                || new java.util.HashSet<>(freeUnits.values()).size() != 5))
            throw new IllegalStateException("Five explicit free learning units are required");
    }
    public void validateLambdaUrl() {
            URI uri = URI.create(lambdaUrl);
            if (!"ap-northeast-2".equals(region) || !"https".equals(uri.getScheme()) || uri.getHost() == null
                    || !uri.getHost().matches("[a-z0-9]+\\.lambda-url\\.ap-northeast-2\\.on\\.aws")
                    || uri.getRawQuery() != null || uri.getUserInfo() != null || uri.getPort() != -1
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())) || uri.getFragment() != null)
                throw new IllegalStateException("Expected a Seoul IAM Lambda Function URL");
    }
}
