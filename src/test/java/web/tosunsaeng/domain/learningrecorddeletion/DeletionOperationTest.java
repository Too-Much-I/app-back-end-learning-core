package web.tosunsaeng.domain.learningrecorddeletion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.learningrecorddeletion.api.DeletionView;
import web.tosunsaeng.domain.learningrecorddeletion.domain.*;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class DeletionOperationTest {
    private static final String OWNER = "00000000-0000-0000-0000-000000000001";
    private static final String ID = "139f345b-9be8-43a3-a63d-9108df972741";
    private static final String DIGEST = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-10-03T01:00:00Z");

    @Test void canonicalKeyIsRequiredAndRawKeyIsNotStored() {
        var command = DeletionCommand.create(OWNER, ID, ID, NOW);
        assertThat(command.id()).hasSize(64).doesNotContain(ID);
        assertThat(command.expiresAt()).isNull();
        assertThat(DeletionIdentifiers.commandId(OWNER, ID)).isEqualTo(command.id());
        assertThat(DeletionIdentifiers.commandId("00000000-0000-0000-0000-000000000002", ID)).isNotEqualTo(command.id());
        for (String key : new String[]{ID.toUpperCase(), " " + ID, ID.replace("43a3", "13a3"), "bad"}) {
            assertThatThrownBy(() -> DeletionIdentifiers.commandId(OWNER, key)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void cannotSkipStagesOrReplaceSealedInventory() {
        var operation = DeletionOperation.requested(ID, OWNER, NOW);
        assertThat(operation.isWriteBlocked()).isTrue();
        assertThat(operation.getExpiresAt()).isNull();
        assertThatThrownBy(() -> operation.advance(DeletionOperation.Stage.VERIFYING)).isInstanceOf(IllegalStateException.class);
        operation.advance(DeletionOperation.Stage.FENCED);
        operation.advance(DeletionOperation.Stage.INVENTORYING);
        assertThatThrownBy(() -> operation.advance(DeletionOperation.Stage.WAITING_COORDINATION)).isInstanceOf(IllegalStateException.class);
        operation.sealInventory(2, DIGEST);
        operation.sealInventory(2, DIGEST);
        assertThatThrownBy(() -> operation.sealInventory(3, DIGEST)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> operation.sealInventory(2, "b".repeat(64))).isInstanceOf(IllegalStateException.class);
    }

    @Test void checkpointRequiresEverySafetyCondition() {
        for (int missing = 0; missing < 6; missing++) {
            var operation = preparing();
            var evidence = new DeletionOperation.RestartEvidence(DIGEST, missing == 0 ? 3 : 2,
                    missing == 1 ? 1 : 2, missing != 2, missing != 3, missing != 4, missing != 5);
            assertThatThrownBy(() -> operation.allowNewLearning(evidence, NOW)).isInstanceOf(IllegalStateException.class);
            assertThat(operation.isWriteBlocked()).isTrue();
        }
    }

    @Test void physicalDelayDoesNotBlockNewLearningOrReleaseActiveGuard() {
        var operation = safe();
        operation.requireIntervention(false);
        assertThat(operation.getStatus()).isEqualTo(DeletionOperation.Status.CLEANUP_DELAYED);
        assertThat(operation.isWriteBlocked()).isFalse();
        assertThat(operation.isActiveGuard()).isTrue();
        assertThat(operation.getExpiresAt()).isNull();
        operation.resumeCleanup();
        assertThat(operation.getStage()).isEqualTo(DeletionOperation.Stage.SAFE_TO_START_LEARNING);
        operation.requireIntervention(true);
        assertThat(operation.isWriteBlocked()).isTrue();
        assertThatThrownBy(operation::resumeCleanup).isInstanceOf(IllegalStateException.class);
    }

    @Test void precheckpointDelayNeedsReview() {
        var operation = preparing();
        operation.requireIntervention(false);
        assertThat(operation.getStatus()).isEqualTo(DeletionOperation.Status.NEEDS_REVIEW);
        assertThat(operation.isWriteBlocked()).isTrue();
    }

    @Test void completionRequiresCapabilityExpiryAndZeroResiduals() {
        var operation = safe();
        operation.advance(DeletionOperation.Stage.DELETING_S3);
        operation.advance(DeletionOperation.Stage.DELETING_MONGO);
        operation.advance(DeletionOperation.Stage.CLEARING_CACHE);
        operation.advance(DeletionOperation.Stage.VERIFYING);
        assertThatThrownBy(() -> operation.complete(new DeletionOperation.CompletionEvidence(0, 0, 0, true, NOW), NOW))
                .isInstanceOf(IllegalStateException.class);
        Instant swept = NOW.plusSeconds(361);
        assertThatThrownBy(() -> operation.complete(new DeletionOperation.CompletionEvidence(1, 0, 0, true, swept), swept))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> operation.complete(new DeletionOperation.CompletionEvidence(0, 0, 1, true, swept), swept))
                .isInstanceOf(IllegalStateException.class);
        operation.complete(new DeletionOperation.CompletionEvidence(0, 0, 0, true, swept), swept);
        assertThat(operation.isActiveGuard()).isFalse();
        assertThat(operation.getExpiresAt()).isEqualTo(swept.plusSeconds(30 * 86400L));
        assertThatThrownBy(() -> operation.requireIntervention(false)).isInstanceOf(IllegalStateException.class);
    }

    @Test void publicProjectionContainsOnlyAllowlistedFields() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var json = mapper.readTree(mapper.writeValueAsString(DeletionView.from(safe())));
        var fields = new java.util.HashSet<String>();
        json.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).isEqualTo(Set.of("deletionId", "status", "canStartLearning", "requestedAt",
                "physicalDeletionTargetAt", "completedAt"));
        assertThat(DeletionView.from(null).status()).isEqualTo("not_requested");
        assertThat(DeletionView.from(null).canStartLearning()).isTrue();
    }

    @Test void targetCannotAcceptBroadStoragePrefixOrSealBeforeCoordination() {
        for (String id : new String[]{"", "../", "a/b", "a:other", "a b", "challenges"}) {
            assertThatThrownBy(() -> DeletionTarget.draining(ID, OWNER, DeletionTarget.Type.EXAM, id))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        var target = DeletionTarget.draining(ID, OWNER, DeletionTarget.Type.EXAM, "ex_test_1");
        assertThat(target.s3Prefix()).isEqualTo("temp/ex_test_1/");
        assertThat(target.redisKey()).isEqualTo("exam:status:ex_test_1");
        assertThatThrownBy(() -> target.sealCallbacks(true, false, NOW)).isInstanceOf(IllegalStateException.class);
        target.sealCallbacks(true, true, NOW);
        target.sealCallbacks(true, true, NOW.plusSeconds(1));
        assertThat(target.getCallbackSealedAt()).isEqualTo(NOW);
        var challenge = DeletionTarget.draining(ID, OWNER, DeletionTarget.Type.CHALLENGE, ID);
        assertThat(challenge.s3Prefix()).isEqualTo("temp/challenges/" + ID + "/");
        assertThat(challenge.redisKey()).isNull();
    }

    private static DeletionOperation preparing() {
        var operation = DeletionOperation.requested(ID, OWNER, NOW);
        operation.advance(DeletionOperation.Stage.FENCED);
        operation.advance(DeletionOperation.Stage.INVENTORYING);
        operation.sealInventory(2, DIGEST);
        operation.advance(DeletionOperation.Stage.WAITING_COORDINATION);
        operation.advance(DeletionOperation.Stage.PREPARING_RESTART);
        return operation;
    }

    private static DeletionOperation safe() {
        var operation = preparing();
        operation.allowNewLearning(new DeletionOperation.RestartEvidence(DIGEST, 2, 2, true, true, true, true), NOW);
        return operation;
    }
}
