package web.tosunsaeng.domain.exams.billing;

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

class BillingSagaTransactionCapabilityProbeTest {

    private MongoTemplate mongoTemplate;
    private TransactionOperations transactionOperations;
    private TransactionStatus transactionStatus;
    private BillingSagaTransactionCapabilityProbe probe;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        transactionOperations = mock(TransactionOperations.class);
        transactionStatus = mock(TransactionStatus.class);
        probe = new BillingSagaTransactionCapabilityProbe(
                mongoTemplate,
                transactionOperations,
                Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void verifiesRollbackRemovesCanary() {
        executeCallback();
        when(mongoTemplate.exists(any(Query.class), anyString())).thenReturn(false);

        assertDoesNotThrow(() -> probe.run(null));

        verify(transactionStatus).setRollbackOnly();
    }

    @Test
    void failsWhenMongoTransactionsAreUnavailable() {
        doThrow(new IllegalStateException("transaction unavailable"))
                .when(transactionOperations).executeWithoutResult(any());

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
