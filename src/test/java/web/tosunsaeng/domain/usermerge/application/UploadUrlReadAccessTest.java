package web.tosunsaeng.domain.usermerge.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionOperations;
import web.tosunsaeng.domain.usermerge.config.UserMergedProperties;
import web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard;
import web.tosunsaeng.domain.usermerge.domain.OwnershipGuardState;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class UploadUrlReadAccessTest {
    @Test
    @SuppressWarnings("unchecked")
    void activeOrMissingGuardDoesNotStartTransactionOrWrite() {
        var guards = mock(UserOwnershipGuardService.class);
        ObjectProvider<TransactionOperations> transactions = mock(ObjectProvider.class);
        var properties = new UserMergedProperties();
        properties.setWriterEnabled(true);
        var executor = new UserOwnedTransactionExecutor(properties, guards, transactions, Clock.systemUTC());
        when(guards.find("owner")).thenReturn(Optional.of(UserOwnershipGuard.active("owner", Instant.EPOCH)), Optional.empty());
        executor.requireUploadUrlAccess("owner");
        executor.requireUploadUrlAccess("owner");
        verify(guards, times(2)).find("owner");
        verifyNoMoreInteractions(guards);
        verifyNoInteractions(transactions);
    }

    @Test
    @SuppressWarnings("unchecked")
    void mergedGuardStillRejectsWithoutWriting() {
        var guards = mock(UserOwnershipGuardService.class);
        ObjectProvider<TransactionOperations> transactions = mock(ObjectProvider.class);
        var properties = new UserMergedProperties();
        properties.setWriterEnabled(true);
        var executor = new UserOwnedTransactionExecutor(properties, guards, transactions, Clock.systemUTC());
        when(guards.find("source")).thenReturn(Optional.of(new UserOwnershipGuard(
                "source", OwnershipGuardState.MERGED, 1L, "target", Instant.EPOCH, "event", Instant.EPOCH, Instant.EPOCH)));
        assertThrows(UserOwnershipGuardException.class, () -> executor.requireUploadUrlAccess("source"));
        verify(guards).find("source");
        verifyNoMoreInteractions(guards);
        verifyNoInteractions(transactions);
    }
}
