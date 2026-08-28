package web.tosunsaeng.domain.withdrawal.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.withdrawal.domain.UserWithdrawnEventInbox;
import web.tosunsaeng.domain.withdrawal.domain.WithdrawnUserAccessDeny;

import java.time.Duration;
import java.util.List;

@Component
@Profile("!test")
@ConditionalOnExpression("${app.user-withdrawn.consumer-enabled:false} || "
        + "${app.user-withdrawn.deny-gate-enabled:false}")
public class UserWithdrawnIndexValidator implements ApplicationRunner {

    public static final String INBOX_TTL_INDEX = "ttl_user_withdrawn_inbox_cleanup";
    public static final String DENY_TTL_INDEX = "ttl_withdrawn_user_access_deny_expire";
    private static final Profiles STAGING_OR_PROD = Profiles.of("staging", "prod");

    private final MongoTemplate mongoTemplate;
    private final Environment environment;
    private final UserWithdrawnConsumerProperties properties;

    public UserWithdrawnIndexValidator(
            MongoTemplate mongoTemplate,
            Environment environment,
            UserWithdrawnConsumerProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.environment = environment;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (environment.acceptsProfiles(STAGING_OR_PROD)) {
            validateExisting(WithdrawnUserAccessDeny.class, DENY_TTL_INDEX, "expireAt");
            if (properties.isConsumerEnabled()) {
                validateExisting(UserWithdrawnEventInbox.class, INBOX_TTL_INDEX, "cleanupAt");
            }
            return;
        }
        ensure(WithdrawnUserAccessDeny.class, DENY_TTL_INDEX, "expireAt");
        if (properties.isConsumerEnabled()) {
            ensure(UserWithdrawnEventInbox.class, INBOX_TTL_INDEX, "cleanupAt");
        }
    }

    private void ensure(Class<?> entityType, String name, String field) {
        mongoTemplate.indexOps(entityType).ensureIndex(
                new Index().on(field, Sort.Direction.ASC).expire(Duration.ZERO).named(name)
        );
    }

    private void validateExisting(Class<?> entityType, String name, String field) {
        List<IndexInfo> indexes = mongoTemplate.indexOps(entityType).getIndexInfo();
        boolean valid = indexes.stream().anyMatch(index -> name.equals(index.getName())
                && index.getIndexFields().size() == 1
                && field.equals(index.getIndexFields().getFirst().getKey())
                && Sort.Direction.ASC == index.getIndexFields().getFirst().getDirection()
                && index.getExpireAfter().filter(Duration.ZERO::equals).isPresent());
        if (!valid) {
            throw new IllegalStateException("Required UserWithdrawn TTL index is missing or incompatible: " + name);
        }
    }
}
