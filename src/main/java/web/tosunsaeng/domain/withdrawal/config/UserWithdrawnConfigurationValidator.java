package web.tosunsaeng.domain.withdrawal.config;

import org.springframework.beans.factory.InitializingBean;
import web.tosunsaeng.global.config.auth.AuthProperties;

public final class UserWithdrawnConfigurationValidator implements InitializingBean {

    private final UserWithdrawnConsumerProperties properties;
    private final AuthProperties authProperties;
    private final boolean restrictedProfile;

    public UserWithdrawnConfigurationValidator(
            UserWithdrawnConsumerProperties properties,
            AuthProperties authProperties,
            boolean restrictedProfile) {
        this.properties = properties;
        this.authProperties = authProperties;
        this.restrictedProfile = restrictedProfile;
    }

    @Override
    public void afterPropertiesSet() {
        properties.validate(authProperties, restrictedProfile);
    }
}
