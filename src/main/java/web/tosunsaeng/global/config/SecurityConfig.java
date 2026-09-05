package web.tosunsaeng.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import web.tosunsaeng.global.config.auth.AuthConfiguration;
import web.tosunsaeng.global.config.auth.AuthProperties;
import web.tosunsaeng.global.config.auth.AuthStartupValidator;
import web.tosunsaeng.global.config.security.JwtAudienceValidator;
import web.tosunsaeng.global.config.security.JwtSubjectValidator;
import web.tosunsaeng.global.config.security.SecurityErrorResponseHandler;
import web.tosunsaeng.domain.withdrawal.security.UserWithdrawnAccessGateFilter;
import web.tosunsaeng.domain.usermerge.security.MergedUserAccessGateFilter;

import java.util.List;

@Configuration
@Import(AuthConfiguration.class)
public class SecurityConfig {

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
    @Order(2)
    @Profile({"local", "test"})
    @ConditionalOnProperty(prefix = "app.auth", name = "mode", havingValue = "legacy", matchIfMissing = true)
    public SecurityFilterChain legacySecurityFilterChain(
            HttpSecurity http,
            AuthStartupValidator authStartupValidator) throws Exception {
        configureCommonSecurity(http);
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    @ConditionalOnProperty(prefix = "app.auth", name = "mode", havingValue = "jwt")
    public SecurityFilterChain jwtSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            SecurityErrorResponseHandler errorHandler,
            ObjectProvider<UserWithdrawnAccessGateFilter> denyGateFilterProvider,
            ObjectProvider<MergedUserAccessGateFilter> mergedGateFilterProvider) throws Exception {
        configureCommonSecurity(http);
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                        .jwt(jwt -> jwt.decoder(jwtDecoder)));

        denyGateFilterProvider.ifAvailable(filter ->
                http.addFilterAfter(filter, BearerTokenAuthenticationFilter.class));
        mergedGateFilterProvider.ifAvailable(filter ->
                http.addFilterAfter(filter, BearerTokenAuthenticationFilter.class));

        return http.build();
    }

    @Bean
    @Order(1)
    @ConditionalOnProperty(prefix = "app.user-withdrawn", name = "consumer-enabled", havingValue = "true")
    public SecurityFilterChain userWithdrawnWorkloadSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("userWithdrawnWorkloadJwtDecoder") JwtDecoder workloadJwtDecoder,
            SecurityErrorResponseHandler errorHandler) throws Exception {
        configureCommonSecurity(http);
        http
                .securityMatcher(new AntPathRequestMatcher(
                        "/internal/v1/events/withdrawn",
                        HttpMethod.POST.name()
                ))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                        .jwt(jwt -> jwt.decoder(workloadJwtDecoder)));
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

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(identity.getClockSkew());
        JwtIssuerValidator issuerValidator = new JwtIssuerValidator(identity.getIssuer());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                issuerValidator,
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
