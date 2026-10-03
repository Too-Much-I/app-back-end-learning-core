package web.tosunsaeng.domain.learningrecorddeletion;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupProjectionStatus;
import web.tosunsaeng.domain.learningrecorddeletion.application.*;
import web.tosunsaeng.domain.learningrecorddeletion.domain.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeletionAccessTest {
    @org.junit.jupiter.api.BeforeEach void transaction() { org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true); }
    @org.junit.jupiter.api.AfterEach void clearTransaction() { org.springframework.transaction.support.TransactionSynchronizationManager.clear(); }
    final MongoTemplate mongo = mock(MongoTemplate.class);
    final DeletionAccess access = new DeletionAccess(mongo);
    final String owner = "00000000-0000-0000-0000-000000000001";
    final DeletionOperation operation = DeletionOperation.requested("139f345b-9be8-43a3-a63d-9108df972741", owner, Instant.EPOCH);

    @Test void drainingGradingIsAllowedButPublicMutationAndSealedCallbacksAreNot() {
        when(mongo.findOne(any(Query.class), eq(DeletionOperation.class))).thenReturn(operation);
        when(mongo.findById("old", ExamSession.class)).thenReturn(ExamSession.builder().examId("old").userId(owner)
                .attemptGroupProjectionStatus(AttemptGroupProjectionStatus.GRADING).build());
        assertThatCode(() -> access.examCoordination("old", () -> { access.requireWriter(owner); return null; })).doesNotThrowAnyException();
        assertThatThrownBy(() -> access.requirePublicWrite(owner)).isInstanceOf(DeletionFailure.class);
        assertThatThrownBy(() -> access.requireWriter(owner)).isInstanceOf(DeletionFailure.class);
        when(mongo.exists(any(Query.class), eq(DeletionTarget.class))).thenReturn(true);
        assertThatThrownBy(() -> access.examCoordination("old", () -> { access.requireWriter(owner); return null; })).isInstanceOf(DeletionFailure.class);
    }
    @Test void openGroupCannotPretendToBeGradingAndWrongOwnerCannotDrain() {
        when(mongo.findOne(any(Query.class), eq(DeletionOperation.class))).thenReturn(operation);
        when(mongo.findById("old", ExamSession.class)).thenReturn(ExamSession.builder().examId("old").userId(owner)
                .attemptGroupProjectionStatus(AttemptGroupProjectionStatus.OPEN).build());
        assertThatThrownBy(() -> access.examCoordination("old", () -> { access.requireWriter(owner); return null; })).isInstanceOf(DeletionFailure.class);
        when(mongo.findById("old", ExamSession.class)).thenReturn(ExamSession.builder().examId("old").userId("another-owner")
                .attemptGroupProjectionStatus(AttemptGroupProjectionStatus.GRADING).build());
        assertThatThrownBy(() -> access.examCoordination("old", () -> { access.requireWriter(owner); return null; })).isInstanceOf(DeletionFailure.class);
    }
}
