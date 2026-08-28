package web.tosunsaeng.domain.withdrawal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import web.tosunsaeng.global.config.auth.AuthProperties;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

@ConfigurationProperties(prefix = "app.user-withdrawn")
public class UserWithdrawnConsumerProperties {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "0.0.0.0", "::1", "[::1]");

    private boolean consumerEnabled;
    private boolean denyGateEnabled;
    private Duration maxAcceptedAccessTokenLifetime;
    private Duration allowedVerifierClockSkew;
    private Duration inboxRetention;
    private Duration maximumFutureEventSkew;
    private Workload workload = new Workload();

    public void validate(AuthProperties authProperties, boolean restrictedProfile) {
        if (consumerEnabled && !denyGateEnabled) {
            throw invalid("consumer cannot be enabled while the deny gate is disabled");
        }
        if (!consumerEnabled) {
            return;
        }
        requirePositive(maxAcceptedAccessTokenLifetime, "max accepted access token lifetime");
        requireNonNegative(allowedVerifierClockSkew, "allowed verifier clock skew");
        requirePositive(inboxRetention, "inbox retention");
        requireNonNegative(maximumFutureEventSkew, "maximum future event skew");
        if (authProperties.getIdentity() == null
                || !allowedVerifierClockSkew.equals(authProperties.getIdentity().getClockSkew())) {
            throw invalid("allowed verifier clock skew must match the user JWT verifier");
        }
        workload.validate(restrictedProfile);
    }

    private static void requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw invalid(field + " must be positive");
        }
    }

    private static void requireNonNegative(Duration value, String field) {
        if (value == null || value.isNegative()) {
            throw invalid(field + " must be non-negative");
        }
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException("UserWithdrawn consumer configuration is invalid: " + reason);
    }

    public boolean isConsumerEnabled() { return consumerEnabled; }
    public void setConsumerEnabled(boolean consumerEnabled) { this.consumerEnabled = consumerEnabled; }
    public boolean isDenyGateEnabled() { return denyGateEnabled; }
    public void setDenyGateEnabled(boolean denyGateEnabled) { this.denyGateEnabled = denyGateEnabled; }
    public Duration getMaxAcceptedAccessTokenLifetime() { return maxAcceptedAccessTokenLifetime; }
    public void setMaxAcceptedAccessTokenLifetime(Duration value) { this.maxAcceptedAccessTokenLifetime = value; }
    public Duration getAllowedVerifierClockSkew() { return allowedVerifierClockSkew; }
    public void setAllowedVerifierClockSkew(Duration value) { this.allowedVerifierClockSkew = value; }
    public Duration getInboxRetention() { return inboxRetention; }
    public void setInboxRetention(Duration inboxRetention) { this.inboxRetention = inboxRetention; }
    public Duration getMaximumFutureEventSkew() { return maximumFutureEventSkew; }
    public void setMaximumFutureEventSkew(Duration value) { this.maximumFutureEventSkew = value; }
    public Workload getWorkload() { return workload; }
    public void setWorkload(Workload workload) { this.workload = workload; }

    public static class Workload {
        private String issuer;
        private String jwkSetUri;
        private String audience;
        private String principalClaim;
        private String principalValue;
        private Duration maxTokenLifetime;
        private Duration clockSkew;

        private void validate(boolean restrictedProfile) {
            URI issuerUri = requireUri(issuer, "workload issuer");
            URI jwksUri = requireUri(jwkSetUri, "workload JWKS URL");
            if (!StringUtils.hasText(audience)
                    || !StringUtils.hasText(principalClaim)
                    || !StringUtils.hasText(principalValue)) {
                throw invalid("workload audience and principal allowlist are required");
            }
            requirePositive(maxTokenLifetime, "workload max token lifetime");
            requireNonNegative(clockSkew, "workload clock skew");
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

        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getJwkSetUri() { return jwkSetUri; }
        public void setJwkSetUri(String jwkSetUri) { this.jwkSetUri = jwkSetUri; }
        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }
        public String getPrincipalClaim() { return principalClaim; }
        public void setPrincipalClaim(String principalClaim) { this.principalClaim = principalClaim; }
        public String getPrincipalValue() { return principalValue; }
        public void setPrincipalValue(String principalValue) { this.principalValue = principalValue; }
        public Duration getMaxTokenLifetime() { return maxTokenLifetime; }
        public void setMaxTokenLifetime(Duration maxTokenLifetime) { this.maxTokenLifetime = maxTokenLifetime; }
        public Duration getClockSkew() { return clockSkew; }
        public void setClockSkew(Duration clockSkew) { this.clockSkew = clockSkew; }
    }
}
