package web.tosunsaeng.domain.withdrawal.config;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.mock.env.MockEnvironment;
import web.tosunsaeng.domain.withdrawal.domain.UserWithdrawnEventInbox;
import web.tosunsaeng.domain.withdrawal.domain.WithdrawnUserAccessDeny;

import java.util.List;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class UserWithdrawnIndexValidatorTest {

    private MongoTemplate mongoTemplate;
    private IndexOperations inboxIndexes;
    private IndexOperations denyIndexes;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        inboxIndexes = mock(IndexOperations.class);
        denyIndexes = mock(IndexOperations.class);
        when(mongoTemplate.indexOps(UserWithdrawnEventInbox.class)).thenReturn(inboxIndexes);
        when(mongoTemplate.indexOps(WithdrawnUserAccessDeny.class)).thenReturn(denyIndexes);
    }

    @Test
    void localEnvironmentEnsuresBothTtlIndexes() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        new UserWithdrawnIndexValidator(mongoTemplate, environment, bothEnabled()).run(null);

        verify(inboxIndexes).ensureIndex(any(IndexDefinition.class));
        verify(denyIndexes).ensureIndex(any(IndexDefinition.class));
    }

    @Test
    void gateOnlyEnvironmentEnsuresOnlyMarkerTtlIndex() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        UserWithdrawnConsumerProperties properties = new UserWithdrawnConsumerProperties();
        properties.setDenyGateEnabled(true);

        new UserWithdrawnIndexValidator(mongoTemplate, environment, properties).run(null);

        verify(denyIndexes).ensureIndex(any(IndexDefinition.class));
        verify(inboxIndexes, never()).ensureIndex(any(IndexDefinition.class));
    }

    @Test
    void productionRequiresExactTtlIndexes() {
        IndexInfo inboxTtl = ttlIndex(
                UserWithdrawnIndexValidator.INBOX_TTL_INDEX,
                "cleanupAt"
        );
        IndexInfo denyTtl = ttlIndex(
                UserWithdrawnIndexValidator.DENY_TTL_INDEX,
                "expireAt"
        );
        assertEquals(Duration.ZERO, inboxTtl.getExpireAfter().orElseThrow());
        assertEquals(Duration.ZERO, denyTtl.getExpireAfter().orElseThrow());
        when(inboxIndexes.getIndexInfo()).thenReturn(List.of(inboxTtl));
        when(denyIndexes.getIndexInfo()).thenReturn(List.of(denyTtl));
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertDoesNotThrow(() -> new UserWithdrawnIndexValidator(
                mongoTemplate,
                environment,
                bothEnabled()
        ).run(null));
    }

    @Test
    void productionFailsClosedForMissingOrWrongIndex() {
        when(inboxIndexes.getIndexInfo()).thenReturn(List.of());
        when(denyIndexes.getIndexInfo()).thenReturn(List.of(ttlIndex(
                UserWithdrawnIndexValidator.DENY_TTL_INDEX,
                "wrongField"
        )));
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("staging");

        assertThrows(
                IllegalStateException.class,
                () -> new UserWithdrawnIndexValidator(
                        mongoTemplate,
                        environment,
                        bothEnabled()
                ).run(null)
        );
    }

    private static UserWithdrawnConsumerProperties bothEnabled() {
        UserWithdrawnConsumerProperties properties = new UserWithdrawnConsumerProperties();
        properties.setConsumerEnabled(true);
        properties.setDenyGateEnabled(true);
        return properties;
    }

    private static IndexInfo ttlIndex(String name, String field) {
        return IndexInfo.indexInfoOf(new Document("name", name)
                .append("key", new Document(field, 1))
                .append("expireAfterSeconds", 0L));
    }
}
