package web.tosunsaeng.domain.exams.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.billing")
public class BillingSagaProperties {

    private boolean creationSagaEnabled;
    private boolean phoneContinuationEnabled;
    private String baseUrl = "";
    private String region = "ap-northeast-2";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);

    public boolean isCreationSagaEnabled() {
        return creationSagaEnabled;
    }

    public void setCreationSagaEnabled(boolean creationSagaEnabled) {
        this.creationSagaEnabled = creationSagaEnabled;
    }

    public boolean isPhoneContinuationEnabled() {
        return phoneContinuationEnabled;
    }

    public void setPhoneContinuationEnabled(boolean phoneContinuationEnabled) {
        this.phoneContinuationEnabled = phoneContinuationEnabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
