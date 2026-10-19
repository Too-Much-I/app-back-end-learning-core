package web.tosunsaeng.domain.usermerge.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import web.tosunsaeng.domain.usermerge.security.UserMergedPayloadLimitFilter;
import web.tosunsaeng.domain.usermerge.security.WorkloadPrincipalAuthorizationManager;
import web.tosunsaeng.global.config.security.SecurityErrorResponseHandler;

@Configuration(proxyBeanMethods = false)
public class UserMergedSecurityConfig {

    @Bean
    @Order(0)
    @ConditionalOnProperty(prefix = "app.user-merged", name = "consumer-enabled", havingValue = "true")
    public SecurityFilterChain userMergedWorkloadSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("userMergedWorkloadJwtDecoder") JwtDecoder workloadJwtDecoder,
            SecurityErrorResponseHandler errorHandler
    ) throws Exception {
        http
                .securityMatcher(new AntPathRequestMatcher(
                        "/internal/v1/events/user-merged",
                        HttpMethod.POST.name()
                ))
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest()
                        .access(new WorkloadPrincipalAuthorizationManager()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                        .jwt(jwt -> jwt.decoder(workloadJwtDecoder)))
                .addFilterAfter(
                        new UserMergedPayloadLimitFilter(),
                        AuthorizationFilter.class
                );
        return http.build();
    }
}
