package web.tosunsaeng.domain.withdrawal.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import web.tosunsaeng.domain.withdrawal.repository.UserWithdrawnEventInboxRepository;
import web.tosunsaeng.domain.withdrawal.repository.WithdrawnUserAccessDenyRepository;

@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("${app.user-withdrawn.consumer-enabled:false} || "
        + "${app.user-withdrawn.deny-gate-enabled:false}")
@EnableMongoRepositories(
        basePackageClasses = WithdrawnUserAccessDenyRepository.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = UserWithdrawnEventInboxRepository.class
        )
)
public class UserWithdrawnDenyRepositoryConfiguration {
}
