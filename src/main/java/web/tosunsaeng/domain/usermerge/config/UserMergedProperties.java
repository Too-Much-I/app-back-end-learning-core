package web.tosunsaeng.domain.usermerge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

@ConfigurationProperties(prefix = "app.user-merged")
public class UserMergedProperties {

    public static final String AUDIENCE = "learning-core-user-merged";
    public static final String PRINCIPAL = "identity-service";
    public static final Duration MAX_TOKEN_LIFETIME = Duration.ofMinutes(2);
    public static final int MAX_BODY_BYTES = 4096;
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "0.0.0.0", "::1", "[::1]");

    private boolean writerEnabled;
    private boolean consumerEnabled;
    private boolean sourceDenyEnabled;
    private Workload workload = new Workload();

    public void validate(boolean restrictedProfile) {
        if (consumerEnabled && (!writerEnabled || !sourceDenyEnabled)) {
            throw invalid("consumer requires writer and source deny gate");
        }
        if (!consumerEnabled) {
            return;
        }
        workload.validate(restrictedProfile);
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException("UserMerged configuration is invalid: " + reason);
    }

    public boolean isWriterEnabled() {
        return writerEnabled;
    }

    public void setWriterEnabled(boolean writerEnabled) {
        this.writerEnabled = writerEnabled;
    }

    public boolean isConsumerEnabled() {
        return consumerEnabled;
    }

    public void setConsumerEnabled(boolean consumerEnabled) {
        this.consumerEnabled = consumerEnabled;
    }

    public boolean isSourceDenyEnabled() {
        return sourceDenyEnabled;
    }

    public void setSourceDenyEnabled(boolean sourceDenyEnabled) {
        this.sourceDenyEnabled = sourceDenyEnabled;
    }

    public Workload getWorkload() {
        return workload;
    }

    public void setWorkload(Workload workload) {
        this.workload = workload;
    }

    public static class Workload {
        private String issuer;
        private String jwkSetUri;
        private Duration clockSkew = Duration.ofSeconds(30);

        private void validate(boolean restrictedProfile) {
            URI issuerUri = requireUri(issuer, "workload issuer");
            URI jwksUri = requireUri(jwkSetUri, "workload JWKS URL");
            if (clockSkew == null || clockSkew.isNegative()
                    || clockSkew.compareTo(Duration.ofMinutes(1)) > 0) {
                throw invalid("workload clock skew must be between PT0S and PT1M");
            }
            if (restrictedProfile && (!"https".equalsIgnoreCase(issuerUri.getScheme())
                    || !"https".equalsIgnoreCase(jwksUri.getScheme())
                    || isLocal(issuerUri)
                    || isLocal(jwksUri))) {
                throw invalid("restricted profiles require remote HTTPS workload issuer and JWKS URL");
            }
        }

        private static URI requireUri(String value, String field) {
            if (!StringUtils.hasText(value)) {
                throw invalid(field + " is required");
            }
            try {
                URI uri = new URI(value);
                if (!uri.isAbsolute() || uri.getHost() == null
                        || !("http".equalsIgnoreCase(uri.getScheme())
                        || "https".equalsIgnoreCase(uri.getScheme()))) {
                    throw invalid(field + " must be an HTTP(S) URI");
                }
                return uri;
            } catch (URISyntaxException exception) {
                throw invalid(field + " must be an HTTP(S) URI");
            }
        }

        private static boolean isLocal(URI uri) {
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return LOCAL_HOSTS.contains(host) || host.startsWith("127.") || host.endsWith(".localhost");
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }

        public Duration getClockSkew() {
            return clockSkew;
        }

        public void setClockSkew(Duration clockSkew) {
            this.clockSkew = clockSkew;
        }
    }
}
