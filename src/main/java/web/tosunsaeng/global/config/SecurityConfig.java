package web.tosunsaeng.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import web.tosunsaeng.global.config.auth.AuthConfiguration;
import web.tosunsaeng.global.config.auth.AuthProperties;
import web.tosunsaeng.global.config.auth.AuthStartupValidator;
import web.tosunsaeng.global.config.security.JwtAudienceValidator;
import web.tosunsaeng.global.config.security.JwtSubjectValidator;
import web.tosunsaeng.global.config.security.SecurityErrorResponseHandler;

import java.util.List;

@Configuration
@Import(AuthConfiguration.class)
public class SecurityConfig {

    private static final String NOTIFICATION_DEVICE_ENDPOINTS = "/api/v1/notifications/devices/**";

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/exams/callback/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/actuator/health",
            "/actuator/health/**"
    };

    // Legacy is intentionally available only for local compatibility and automated tests.
    @Bean
    @Profile({"local", "test"})
    @ConditionalOnProperty(prefix = "app.auth", name = "mode", havingValue = "legacy", matchIfMissing = true)
    public SecurityFilterChain legacySecurityFilterChain(
            HttpSecurity http,
            AuthStartupValidator authStartupValidator,
            SecurityErrorResponseHandler errorHandler) throws Exception {
        configureCommonSecurity(http);
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(NOTIFICATION_DEVICE_ENDPOINTS).authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler));
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.auth", name = "mode", havingValue = "jwt")
    public SecurityFilterChain jwtSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            SecurityErrorResponseHandler errorHandler) throws Exception {
        configureCommonSecurity(http);
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(NOTIFICATION_DEVICE_ENDPOINTS).authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                        .jwt(jwt -> jwt.decoder(jwtDecoder)));

        return http.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.auth", name = "mode", havingValue = "jwt")
    public JwtDecoder jwtDecoder(
            AuthProperties authProperties,
            AuthStartupValidator authStartupValidator) {
        AuthProperties.Identity identity = authProperties.getIdentity();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(identity.getJwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> issuerAndTimestampValidator =
                JwtValidators.createDefaultWithIssuer(identity.getIssuer());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerAndTimestampValidator,
                new JwtAudienceValidator(identity.getAudience()),
                new JwtSubjectValidator()
        ));
        return decoder;
    }

    private void configureCommonSecurity(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration corsConfiguration = new CorsConfiguration();
                    corsConfiguration.setAllowedOrigins(List.of(
                            "http://localhost:5173",
                            "http://localhost:3000",
                            "https://to-teacher.com",
                            "https://www.to-teacher.com"
                    ));
                    corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
                    corsConfiguration.setAllowedHeaders(List.of("*"));
                    corsConfiguration.setAllowCredentials(true);
                    corsConfiguration.setExposedHeaders(List.of("Authorization"));
                    return corsConfiguration;
                }))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }
}
