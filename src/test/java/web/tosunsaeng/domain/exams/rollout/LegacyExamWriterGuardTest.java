package web.tosunsaeng.domain.exams.rollout;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.dao.DataAccessResourceFailureException;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyExamWriterGuardTest {
    @Test void absentStateAllowsExistingWriterWithoutCreatingCollection() {
        var mongo = mock(MongoTemplate.class);
        var guard = new LegacyExamWriterGuard(mongo);
        guard.requireAllowed();
        verify(mongo).exists(argThat((Query q) -> "legacy-v1".equals(q.getQueryObject().get("_id"))),
                eq(PaidExamCutoverInspector.STATE));
        verifyNoMoreInteractions(mongo);
    }
    @Test void installedStateBlocksRegardlessOfFeatureFlagsOrStatusContents() {
        var mongo = mock(MongoTemplate.class);
        when(mongo.exists(any(Query.class), eq(PaidExamCutoverInspector.STATE))).thenReturn(true);
        var failure = assertThrows(ExamsException.class, () -> new LegacyExamWriterGuard(mongo).requireAllowed());
        assertEquals(ErrorStatus._EXAM_CREATION_PROCESSING, failure.getCode());
        assertEquals(1, failure.getRetryAfterSeconds());
    }
    @Test void databaseFailureCannotReopenLegacyWriter() {
        var mongo = mock(MongoTemplate.class);
        when(mongo.exists(any(Query.class), eq(PaidExamCutoverInspector.STATE)))
                .thenThrow(new DataAccessResourceFailureException("test unavailable"));
        assertThrows(ExamsException.class, () -> new LegacyExamWriterGuard(mongo).requireAllowed());
    }
}
