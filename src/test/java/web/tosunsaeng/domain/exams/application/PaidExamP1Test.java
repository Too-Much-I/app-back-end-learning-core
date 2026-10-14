package web.tosunsaeng.domain.exams.application;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import web.tosunsaeng.domain.exams.domain.entity.*;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaidExamP1Test {
    @Test void rolloutIsOffAndCannotEnableIncompletePaidFlow() {
        new PaidExamActivationGate(false).afterPropertiesSet();
        assertThatThrownBy(() -> new PaidExamActivationGate(true).afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
    }
    @Test void snapshotIsImmutableAndCannotBeReplacedOrClaimedByLegacyWorker() {
        var operation = ExamCreationOperation.prepared("owner", "key", "session", "mock", 1, Instant.EPOCH);
        operation.fixPaidSelection(new PaidExamSelection(PaidExamSelection.StartReason.NEW_AFTER_SUBMISSION, List.of(1, 2)));
        assertThat(operation.getRecovery().getSchemaVersion()).isEqualTo(2);
        assertThat(operation.getPurgeAt()).isNull();
        assertThatThrownBy(() -> operation.getPaidSelection().requiredQuestionNumbers().add(3)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> operation.fixPaidSelection(operation.getPaidSelection())).isInstanceOf(IllegalStateException.class);
    }
    @Test void requiredSnapshotRejectsMissingAndInvalidNumbers() {
        assertThat(ExamSubmissionEvidence.required(List.of(2, 1, 2))).containsExactly(1, 2);
        assertThatThrownBy(() -> ExamSubmissionEvidence.required(List.of())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ExamSubmissionEvidence.required(List.of(0))).isInstanceOf(IllegalStateException.class);
    }
    @Test void unknownCoverageAndMissingTransactionFailClosed() {
        var mongo = mock(MongoTemplate.class);
        var evidence = new ExamSubmissionEvidence(mongo);
        assertThat(evidence.covered(ExamSession.builder().build())).isFalse();
        assertThatThrownBy(() -> evidence.covered(ExamSession.builder().submissionEvidenceVersion(2).build())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> evidence.accepted("s", 1, 0, List.of(1), Instant.EPOCH)).isInstanceOf(IllegalStateException.class);
        evidence.accepted("s", 1, 1, List.of(1), Instant.EPOCH);
        verifyNoInteractions(mongo);
    }
}
