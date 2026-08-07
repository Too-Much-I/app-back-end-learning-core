package web.tosunsaeng.domain.exams.domain.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamReadRepositoryContractTest {

    @Test
    void completedHistoryQueryUsesUserAndCompletedAtWithDeterministicSort() throws Exception {
        Query query = query(
                ExamSessionRepository.class,
                "findCompletedByUserId",
                String.class
        );

        assertAll(
                () -> assertTrue(query.value().contains("'userId': ?0")),
                () -> assertTrue(query.value().contains("'completedAt'")),
                () -> assertTrue(query.value().contains("'$exists': true")),
                () -> assertTrue(query.value().contains("'$ne': null")),
                () -> assertFalse(query.value().contains("'active'")),
                () -> assertEquals("{ 'completedAt': -1, '_id': -1 }", query.sort())
        );
    }

    @Test
    void historyMetadataRepositoriesExposeBatchQueriesInsteadOfFullCollectionReads() throws Exception {
        Query mockExamQuery = query(
                MockExamRepository.class,
                "findTitlesByMockExamIdIn",
                Collection.class
        );
        Query summaryQuery = query(
                ExamSummaryRepository.class,
                "findHistoryCandidatesByExamIdIn",
                Collection.class
        );
        Query legacySummaryQuery = query(
                ExamResultRepository.class,
                "findLegacySummaryCandidatesByExamIdIn",
                Collection.class
        );
        Query retriedJobQuery = query(
                QuestionGradingJobRepository.class,
                "findRetriedQuestionCandidatesByExamIdIn",
                Collection.class
        );
        Query retriedResultQuery = query(
                ExamResultRepository.class,
                "findRetriedQuestionCandidatesByExamIdIn",
                Collection.class
        );

        assertAll(
                () -> assertTrue(mockExamQuery.value().contains("'$in': ?0")),
                () -> assertTrue(mockExamQuery.fields().contains("'title': 1")),
                () -> assertFalse(mockExamQuery.fields().contains("'questions': 1")),
                () -> assertTrue(summaryQuery.value().contains("'$in': ?0")),
                () -> assertEquals("{ 'examId': 1, '_id': -1 }", summaryQuery.sort()),
                () -> assertTrue(legacySummaryQuery.value().contains("'totalScore'")),
                () -> assertTrue(legacySummaryQuery.value().contains("'$ne': null")),
                () -> assertEquals("{ 'examId': 1, '_id': -1 }", legacySummaryQuery.sort()),
                () -> assertTrue(retriedJobQuery.value().contains("'examId': { '$in': ?0 }")),
                () -> assertTrue(retriedJobQuery.value().contains("'retryCount': { '$gte': 1 }")),
                () -> assertTrue(retriedJobQuery.fields().contains("'questionNumber': 1")),
                () -> assertFalse(retriedJobQuery.fields().contains("dispatchAttempt")),
                () -> assertTrue(retriedResultQuery.value().contains("'examId': { '$in': ?0 }")),
                () -> assertTrue(retriedResultQuery.value().contains("'retryCount': { '$gte': 1 }")),
                () -> assertTrue(retriedResultQuery.fields().contains("'questionNumber': 1")),
                () -> assertFalse(retriedResultQuery.fields().contains("feedback"))
        );
    }

    @Test
    void retryQueriesLoadOnlyOneExamAndDoNotProjectDispatchOrFeedbackPayloads() throws Exception {
        Query jobQuery = query(
                QuestionGradingJobRepository.class,
                "findAttemptsByExamId",
                String.class
        );
        Query resultQuery = query(
                ExamResultRepository.class,
                "findQuestionAttemptsByExamId",
                String.class
        );

        assertAll(
                () -> assertEquals("{ 'examId': ?0 }", jobQuery.value()),
                () -> assertTrue(jobQuery.fields().contains("'retryCount': 1")),
                () -> assertTrue(jobQuery.fields().contains("'status': 1")),
                () -> assertFalse(jobQuery.fields().contains("dispatchAttempt")),
                () -> assertFalse(jobQuery.fields().contains("failureReason")),
                () -> assertTrue(resultQuery.value().contains("'examId': ?0")),
                () -> assertTrue(resultQuery.value().contains("'questionNumber'")),
                () -> assertFalse(resultQuery.fields().contains("score")),
                () -> assertFalse(resultQuery.fields().contains("feedback")),
                () -> assertFalse(resultQuery.fields().contains("transcript"))
        );
    }

    @Test
    void sessionLifecycleQueriesUseExplicitStatusAndConditionalUpdates() throws Exception {
        Query candidates = query(
                ExamSessionRepository.class,
                "findActiveOrLegacyCandidatesByUserId",
                String.class
        );
        Query abandon = query(
                ExamSessionRepository.class,
                "abandonIfInProgress",
                String.class
        );
        Update abandonUpdate = update(
                ExamSessionRepository.class,
                "abandonIfInProgress",
                String.class
        );
        Update completeUpdate = update(
                ExamSessionRepository.class,
                "completeIfIncomplete",
                String.class,
                LocalDateTime.class
        );

        assertAll(
                () -> assertTrue(candidates.value().contains("'status': 'IN_PROGRESS'")),
                () -> assertEquals("{ 'createdAt': -1, '_id': -1 }", candidates.sort()),
                () -> assertTrue(abandon.value().contains("'status': 'IN_PROGRESS'")),
                () -> assertTrue(abandon.value().contains("'completedAt'")),
                () -> assertTrue(abandonUpdate.value().contains("'status': 'ABANDONED'")),
                () -> assertTrue(abandonUpdate.value().contains("'active': false")),
                () -> assertTrue(completeUpdate.value().contains("'status': 'COMPLETED'")),
                () -> assertTrue(completeUpdate.value().contains("'active': false"))
        );
    }

    private static Query query(Class<?> repository, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = repository.getMethod(name, parameterTypes);
        return method.getAnnotation(Query.class);
    }

    private static Update update(Class<?> repository, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = repository.getMethod(name, parameterTypes);
        return method.getAnnotation(Update.class);
    }
}
