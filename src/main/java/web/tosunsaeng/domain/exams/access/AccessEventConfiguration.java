package web.tosunsaeng.domain.exams.access;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(AccessEventProperties.class)
public class AccessEventConfiguration {
    @Bean @Order(-1)
    public SecurityFilterChain billingAccessChain(HttpSecurity http,
            AccessEventProperties properties,ObjectProvider<AccessWorkloadVerifier> verifier,ObjectProvider<AccessRevocationStore> store) throws Exception {
        boolean enabled=properties.isReceiverEnabled();
        var filter=enabled?new AccessEventFilter(true,properties.getPort(),properties.getConsumerScopeId(),verifier.getObject(),store.getObject()::accept)
                :new AccessEventFilter(false,0,null,null,null);
        http.securityMatcher(request->AccessEventFilter.owns(request.getRequestURI()))
                .csrf(AbstractHttpConfigurer::disable).cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a->a.anyRequest().denyAll())
                .addFilterBefore(filter,AuthorizationFilter.class);
        return http.build();
    }
}
