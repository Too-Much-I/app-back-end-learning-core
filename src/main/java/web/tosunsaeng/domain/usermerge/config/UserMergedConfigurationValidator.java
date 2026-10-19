package web.tosunsaeng.domain.usermerge.config;

import org.springframework.beans.factory.InitializingBean;

public final class UserMergedConfigurationValidator implements InitializingBean {

    private final UserMergedProperties properties;
    private final boolean restrictedProfile;

    public UserMergedConfigurationValidator(
            UserMergedProperties properties,
            boolean restrictedProfile
    ) {
        this.properties = properties;
        this.restrictedProfile = restrictedProfile;
    }

    @Override
    public void afterPropertiesSet() {
        properties.validate(restrictedProfile);
    }
}
