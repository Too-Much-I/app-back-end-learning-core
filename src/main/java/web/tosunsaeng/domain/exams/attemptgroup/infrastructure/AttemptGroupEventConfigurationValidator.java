package web.tosunsaeng.domain.exams.attemptgroup.infrastructure;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class AttemptGroupEventConfigurationValidator {
    private final AttemptGroupEventProperties properties;

    @PostConstruct
    void validate() {
        if (!properties.publisherEnabled()) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(properties.billingBaseUrl());
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("AttemptGroup Billing base URL is invalid", invalid);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
            throw new IllegalStateException("AttemptGroup publisher requires HTTPS Billing origin");
        }
    }
}
