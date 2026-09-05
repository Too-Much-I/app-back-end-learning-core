package web.tosunsaeng.domain.usermerge.application;

import com.mongodb.MongoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionOperations;
import web.tosunsaeng.domain.usermerge.config.UserMergedProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserOwnedTransactionExecutorTest {

    @Mock
    private UserOwnershipGuardService guardService;
    @Mock
    private ObjectProvider<TransactionOperations> transactionProvider;
    @Mock
    private TransactionOperations transactions;

    private UserOwnedTransactionExecutor executor;

    @BeforeEach
    void setUp() {
        UserMergedProperties properties = new UserMergedProperties();
        properties.setWriterEnabled(true);
        when(transactionProvider.getIfAvailable()).thenReturn(transactions);
        executor = new UserOwnedTransactionExecutor(
                properties,
                guardService,
                transactionProvider,
                Clock.fixed(Instant.parse("2026-09-05T01:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void unknownCommitIsWrappedWithoutReplayingTheCommand() {
        TransactionSystemException unknownCommit = unknownCommit();
        doThrow(unknownCommit).when(transactions).execute(any());

        assertThatThrownBy(() -> executor.executeWithoutGuard(() -> "not-observed"))
                .isInstanceOf(UserOwnedCommitOutcomeUnknownException.class)
                .hasCause(unknownCommit);

        verify(transactions, times(1)).execute(any());
    }

    @Test
    void unknownCommitWinsOverOuterDuplicateClassification() {
        DuplicateKeyException wrapped = new DuplicateKeyException("outer race", unknownCommit());
        doThrow(wrapped).when(transactions).execute(any());

        assertThatThrownBy(() -> executor.executeWithoutGuard(() -> "not-observed"))
                .isInstanceOf(UserOwnedCommitOutcomeUnknownException.class)
                .hasCause(wrapped);

        verify(transactions, times(1)).execute(any());
    }

    @Test
    void transientTransactionFailureStillRetriesWithinTheBound() {
        MongoException mongoFailure = new MongoException("transient transaction");
        mongoFailure.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        TransactionSystemException transientFailure =
                new TransactionSystemException("transient", mongoFailure);
        doThrow(transientFailure)
                .doAnswer(invocation -> "committed")
                .when(transactions).execute(any());

        assertThat(executor.executeWithoutGuard(() -> "command-result"))
                .isEqualTo("committed");

        verify(transactions, times(2)).execute(any());
    }

    private static TransactionSystemException unknownCommit() {
        MongoException mongoFailure = new MongoException("unknown commit result");
        mongoFailure.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        return new TransactionSystemException("commit result unknown", mongoFailure);
    }
}
