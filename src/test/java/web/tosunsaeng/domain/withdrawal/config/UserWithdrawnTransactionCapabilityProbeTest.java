package web.tosunsaeng.domain.withdrawal.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserWithdrawnTransactionCapabilityProbeTest {

    private MongoTemplate mongoTemplate;
    private TransactionOperations transactionOperations;
    private TransactionStatus transactionStatus;
    private UserWithdrawnTransactionCapabilityProbe probe;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        transactionOperations = mock(TransactionOperations.class);
        transactionStatus = mock(TransactionStatus.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC);
        probe = new UserWithdrawnTransactionCapabilityProbe(
                mongoTemplate,
                transactionOperations,
                clock
        );
    }

    @Test
    void writesCanaryInsideTransactionAndRequiresRollbackToRemoveIt() {
        executeCallback();
        when(mongoTemplate.exists(any(Query.class), anyString())).thenReturn(false);

        assertDoesNotThrow(() -> probe.run(null));

        verify(mongoTemplate).insert(any(org.bson.Document.class),
                org.mockito.ArgumentMatchers.eq(UserWithdrawnTransactionCapabilityProbe.COLLECTION));
        verify(transactionStatus).setRollbackOnly();
    }

    @Test
    void failsStartupWhenCanaryRemainsAfterRollback() {
        executeCallback();
        when(mongoTemplate.exists(any(Query.class), anyString())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> probe.run(null));
    }

    @Test
    void failsStartupWhenTransactionCannotStartOrWrite() {
        doThrow(new IllegalStateException("transaction unavailable"))
                .when(transactionOperations)
                .executeWithoutResult(any());

        assertThrows(IllegalStateException.class, () -> probe.run(null));
    }

    @SuppressWarnings("unchecked")
    private void executeCallback() {
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(transactionStatus);
            return null;
        }).when(transactionOperations).executeWithoutResult(any());
    }
}
