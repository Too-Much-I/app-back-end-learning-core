package web.tosunsaeng.domain.exams.domain.repository;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import web.tosunsaeng.domain.exams.application.GradingKeys;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamSessionCompletionQueryTest {

    @Test
    void aggregatesOnlyCurrentUsersCompletedSessionsWithLegacyMockExamFallback() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        List<ExamSessionCompletionQuery.CompletionCount> mapped = List.of(
                new ExamSessionCompletionQuery.CompletionCount("mock_exam_001", 2),
                new ExamSessionCompletionQuery.CompletionCount(GradingKeys.LEGACY_MOCK_EXAM_ID, 1)
        );
        when(mongoTemplate.aggregate(
                any(Aggregation.class),
                eq(ExamSession.class),
                eq(ExamSessionCompletionQuery.CompletionCount.class)
        )).thenReturn(new AggregationResults<>(mapped, new Document()));
        ExamSessionCompletionQuery query = new ExamSessionCompletionQuery(mongoTemplate);

        List<ExamSessionCompletionQuery.CompletionCount> result =
                query.countCompletedByMockExamId("00000000-0000-0000-0000-000000000031");

        assertEquals(mapped, result);
        ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongoTemplate).aggregate(
                aggregationCaptor.capture(),
                eq(ExamSession.class),
                eq(ExamSessionCompletionQuery.CompletionCount.class)
        );
        String pipeline = aggregationCaptor.getValue()
                .toPipeline(Aggregation.DEFAULT_CONTEXT)
                .toString();
        assertTrue(pipeline.contains("00000000-0000-0000-0000-000000000031"));
        assertTrue(pipeline.contains("completedAt"));
        assertTrue(pipeline.contains("$exists=true"));
        assertTrue(pipeline.contains("$ne=null"));
        assertTrue(pipeline.contains("$ifNull"));
        assertTrue(pipeline.contains(GradingKeys.LEGACY_MOCK_EXAM_ID));
        assertTrue(pipeline.contains("$group"));
    }
}
