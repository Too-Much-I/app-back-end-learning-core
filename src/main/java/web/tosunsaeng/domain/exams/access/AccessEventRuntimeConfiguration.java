package web.tosunsaeng.domain.exams.access;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.time.Clock;
import java.io.IOException;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(AccessEventProperties.class)
@ConditionalOnProperty(prefix="app.billing.access-events",name="receiver-enabled",havingValue="true")
public class AccessEventRuntimeConfiguration {
    @Bean public AccessWorkloadVerifier billingAccessVerifier(AccessEventProperties p,@Value("${server.port:8080}") int publicPort){
        p.validate(publicPort);
        return new AccessWorkloadVerifier(p.getEnvironment(),p.getBillingPublicJwkSet(),Clock.systemUTC());
    }
    @Bean public AccessRevocationStore billingAccessStore(MongoTemplate mongo,AccessEventProperties p,
            org.springframework.beans.factory.ObjectProvider<web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor> owners){
        var owner=owners.getIfAvailable();
        if(owner==null || !owner.enabled()) throw new IllegalStateException("Billing access receiver requires owner writer fencing");
        var store=new AccessRevocationStore(mongo,p.getEnvironment(),p.getConsumerScopeId(),Clock.systemUTC());
        store.validateIndexes(); return store;
    }
    @Bean public WebServerFactoryCustomizer<TomcatServletWebServerFactory> billingAccessTls(AccessEventProperties p,
            @Value("${server.port:8080}") int publicPort,AccessWorkloadVerifier verifier,AccessRevocationStore store){
        var connector=AccessEventTls.connector(p,publicPort);
        return factory->factory.addAdditionalTomcatConnectors(connector);
    }
    @Bean public FilterRegistrationBean<OncePerRequestFilter> billingAccessPortIsolation(AccessEventProperties p){
        var filter=new FilterRegistrationBean<OncePerRequestFilter>();filter.setOrder(Integer.MIN_VALUE+10);
        filter.setFilter(new OncePerRequestFilter(){
            @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws IOException,ServletException {
                if(req.getLocalPort()==p.getPort() && !AccessEventFilter.owns(req.getRequestURI())) {
                    res.setHeader("Cache-Control","no-store");res.setStatus(404);return;
                }
                chain.doFilter(req,res);
            }
        });return filter;
    }
}
