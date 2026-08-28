package web.tosunsaeng.global.config.auth;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;
import web.tosunsaeng.global.auth.LegacyCurrentUserProvider;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

public class AuthStartupValidator implements InitializingBean, SmartInitializingSingleton {

    private static final Profiles LOCAL_OR_TEST = Profiles.of("local", "test");
    private static final Profiles STAGING_OR_PROD = Profiles.of("staging", "prod");
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "0.0.0.0", "::1", "[::1]");
    private static final Set<String> AUDIENCE_PLACEHOLDERS = Set.of(
            "change-me",
            "changeme",
            "placeholder",
            "default",
            "your-audience"
    );

    private final AuthProperties authProperties;
    private final Environment environment;
    private final ApplicationContext applicationContext;

    public AuthStartupValidator(
            AuthProperties authProperties,
            Environment environment,
            ApplicationContext applicationContext) {
        this.authProperties = authProperties;
        this.environment = environment;
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() {
        validateRawAuthMode();

        boolean restrictedProfile = environment.acceptsProfiles(STAGING_OR_PROD);
        if (authProperties.getMode() == AuthMode.LEGACY) {
            if (restrictedProfile) {
                throw new IllegalStateException("staging and prod profiles require jwt authentication mode");
            }
            if (!environment.acceptsProfiles(LOCAL_OR_TEST)) {
                throw new IllegalStateException("Legacy authentication is allowed only for local and test profiles");
            }
            return;
        }

        AuthProperties.Identity identity = authProperties.getIdentity();
        if (identity == null) {
            throw new IllegalStateException("JWT Identity settings are required");
        }

        URI issuer = requireHttpUri(identity.getIssuer(), "JWT issuer must be a non-empty HTTP(S) URI");
        URI jwkSetUri = requireHttpUri(
                identity.getJwkSetUri(),
                "JWT JWKS URL must be a non-empty HTTP(S) URI"
        );
        requireAudience(identity.getAudience(), restrictedProfile);
        requireNonNegative(identity.getClockSkew(), "JWT clock skew must be non-negative");

        if (restrictedProfile && isLocalHost(issuer)) {
            throw new IllegalStateException("staging and prod profiles cannot use a local Identity issuer");
        }
        if (restrictedProfile && isLocalHost(jwkSetUri)) {
            throw new IllegalStateException("staging and prod profiles cannot use a local Identity JWKS URL");
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!environment.acceptsProfiles(STAGING_OR_PROD)) {
            return;
        }

        if (!applicationContext.getBeansOfType(LegacyCurrentUserProvider.class, false, false).isEmpty()) {
            throw new IllegalStateException("LegacyCurrentUserProvider must not be registered in staging or prod");
        }
        if (applicationContext.containsBean("legacySecurityFilterChain")) {
            throw new IllegalStateException("Legacy SecurityFilterChain must not be registered in staging or prod");
        }
    }

    private void validateRawAuthMode() {
        if (!environment.containsProperty("app.auth.mode")) {
            return;
        }
        AuthMode configuredMode = AuthMode.fromProperty(environment.getProperty("app.auth.mode"));
        if (configuredMode != authProperties.getMode()) {
            throw new IllegalStateException(AuthMode.SUPPORTED_VALUES_MESSAGE);
        }
    }

    private URI requireHttpUri(String value, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(errorMessage);
        }

        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalStateException(errorMessage);
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(errorMessage);
        }
    }

    private void requireAudience(String audience, boolean restrictedProfile) {
        if (!StringUtils.hasText(audience)) {
            throw new IllegalStateException("JWT audience must be configured");
        }
        if (restrictedProfile
                && AUDIENCE_PLACEHOLDERS.contains(audience.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("staging and prod profiles require a non-placeholder JWT audience");
        }
    }

    private void requireNonNegative(Duration value, String errorMessage) {
        if (value == null || value.isNegative()) {
            throw new IllegalStateException(errorMessage);
        }
    }

    private boolean isLocalHost(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return LOCAL_HOSTS.contains(host)
                || host.startsWith("127.")
                || host.endsWith(".localhost");
    }
}
