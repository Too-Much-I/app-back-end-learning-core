package web.tosunsaeng.global.config.auth;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

    @Bean
    @ConfigurationPropertiesBinding
    public static AuthModeConverter authModeConverter() {
        return new AuthModeConverter();
    }

    @Bean
    public AuthStartupValidator authStartupValidator(
            AuthProperties authProperties,
            Environment environment,
            ApplicationContext applicationContext) {
        return new AuthStartupValidator(authProperties, environment, applicationContext);
    }
}
