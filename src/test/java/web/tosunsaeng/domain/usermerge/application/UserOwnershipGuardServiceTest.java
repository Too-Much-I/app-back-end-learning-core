package web.tosunsaeng.domain.usermerge.application;

import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import web.tosunsaeng.domain.usermerge.domain.OwnershipGuardState;
import web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserOwnershipGuardServiceTest {

    private static final String SOURCE = "73a18ed4-1d56-4c4f-afd6-b39175b82a86";
    private static final String TARGET = "45c05c3f-ae7f-4ca7-af88-3ab8aa8f428e";
    private static final Instant NOW = Instant.parse("2026-09-04T01:00:00Z");

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private UserOwnershipGuardRepository repository;

    private UserOwnershipGuardService service;

    @BeforeEach
    void setUp() {
        service = new UserOwnershipGuardService(mongoTemplate, repository);
    }

    @Test
    void touchesExistingActiveGuardWithoutInsert() {
        when(mongoTemplate.updateFirst(any(), any(), any(Class.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));

        service.touchActive(SOURCE, NOW);

        verify(repository, never()).findById(any());
        verify(repository, never()).insert(any(UserOwnershipGuard.class));
    }

    @Test
    void insertsActiveGuardWhenItIsAbsent() {
        when(mongoTemplate.updateFirst(any(), any(), any(Class.class)))
                .thenReturn(UpdateResult.acknowledged(0L, 0L, null));
        when(repository.findById(SOURCE)).thenReturn(Optional.empty());

        service.touchActive(SOURCE, NOW);

        verify(repository).insert(any(UserOwnershipGuard.class));
    }

    @Test
    void mergedGuardRejectsFurtherWrites() {
        when(mongoTemplate.updateFirst(any(), any(), any(Class.class)))
                .thenReturn(UpdateResult.acknowledged(0L, 0L, null));
        when(repository.findById(SOURCE)).thenReturn(Optional.of(new UserOwnershipGuard(
                SOURCE,
                OwnershipGuardState.MERGED,
                1L,
                TARGET,
                NOW,
                "9a88bc80-d73a-4a3d-8f68-492641d27208",
                NOW,
                NOW
        )));

        assertThatThrownBy(() -> service.touchActive(SOURCE, NOW))
                .isInstanceOfSatisfying(UserOwnershipGuardException.class, failure ->
                        org.assertj.core.api.Assertions.assertThat(failure.getReason())
                                .isEqualTo(UserOwnershipGuardException.Reason.ALREADY_MERGED));
    }

    @Test
    void mergeRequiresAnActiveSourceGuard() {
        when(mongoTemplate.updateFirst(any(), any(), any(Class.class)))
                .thenReturn(UpdateResult.acknowledged(0L, 0L, null));

        assertThatThrownBy(() -> service.markMerged(
                SOURCE,
                TARGET,
                "9a88bc80-d73a-4a3d-8f68-492641d27208",
                NOW,
                NOW
        )).isInstanceOf(UserOwnershipGuardException.class);
    }
}
