package web.tosunsaeng.domain.exams.attemptgroup.infrastructure;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(AttemptGroupEventProperties.class)
public class AttemptGroupEventConfiguration {

    @Bean(name = "attemptGroupMongoTransactionManager")
    @ConditionalOnProperty(
            prefix = "app.attempt-group-events",
            name = "writer-enabled",
            havingValue = "true"
    )
    public MongoTransactionManager attemptGroupMongoTransactionManager(
            MongoDatabaseFactory databaseFactory
    ) {
        return new MongoTransactionManager(databaseFactory);
    }

    @Bean(name = "attemptGroupTransactionOperations")
    @ConditionalOnProperty(
            prefix = "app.attempt-group-events",
            name = "writer-enabled",
            havingValue = "true"
    )
    public TransactionOperations attemptGroupTransactionOperations(
            @Qualifier("attemptGroupMongoTransactionManager") MongoTransactionManager manager
    ) {
        return new TransactionTemplate(manager);
    }
}
