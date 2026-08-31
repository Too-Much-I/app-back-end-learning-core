package web.tosunsaeng.domain.exams.billing;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.mock.env.MockEnvironment;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BillingSagaIndexValidatorTest {

    @Test
    void missingIndexesFailClosedInStagingAndOnlyWarnLocally() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        IndexOperations operationIndexes = mock(IndexOperations.class);
        IndexOperations sessionIndexes = mock(IndexOperations.class);
        when(mongoTemplate.indexOps(ExamCreationOperation.class)).thenReturn(operationIndexes);
        when(mongoTemplate.indexOps(ExamSession.class)).thenReturn(sessionIndexes);
        when(operationIndexes.getIndexInfo()).thenReturn(List.of());
        when(sessionIndexes.getIndexInfo()).thenReturn(List.of());

        BillingSagaIndexValidator validator = new BillingSagaIndexValidator(
                mongoTemplate, new MockEnvironment());

        assertDoesNotThrow(() -> validator.validate(false));
        assertThrows(IllegalStateException.class, () -> validator.validate(true));
    }
}
