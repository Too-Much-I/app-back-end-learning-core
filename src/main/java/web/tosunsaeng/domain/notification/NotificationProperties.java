package web.tosunsaeng.domain.notification;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Getter @Setter @Component
@ConfigurationProperties(prefix = "app.notifications")
public class NotificationProperties {
    private boolean trackingEnabled;
    private boolean apiEnabled;
    private boolean sendingEnabled;
    private boolean dryRun = true;
    private int batchSize = 100;
    // First instant after every old submit writer was drained. Sending needs a complete KST day.
    private Instant trackingReadyAt;
    private String projectId;
    private String credentialFile;
}
