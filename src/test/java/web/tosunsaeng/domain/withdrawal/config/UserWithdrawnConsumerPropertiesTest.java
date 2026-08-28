package web.tosunsaeng.domain.withdrawal.config;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.global.config.auth.AuthProperties;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserWithdrawnConsumerPropertiesTest {

    @Test
    void bothDisabledConfigurationNeedsNoOperationalValues() {
        assertDoesNotThrow(() -> new UserWithdrawnConsumerProperties().validate(auth(), true));
    }

    @Test
    void gateOnlyConfigurationNeedsNoConsumerValues() {
        UserWithdrawnConsumerProperties properties = new UserWithdrawnConsumerProperties();
        properties.setDenyGateEnabled(true);
        assertDoesNotThrow(() -> properties.validate(auth(), true));
    }

    @Test
    void consumerCannotRunWithoutDenyGate() {
        UserWithdrawnConsumerProperties properties = valid();
        properties.setDenyGateEnabled(false);
        assertThrows(IllegalStateException.class, () -> properties.validate(auth(), false));
    }

    @Test
    void enabledLocalConfigurationAcceptsExplicitValues() {
        assertDoesNotThrow(() -> valid().validate(auth(), false));
    }

    @Test
    void verifierSkewMustMatchUserJwtAndDurationsMustBeExplicit() {
        UserWithdrawnConsumerProperties mismatchedSkew = valid();
        mismatchedSkew.setAllowedVerifierClockSkew(Duration.ofSeconds(30));
        assertThrows(IllegalStateException.class, () -> mismatchedSkew.validate(auth(), false));

        UserWithdrawnConsumerProperties missingRetention = valid();
        missingRetention.setInboxRetention(null);
        assertThrows(IllegalStateException.class, () -> missingRetention.validate(auth(), false));
    }

    @Test
    void restrictedProfileRejectsHttpWorkloadProfile() {
        assertThrows(IllegalStateException.class, () -> valid().validate(auth(), true));
    }

    private static UserWithdrawnConsumerProperties valid() {
        UserWithdrawnConsumerProperties properties = new UserWithdrawnConsumerProperties();
        properties.setConsumerEnabled(true);
        properties.setDenyGateEnabled(true);
        properties.setMaxAcceptedAccessTokenLifetime(Duration.ofMinutes(30));
        properties.setAllowedVerifierClockSkew(Duration.ofMinutes(1));
        properties.setInboxRetention(Duration.ofDays(120));
        properties.setMaximumFutureEventSkew(Duration.ofMinutes(1));
        UserWithdrawnConsumerProperties.Workload workload = new UserWithdrawnConsumerProperties.Workload();
        workload.setIssuer("http://identity-workload.test");
        workload.setJwkSetUri("http://identity-workload.test/.well-known/jwks.json");
        workload.setAudience("learning-core-user-withdrawn");
        workload.setPrincipalClaim("service");
        workload.setPrincipalValue("identity");
        workload.setMaxTokenLifetime(Duration.ofMinutes(5));
        workload.setClockSkew(Duration.ofSeconds(30));
        properties.setWorkload(workload);
        return properties;
    }

    private static AuthProperties auth() {
        AuthProperties auth = new AuthProperties();
        auth.getIdentity().setClockSkew(Duration.ofMinutes(1));
        return auth;
    }
}
