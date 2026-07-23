package web.tosunsaeng.global.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
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
