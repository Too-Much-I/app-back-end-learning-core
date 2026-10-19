package web.tosunsaeng.domain.exams.access;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Never generate a toString: TLS credentials are configuration secrets. */
@Getter @Setter
@ConfigurationProperties("app.billing.access-events")
public class AccessEventProperties {
    private boolean receiverEnabled;
    private String environment="";
    private String consumerScopeId="";
    private String billingPublicJwkSet="";
    private int port;
    private String tlsKeyStore="";
    private String tlsKeyStorePassword="";
    private String tlsKeyAlias="";
    public void validate(int publicPort) {
        if(!receiverEnabled) return;
        if(port<1 || port>65535 || port==publicPort || consumerScopeId==null || consumerScopeId.isBlank())
            throw new IllegalStateException("Billing access listener configuration is invalid");
    }
}
