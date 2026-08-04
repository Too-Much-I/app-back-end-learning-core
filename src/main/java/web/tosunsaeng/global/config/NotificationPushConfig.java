package web.tosunsaeng.global.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;
import web.tosunsaeng.domain.notifications.provider.DisabledPushNotificationSender;
import web.tosunsaeng.domain.notifications.provider.DisabledPushReceiptClient;
import web.tosunsaeng.domain.notifications.provider.ExpoPushNotificationSender;
import web.tosunsaeng.domain.notifications.provider.ExpoPushReceiptClient;
import web.tosunsaeng.domain.notifications.provider.PushNotificationSender;
import web.tosunsaeng.domain.notifications.provider.PushReceiptClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(NotificationPushProperties.class)
public class NotificationPushConfig {

    @Bean(name = "notificationMongoTransactionOperations")
    public TransactionOperations notificationMongoTransactionOperations(
            MongoDatabaseFactory databaseFactory) {
        return new TransactionTemplate(new MongoTransactionManager(databaseFactory));
    }

    @Bean(name = "expoPushRestTemplate")
    @ConditionalOnProperty(prefix = "app.notification.push", name = "enabled", havingValue = "true")
    public RestTemplate expoPushRestTemplate(
            RestTemplateBuilder builder,
            NotificationPushProperties properties) {
        return builder
                .connectTimeout(properties.connectTimeout())
                .readTimeout(properties.readTimeout())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.notification.push", name = "enabled", havingValue = "true")
    public PushNotificationSender expoPushNotificationSender(
            @Qualifier("expoPushRestTemplate") RestTemplate restTemplate,
            NotificationPushProperties properties) {
        return new ExpoPushNotificationSender(restTemplate, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.notification.push", name = "enabled", havingValue = "true")
    public PushReceiptClient expoPushReceiptClient(
            @Qualifier("expoPushRestTemplate") RestTemplate restTemplate,
            NotificationPushProperties properties) {
        return new ExpoPushReceiptClient(restTemplate, properties);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.notification.push",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    public PushNotificationSender disabledPushNotificationSender() {
        return new DisabledPushNotificationSender();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.notification.push",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    public PushReceiptClient disabledPushReceiptClient() {
        return new DisabledPushReceiptClient();
    }
}
