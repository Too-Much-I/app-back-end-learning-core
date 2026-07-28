package web.tosunsaeng.global.config.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private AuthMode mode = AuthMode.LEGACY;
    private String legacyUserId = "00000000-0000-0000-0000-000000000001";
    private Identity identity = new Identity();

    public AuthMode getMode() {
        return mode;
    }

    public void setMode(AuthMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException(AuthMode.SUPPORTED_VALUES_MESSAGE);
        }
        this.mode = mode;
    }

    public String getLegacyUserId() {
        return legacyUserId;
    }

    public void setLegacyUserId(String legacyUserId) {
        this.legacyUserId = legacyUserId;
    }

    public Identity getIdentity() {
        return identity;
    }

    public void setIdentity(Identity identity) {
        this.identity = identity;
    }

    public static class Identity {

        private String issuer;
        private String jwkSetUri;
        private String audience;

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

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }
    }
}
