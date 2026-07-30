package web.tosunsaeng.domain.exams.domain.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;
import web.tosunsaeng.domain.exams.application.GradingKeys;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;

import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;

@Repository
@RequiredArgsConstructor
public class ExamSessionCompletionQuery {

    private final MongoTemplate mongoTemplate;

    public List<CompletionCount> countCompletedByMockExamId(String userId) {
        Aggregation aggregation = newAggregation(
                match(new Criteria().andOperator(
                        Criteria.where("userId").is(userId),
                        Criteria.where("completedAt").exists(true).ne(null)
                )),
                project()
                        .and(ConditionalOperators.ifNull("mockExamId")
                                .then(GradingKeys.LEGACY_MOCK_EXAM_ID))
                        .as("mockExamId"),
                group("mockExamId").count().as("completionCount"),
                project("completionCount")
                        .and("_id").as("mockExamId")
                        .andExclude("_id")
        );

        return mongoTemplate.aggregate(aggregation, ExamSession.class, CompletionCount.class)
                .getMappedResults();
    }

    public record CompletionCount(String mockExamId, long completionCount) {
    }
}
