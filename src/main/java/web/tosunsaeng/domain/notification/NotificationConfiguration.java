package web.tosunsaeng.domain.notification;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardService;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;
import java.time.Clock;

@Configuration
@ConditionalOnExpression("${app.notifications.tracking-enabled:false} || ${app.notifications.api-enabled:false} || ${app.notifications.sending-enabled:false}")
public class NotificationConfiguration {
    @Bean public NotificationTransactions notificationTransactions(MongoDatabaseFactory factory) { return new NotificationTransactions(factory); }
    @Bean public NotificationStore notificationStore(MongoTemplate mongo, UserOwnershipGuardRepository repository, Clock clock) {
        return new NotificationStore(mongo, new UserOwnershipGuardService(mongo, repository), clock);
    }
    @Bean public NotificationSubmissionTracker notificationSubmissionTracker(MongoTemplate mongo, NotificationTransactions tx,
            NotificationProperties properties, NotificationStore store) { return new NotificationSubmissionTracker(mongo, tx, properties, store); }
    @Bean public NotificationStartupValidator notificationStartupValidator(MongoTemplate mongo, NotificationTransactions tx,
            NotificationProperties p, Environment environment) {
        var validator = new NotificationStartupValidator(mongo, tx, p, environment); validator.validate(); return validator;
    }
    @Bean @DependsOn("notificationStartupValidator") public PushGateway notificationGateway(NotificationProperties p, Clock clock) {
        if (p.isSendingEnabled() && !p.isDryRun()) return new FcmPushGateway(p.getProjectId(), p.getCredentialFile(), clock);
        return new PushGateway() {
            public void prepare() { throw new IllegalStateException("Real sending is disabled"); }
            public Result send(String token, String id, java.time.Instant expiry) { throw new IllegalStateException("Real sending is disabled"); }
        };
    }
    @Bean public DailyReminderWorker dailyReminderWorker(NotificationStore store, NotificationTransactions tx,
            NotificationProperties p, PushGateway gateway, Clock clock, MeterRegistry meters) { return new DailyReminderWorker(store, tx, p, gateway, clock, meters); }
}
