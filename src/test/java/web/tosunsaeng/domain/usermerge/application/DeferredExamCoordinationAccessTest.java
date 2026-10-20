package web.tosunsaeng.domain.usermerge.application;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import web.tosunsaeng.domain.usermerge.config.UserMergedProperties;
import web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard;
import web.tosunsaeng.domain.usermerge.domain.OwnershipGuardState;
import web.tosunsaeng.domain.learningrecorddeletion.application.DeletionAccess;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeferredExamCoordinationAccessTest {
    @Test void activeGuardIsReadWithoutStartingAnotherTransaction() {
        var service = mock(UserOwnershipGuardService.class);
        when(service.find("owner")).thenReturn(Optional.of(UserOwnershipGuard.active("owner", Instant.EPOCH)));
        var properties = new UserMergedProperties(); properties.setWriterEnabled(true);
        var executor = new UserOwnedTransactionExecutor(properties, service, null, Clock.systemUTC());
        assertDoesNotThrow(() -> executor.requireDeferredExamCoordinationAllowed("owner", "ex"));
        verify(service).find("owner");
        verifyNoMoreInteractions(service);
    }
    @Test void missingAndMergedGuardsAreDenied() {
        var service = mock(UserOwnershipGuardService.class);
        var properties = new UserMergedProperties(); properties.setWriterEnabled(true);
        var executor = new UserOwnedTransactionExecutor(properties, service, null, Clock.systemUTC());
        assertThrows(UserOwnershipGuardException.class, () -> executor.requireDeferredExamCoordinationAllowed("owner", "ex"));
        when(service.find("owner")).thenReturn(Optional.of(new UserOwnershipGuard("owner", OwnershipGuardState.MERGED,
                1L, "target", Instant.EPOCH, "event", Instant.EPOCH, Instant.EPOCH)));
        assertThrows(UserOwnershipGuardException.class, () -> executor.requireDeferredExamCoordinationAllowed("owner", "ex"));
    }
    @Test void deletionFenceDenialIsPropagated() {
        var service = mock(UserOwnershipGuardService.class);
        when(service.find("owner")).thenReturn(Optional.of(UserOwnershipGuard.active("owner", Instant.EPOCH)));
        var properties = new UserMergedProperties(); properties.setWriterEnabled(true);
        var executor = new UserOwnedTransactionExecutor(properties, service, null, Clock.systemUTC());
        var deletion = mock(DeletionAccess.class);
        ReflectionTestUtils.setField(executor, "deletionAccess", deletion);
        doAnswer(call -> ((java.util.function.Supplier<?>) call.getArgument(1)).get())
                .when(deletion).examCoordination(eq("ex"), any());
        var failure = new IllegalStateException("deletion blocked");
        doThrow(failure).when(deletion).requireWriter("owner");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> executor.requireDeferredExamCoordinationAllowed("owner", "ex")));
    }
}
