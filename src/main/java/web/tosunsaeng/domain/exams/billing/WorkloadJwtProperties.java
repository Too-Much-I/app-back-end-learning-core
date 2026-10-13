package web.tosunsaeng.domain.exams.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.billing.workload-jwt")
public class WorkloadJwtProperties {
    private boolean senderEnabled;
    private String environment = "";
    private String privateJwk = "";
    public boolean isSenderEnabled() { return senderEnabled; }
    public void setSenderEnabled(boolean value) { senderEnabled = value; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String value) { environment = value; }
    public String getPrivateJwk() { return privateJwk; }
    public void setPrivateJwk(String value) { privateJwk = value; }
    // Deliberately no toString: configuration contains private key material.
}
