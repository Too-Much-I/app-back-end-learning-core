package web.tosunsaeng.global.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AwsSdkCredentialModulesTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "software.amazon.awssdk.services.sso.SsoClient",
            "software.amazon.awssdk.services.ssooidc.SsoOidcClient",
            "software.amazon.awssdk.services.sts.StsClient",
            "software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider",
            "software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider"
    })
    void requiredCredentialSupportClassesAreOnClasspath(String className) {
        assertDoesNotThrow(() -> Class.forName(
                className,
                false,
                AwsSdkCredentialModulesTest.class.getClassLoader()
        ));
    }
}
