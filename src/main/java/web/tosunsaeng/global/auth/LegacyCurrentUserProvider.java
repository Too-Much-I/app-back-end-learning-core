package web.tosunsaeng.global.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import web.tosunsaeng.global.config.auth.AuthProperties;

// Never register the fixed development user in staging or production.
@Component
@Profile({"local", "test"})
@ConditionalOnProperty(prefix = "app.auth", name = "mode", havingValue = "legacy", matchIfMissing = true)
public class LegacyCurrentUserProvider implements CurrentUserProvider {

    private final String legacyUserId;

    public LegacyCurrentUserProvider(AuthProperties authProperties) {
        this.legacyUserId = authProperties.getLegacyUserId();
    }

    @Override
    public String getCurrentUserId() {
        return legacyUserId;
    }
}
