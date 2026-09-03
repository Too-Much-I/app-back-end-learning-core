package web.tosunsaeng.domain.exams.billing;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.net.URI;

public class BillingSagaConfigurationValidator implements ApplicationRunner {

    private static final Profiles STAGING_OR_PROD = Profiles.of("staging", "prod");

    private final BillingSagaProperties properties;
    private final Environment environment;

    public BillingSagaConfigurationValidator(
            BillingSagaProperties properties,
            Environment environment
    ) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.isPhoneContinuationEnabled() && !properties.isCreationSagaEnabled()) {
            throw new IllegalStateException(
                    "Billing phone continuation requires the creation saga"
            );
        }
        if (!properties.isCreationSagaEnabled()) {
            return;
        }
        URI baseUri = parseBaseUri(properties.getBaseUrl());
        if (environment.acceptsProfiles(STAGING_OR_PROD)
                && !"https".equalsIgnoreCase(baseUri.getScheme())) {
            throw new IllegalStateException("Billing base URL must use HTTPS in staging/prod");
        }
        if (!"ap-northeast-2".equals(properties.getRegion())) {
            throw new IllegalStateException("Billing SigV4 region must be ap-northeast-2");
        }
        requirePositive(properties.getConnectTimeout(), "Billing connect timeout");
        requirePositive(properties.getReadTimeout(), "Billing read timeout");
    }

    static URI parseBaseUri(String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("Billing base URL is required when the creation saga is enabled");
        }
        URI uri;
        try {
            uri = URI.create(configured.trim());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("Billing base URL is invalid", invalid);
        }
        String scheme = uri.getScheme();
        if (!uri.isAbsolute()
                || uri.getHost() == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || uri.getUserInfo() != null
                || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath()))
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalStateException("Billing base URL must be an HTTP(S) service origin");
        }
        return uri;
    }

    private static void requirePositive(java.time.Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(name + " must be positive");
        }
    }
}
