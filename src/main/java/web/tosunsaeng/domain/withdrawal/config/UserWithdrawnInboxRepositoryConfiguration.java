package web.tosunsaeng.domain.withdrawal.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import web.tosunsaeng.domain.withdrawal.repository.UserWithdrawnEventInboxRepository;
import web.tosunsaeng.domain.withdrawal.repository.WithdrawnUserAccessDenyRepository;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.user-withdrawn", name = "consumer-enabled", havingValue = "true")
@EnableMongoRepositories(
        basePackageClasses = UserWithdrawnEventInboxRepository.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = WithdrawnUserAccessDenyRepository.class
        )
)
public class UserWithdrawnInboxRepositoryConfiguration {
}
