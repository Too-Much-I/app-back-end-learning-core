package web.tosunsaeng.global.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.auth", name = "mode", havingValue = "legacy", matchIfMissing = true)
public class LegacyCurrentUserProvider implements CurrentUserProvider {

    private final String legacyUserId;

    public LegacyCurrentUserProvider(@Value("${app.auth.legacy-user-id}") String legacyUserId) {
        this.legacyUserId = legacyUserId;
    }

    @Override
    public String getCurrentUserId() {
        return legacyUserId;
    }
}
