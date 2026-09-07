package web.tosunsaeng.domain.challenge;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

@Configuration
@EnableConfigurationProperties(ChallengeProperties.class)
public class ChallengeSecurityConfiguration {
    @Bean @Order(-1)
    public SecurityFilterChain challengeCallbackChain(HttpSecurity http, ChallengeProperties properties) throws Exception {
        return http.securityMatcher(new AntPathRequestMatcher(ChallengeCallbackController.PATH))
                .csrf(AbstractHttpConfigurer::disable).cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new CallbackCredentialFilter(properties), AuthorizationFilter.class)
                .authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
    }
    static class CallbackCredentialFilter extends OncePerRequestFilter {
        private final ChallengeProperties properties;
        CallbackCredentialFilter(ChallengeProperties properties) { this.properties = properties; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
            if (!properties.isEnabled() || !"POST".equals(request.getMethod())) { reject(response, 403); return; }
            var values = Collections.list(request.getHeaders("Authorization"));
            String expected = properties.getCallbackCredential();
            if (expected == null || values.size() != 1 || !constantEquals("Bearer " + expected, values.getFirst())) {
                org.slf4j.LoggerFactory.getLogger(ChallengeSecurityConfiguration.class)
                        .warn("service=learning-core operation=challenge stage=callback outcome=auth_failure");
                reject(response, 401); return;
            }
            chain.doFilter(request, response);
        }
        static boolean constantEquals(String expected, String actual) {
            return actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
        }
        private static void reject(HttpServletResponse response, int code) throws IOException {
            response.setStatus(code); response.setContentType("application/json"); response.getWriter().write("{\"code\":\"UNAUTHORIZED\"}");
        }
    }
}
