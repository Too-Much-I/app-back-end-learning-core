package web.tosunsaeng.domain.exams.reanswer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import web.tosunsaeng.domain.exams.billing.*;
import java.net.URI;

@Configuration(proxyBeanMethods=false)
public class ReanswerConfiguration {
    @Bean
    @ConditionalOnProperty(name="app.billing.reanswer.enabled", havingValue="true")
    ReanswerEligibilityClient reanswerEligibilityClient(Environment env, WorkloadJwtProperties jwt,
            BillingSagaProperties saga, BillingWorkloadTokenIssuer issuer) {
        if (!jwt.isSenderEnabled() || !env.getProperty("app.user-merged.writer-enabled",Boolean.class,false))
            throw new IllegalStateException("Reanswer requires workload sender and owner transactions");
        URI origin=URI.create(saga.getBaseUrl());
        if (!"https".equals(origin.getScheme()) || origin.getHost()==null || origin.getUserInfo()!=null
                || origin.getQuery()!=null || origin.getFragment()!=null
                || !(origin.getPath().isEmpty() || origin.getPath().equals("/")))
            throw new IllegalStateException("Reanswer requires a Billing HTTPS origin");
        return new HttpReanswerEligibilityClient(origin,issuer);
    }
}
