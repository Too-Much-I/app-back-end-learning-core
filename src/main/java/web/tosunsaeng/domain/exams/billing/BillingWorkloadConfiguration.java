package web.tosunsaeng.domain.exams.billing;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventProperties;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WorkloadJwtProperties.class)
public class BillingWorkloadConfiguration {
    @Bean
    public BillingWorkloadTokenIssuer billingWorkloadTokenIssuer(WorkloadJwtProperties jwt,
            BillingSagaProperties saga, AttemptGroupEventProperties events, Environment env) {
        validateFlags(jwt.isSenderEnabled(), saga.isCreationSagaEnabled(), saga.isPhoneContinuationEnabled(),
                env.getProperty("app.billing.reconciliation.enabled", Boolean.class, false), events.publisherEnabled());
        if (saga.isCreationSagaEnabled() || events.publisherEnabled()) {
            var origin = BillingSagaConfigurationValidator.parseBaseUri(
                    saga.isCreationSagaEnabled() ? saga.getBaseUrl() : events.billingBaseUrl());
            if (!"https".equalsIgnoreCase(origin.getScheme()))
                throw new IllegalStateException("Billing workload origin requires HTTPS");
            if (saga.isCreationSagaEnabled() && events.publisherEnabled()
                    && !sameOrigin(origin, BillingSagaConfigurationValidator.parseBaseUri(events.billingBaseUrl())))
                throw new IllegalStateException("Billing workload clients must share an origin");
            if (saga.isCreationSagaEnabled()) {
                requirePositive(saga.getConnectTimeout());
                requirePositive(saga.getReadTimeout());
            }
            if (events.publisherEnabled()) {
                requirePositive(events.connectTimeout());
                requirePositive(events.readTimeout());
            }
        }
        return new BillingWorkloadTokenIssuer(jwt, Clock.systemUTC());
    }

    private static boolean sameOrigin(java.net.URI first, java.net.URI second) {
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && (first.getPort() == -1 ? 443 : first.getPort())
                == (second.getPort() == -1 ? 443 : second.getPort());
    }

    private static void requirePositive(java.time.Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative())
            throw new IllegalStateException("Billing workload timeout must be positive");
    }

    public static void validateFlags(boolean j, boolean s, boolean h, boolean r, boolean p) {
        if ((h && !s) || (r && !s) || (!j && (s || p)))
            throw new IllegalStateException("Invalid Billing workload feature combination");
    }
}
