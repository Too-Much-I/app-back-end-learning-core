package web.tosunsaeng.domain.exams.billing;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.core.StreamReadFeature;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BillingSagaProperties.class)
public class BillingSagaConfiguration {

    @Bean
    public BillingSagaConfigurationValidator billingSagaConfigurationValidator(
            BillingSagaProperties properties,
            Environment environment
    ) {
        return new BillingSagaConfigurationValidator(properties, environment);
    }

    @Bean(name = "billingContractObjectMapper")
    public ObjectMapper billingContractObjectMapper() {
        return JsonMapper.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .withCoercionConfig(LogicalType.Textual, coercion -> coercion
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.DateTime, coercion -> coercion
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .findAndAddModules()
                .build();
    }

    @Bean
    public BillingReservationClient billingReservationClient(
            BillingSagaProperties properties,
            AwsCredentialsProvider credentialsProvider,
            @Qualifier("billingContractObjectMapper") ObjectMapper objectMapper
    ) {
        return new SigV4BillingReservationClient(properties, credentialsProvider, objectMapper);
    }

    @Bean(name = "billingMongoTransactionManager")
    @Primary
    @ConditionalOnProperty(
            prefix = "app.billing",
            name = "creation-saga-enabled",
            havingValue = "true"
    )
    public MongoTransactionManager billingMongoTransactionManager(
            MongoDatabaseFactory databaseFactory
    ) {
        return new MongoTransactionManager(databaseFactory);
    }

    @Bean(name = "billingTransactionOperations")
    @ConditionalOnProperty(
            prefix = "app.billing",
            name = "creation-saga-enabled",
            havingValue = "true"
    )
    public TransactionOperations billingTransactionOperations(
            @Qualifier("billingMongoTransactionManager") MongoTransactionManager transactionManager
    ) {
        return new TransactionTemplate(transactionManager);
    }
}
