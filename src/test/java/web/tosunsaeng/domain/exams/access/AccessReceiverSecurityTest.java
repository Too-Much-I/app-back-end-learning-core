package web.tosunsaeng.domain.exams.access;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class AccessReceiverSecurityTest {
    @Configuration @EnableWebSecurity @Import(AccessEventConfiguration.class)
    static class Security {
        @Bean @Order(2) SecurityFilterChain existing(HttpSecurity http) throws Exception {
            return http.csrf(c->c.disable()).authorizeHttpRequests(a->a.anyRequest().permitAll()).build();
        }
    }
    AnnotationConfigWebApplicationContext context(boolean enabled) {
        var c=new AnnotationConfigWebApplicationContext(); c.setServletContext(new MockServletContext());
        c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of(
                "app.billing.access-events.receiver-enabled",enabled,"app.billing.access-events.billing-public-jwk-set","invalid-do-not-parse")));
        c.register(Security.class); return c;
    }
    @Test void offNamespaceIs404BeforeLegacyCatchAllWithoutParsingKeys() throws Exception {
        try(var c=context(false)) {
            c.refresh(); var chain=c.getBean(FilterChainProxy.class); var downstream=new AtomicInteger();
            for(String path:new String[]{AccessEventFilter.ROUTE,AccessEventFilter.NAMESPACE+"/unknown",AccessEventFilter.NAMESPACE}) {
                var req=new MockHttpServletRequest("POST",path);req.setServletPath(path);req.setQueryString("");
                var res=new MockHttpServletResponse();chain.doFilter(req,res,(a,b)->downstream.incrementAndGet());
                assertThat(res.getStatus()).isEqualTo(404);assertThat(res.getContentAsByteArray()).isEmpty();
            }
            var req=new MockHttpServletRequest("GET","/api/v1/exams");req.setServletPath("/api/v1/exams");
            chain.doFilter(req,new MockHttpServletResponse(),(a,b)->downstream.incrementAndGet());
            assertThat(downstream.get()).isEqualTo(1);
        }
    }
    @Test void activationCannotBypassRemainingRolloutGates() {
        assertThatThrownBy(()->new AccessEventRolloutGate(true).afterPropertiesSet())
                .hasMessage("Billing access activation requires legacy lifecycle and V07 rollout verification");
    }
    @Test void disabledRuntimeDoesNotRequireMongoOwnerKeysOrTls() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(AccessEventRuntimeConfiguration.class)
                .withPropertyValues("app.billing.access-events.receiver-enabled=false",
                        "app.billing.access-events.billing-public-jwk-set=invalid",
                        "app.billing.access-events.tls-key-store=/missing")
                .run(c->{assertThat(c).hasNotFailed();assertThat(c).doesNotHaveBean(AccessRevocationStore.class);
                    assertThat(c).doesNotHaveBean(AccessWorkloadVerifier.class);});
    }
    @Test void enabledSecurityChainUsesActualVerifierAndStoreBean() throws Exception {
        try(var c=context(true)) {
            c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("listener",Map.of(
                    "app.billing.access-events.port",8443,"app.billing.access-events.consumer-scope-id","fixture-test")));
            var store=org.mockito.Mockito.mock(AccessRevocationStore.class);
            var verifier=AccessReceiverContractTest.verifier("current",AccessReceiverContractTest.NOW);
            c.addBeanFactoryPostProcessor(factory->{factory.registerSingleton("billingAccessStore",store);factory.registerSingleton("billingAccessVerifier",verifier);});
            c.refresh();
            var req=new MockHttpServletRequest("POST",AccessEventFilter.ROUTE);req.setServletPath(AccessEventFilter.ROUTE);req.setLocalPort(8443);
            req.addHeader("Authorization",AccessReceiverContractTest.load("http-cases.json").get(0).get("authorization").asText());
            req.setContent(AccessReceiverContractTest.event());var res=new MockHttpServletResponse();
            c.getBean(FilterChainProxy.class).doFilter(req,res,(a,b)->{throw new AssertionError();});
            assertThat(res.getStatus()).isEqualTo(204);org.mockito.Mockito.verify(store).accept(org.mockito.ArgumentMatchers.any());
        }
    }
}
