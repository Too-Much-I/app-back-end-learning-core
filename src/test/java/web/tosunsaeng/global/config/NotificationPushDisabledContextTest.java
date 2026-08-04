package web.tosunsaeng.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import web.tosunsaeng.domain.notifications.application.ExpoPushReceiptWorker;
import web.tosunsaeng.domain.notifications.application.ExpoPushTicketWorker;
import web.tosunsaeng.domain.notifications.application.NotificationOutboxFinalizer;
import web.tosunsaeng.domain.notifications.application.NotificationOutboxWorker;
import web.tosunsaeng.domain.notifications.application.NotificationWorkerScheduler;
import web.tosunsaeng.domain.notifications.provider.DisabledPushNotificationSender;
import web.tosunsaeng.domain.notifications.provider.DisabledPushReceiptClient;
import web.tosunsaeng.domain.notifications.provider.PushNotificationSender;
import web.tosunsaeng.domain.notifications.provider.PushReceiptClient;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class NotificationPushDisabledContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    NotificationPushConfig.class,
                    NotificationWorkerScheduler.class,
                    NotificationOutboxWorker.class,
                    ExpoPushTicketWorker.class,
                    ExpoPushReceiptWorker.class,
                    NotificationOutboxFinalizer.class
            )
            .withBean(MongoDatabaseFactory.class, () -> mock(MongoDatabaseFactory.class))
            .withPropertyValues(
                    "app.notification.push.enabled=false",
                    "app.notification.push.provider=expo",
                    "app.notification.push.expo.send-url=https://expo.example.test/send",
                    "app.notification.push.expo.receipt-url=https://expo.example.test/receipts",
                    "app.notification.push.expo.access-token=",
                    "app.notification.push.expo.access-token-required=false",
                    "app.notification.push.worker-delay=5s",
                    "app.notification.push.receipt-delay=15m",
                    "app.notification.push.lease-duration=2m",
                    "app.notification.push.max-attempts=5",
                    "app.notification.push.initial-backoff=30s",
                    "app.notification.push.max-backoff=30m",
                    "app.notification.push.batch-size=100",
                    "app.notification.push.connect-timeout=3s",
                    "app.notification.push.read-timeout=10s"
            );

    @Test
    void disabledPushStartsWithoutAccessTokenHttpClientOrWorkers() {
        contextRunner.run(context -> {
            assertInstanceOf(
                    DisabledPushNotificationSender.class,
                    context.getBean(PushNotificationSender.class)
            );
            assertInstanceOf(
                    DisabledPushReceiptClient.class,
                    context.getBean(PushReceiptClient.class)
            );
            assertTrue(context.getBeansOfType(NotificationWorkerScheduler.class).isEmpty());
            assertTrue(context.getBeansOfType(NotificationOutboxWorker.class).isEmpty());
            assertTrue(context.getBeansOfType(ExpoPushTicketWorker.class).isEmpty());
            assertTrue(context.getBeansOfType(ExpoPushReceiptWorker.class).isEmpty());
            assertTrue(context.getBeansOfType(NotificationOutboxFinalizer.class).isEmpty());
            assertTrue(context.getBeansOfType(org.springframework.web.client.RestTemplate.class).isEmpty());
        });
    }
}
