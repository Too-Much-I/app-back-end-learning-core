package web.tosunsaeng.domain.learningrecorddeletion;

import com.mongodb.client.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.mapping.callback.EntityCallbacks;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import web.tosunsaeng.domain.exams.domain.entity.*;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.repository.*;
import web.tosunsaeng.domain.learningrecorddeletion.analytics.*;
import web.tosunsaeng.domain.learningrecorddeletion.application.DeletionTransactions;
import web.tosunsaeng.domain.learningrecorddeletion.infrastructure.DeletionPersistenceFence;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class LearningActivityMongoTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0.14");
    static final Instant NOW = Instant.parse("2026-10-03T15:01:00Z"); // KST October 4
    MongoClient client; MongoTemplate mongo; DeletionTransactions tx; LearningActivityRecorder recorder;
    QuestionGradingJobRepository questions; ExamSessionRepository sessions;
    DeletionPersistenceFence fence;
    @BeforeEach void setup() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        mongo = new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, "learning-daily")); mongo.getDb().drop();
        for (String c : LearningActivityRecorder.SOURCES) mongo.createCollection(c);
        mongo.createCollection(LearningActivityRecorder.COLLECTION);
        tx = new DeletionTransactions(mongo.getMongoDatabaseFactory());
        recorder = new LearningActivityRecorder(mongo, Clock.fixed(NOW, ZoneOffset.UTC));
        mongo.setEntityCallbacks(EntityCallbacks.create(new LearningActivityMongoCallback(recorder)));
        var beans = new StaticListableBeanFactory(); beans.addBean("recorder", recorder); beans.addBean("mongo", mongo);
        fence = mock(DeletionPersistenceFence.class);
        beans.addBean("fence", fence);
        var hooks = new LearningActivityRepositoryHooks(beans.getBeanProvider(LearningActivityRecorder.class),
                beans.getBeanProvider(DeletionPersistenceFence.class), beans.getBeanProvider(MongoTemplate.class));
        var factory = new MongoRepositoryFactory(mongo);
        questions = (QuestionGradingJobRepository) hooks.postProcessAfterInitialization(factory.getRepository(QuestionGradingJobRepository.class,
                org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments.just(
                        new QuestionGradingJobRecoveryImpl(mongo))), "questions");
        sessions = (ExamSessionRepository) hooks.postProcessAfterInitialization(factory.getRepository(ExamSessionRepository.class), "sessions");
    }
    @AfterEach void close() { client.close(); }

    @Test void completionReopenAndDuplicateKeepFirstMarkersAndCountOnlyOneRetryDecision() {
        tx.run(() -> questions.insert(QuestionGradingJob.pending("job", "exam", 1, 0, "fixture", NOW).markUserSubmission()));
        tx.run(() -> { var job = questions.findById("job").orElseThrow(); job.complete(NOW); return questions.save(job); });
        assertThat(tx.run(() -> questions.reopenCompletedMissingResult("job", 0, NOW))).isEqualTo(1);
        assertThat(questions.findById("job").orElseThrow().effectiveRecoveryCycle()).isEqualTo(1);
        assertThat(tx.run(() -> questions.reopenCompletedMissingResult("job", 0, NOW))).isZero();
        tx.run(() -> {
            var job = questions.findById("job").orElseThrow(); job.startProcessing(NOW); questions.save(job);
            job.complete(NOW); return questions.save(job);
        });
        assertThat(count("question_submitted_total")).isEqualTo(1);
        assertThat(count("question_grading_completed_total")).isEqualTo(1);
        assertThat(count("question_grading_retry_total")).isEqualTo(1);
        var source = recorder.source("question_grading_jobs", "job");
        assertThat(source.get(LearningActivityRecorder.MARKERS, Document.class).get("retrySequence")).isEqualTo(1L);
    }

    @Test void rawSessionCasCountsOnceAndCasLoserDoesNotCount() {
        tx.run(() -> sessions.insert(ExamSession.builder().examId("exam").userId("fixture").active(true)
                .status(ExamSessionStatus.IN_PROGRESS).build()));
        tx.run(() -> sessions.completeIfIncomplete("exam", LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)));
        tx.run(() -> sessions.completeIfIncomplete("exam", LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)));
        assertThat(count("exam_started_total")).isEqualTo(1);
        assertThat(count("exam_completed_total")).isEqualTo(1);
    }

    @Test void recoveryStillChecksDeletionFenceAndRollsBackItsStatistics() {
        tx.run(() -> questions.insert(QuestionGradingJob.pending("job", "exam", 1, 0, "fixture", NOW).markUserSubmission()));
        tx.run(() -> { var job = questions.findById("job").orElseThrow(); job.complete(NOW); return questions.save(job); });
        doThrow(new IllegalStateException("sealed fixture")).when(fence).check(eq("question_grading_jobs"), any());
        assertThatThrownBy(() -> tx.run(() -> questions.reopenCompletedMissingResult("job", 0, NOW)))
                .isInstanceOf(IllegalStateException.class).hasMessage("sealed fixture");
        assertThat(questions.findById("job").orElseThrow().effectiveRecoveryCycle()).isZero();
        assertThat(count("question_grading_retry_total")).isZero();
        reset(fence);
        assertThatThrownBy(() -> tx.run(() -> {
            assertThat(questions.reopenCompletedMissingResult("job", 0, NOW)).isEqualTo(1);
            throw new IllegalStateException("rollback fixture");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(questions.findById("job").orElseThrow().effectiveRecoveryCycle()).isZero();
        assertThat(count("question_grading_retry_total")).isZero();
        assertThat(tx.run(() -> questions.reopenCompletedMissingResult("job", 0, NOW))).isEqualTo(1);
        assertThat(count("question_grading_retry_total")).isEqualTo(1);
    }


    @Test void rollbackDoesNotLeaveCountsOrSourceAndOutsideTransactionIsRefused() {
        assertThatThrownBy(() -> tx.run(() -> {
            questions.insert(QuestionGradingJob.pending("job", "exam", 1, 0, "fixture", NOW));
            throw new IllegalStateException("fixture rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(mongo.count(new Query(), LearningActivityRecorder.COLLECTION)).isZero();
        assertThat(questions.findById("job")).isEmpty();
        assertThatThrownBy(() -> questions.insert(QuestionGradingJob.pending("unsafe", "exam", 1, 0, "fixture", NOW)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void legacyReopenDoesNotInventHistoricalFirstEventAndSourceDeletionKeepsOnlyAnonymousCounts() {
        mongo.getCollection("question_grading_jobs").insertOne(new Document("_id", "legacy").append("examId", "exam")
                .append("status", "COMPLETED").append("recoveryCycle", 0).append("dispatchAttempt", 0).append("version", 0L));
        tx.run(() -> questions.reopenCompletedMissingResult("legacy", 0, NOW));
        tx.run(() -> { var job = questions.findById("legacy").orElseThrow(); job.complete(NOW); return questions.save(job); });
        assertThat(count("question_grading_completed_total")).isZero();
        tx.run(() -> questions.insert(QuestionGradingJob.pending("new-job", "exam", 1, 0, "fixture", NOW).markUserSubmission()));
        mongo.remove(new Query(), "question_grading_jobs");
        assertThat(count("question_submitted_total")).isEqualTo(1);
        var aggregate = mongo.findOne(new Query(), Document.class, LearningActivityRecorder.COLLECTION);
        assertThat(aggregate.keySet()).containsExactlyInAnyOrder("_id", "bucketDate", "metric", "examType", "outcome", "count", "createdAt", "updatedAt", "version");
        assertThat(aggregate.getString("bucketDate")).isEqualTo("2026-10-04");
        assertThat(Arrays.stream(LearningActivityMetric.values()).mapToInt(m -> m.outcomes.size()).sum()).isEqualTo(18);
    }

    @Test void elevenQuestionExamProducesTwentyFiveIncrements() {
        tx.run(() -> sessions.insert(ExamSession.builder().examId("exam").active(true).status(ExamSessionStatus.IN_PROGRESS).build()));
        for (int n = 1; n <= 11; n++) {
            int q = n;
            tx.run(() -> questions.insert(QuestionGradingJob.pending("job-" + q, "exam", q, 0, "fixture", NOW).markUserSubmission()));
            tx.run(() -> { var job = questions.findById("job-" + q).orElseThrow(); job.complete(NOW); return questions.save(job); });
        }
        tx.run(() -> mongo.insert(SummaryGradingJob.pending("summary", "exam", NOW)));
        tx.run(() -> {
            var before = recorder.source("summary_grading_jobs", "summary");
            mongo.updateFirst(Query.query(Criteria.where("_id").is("summary")), new Update().set("status", "COMPLETED"), "summary_grading_jobs");
            recorder.recordRawTransition("summary_grading_jobs", before, recorder.source("summary_grading_jobs", "summary"));
            return null;
        });
        tx.run(() -> sessions.completeIfIncomplete("exam", LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)));
        long total = mongo.findAll(Document.class, LearningActivityRecorder.COLLECTION).stream().mapToLong(d -> d.getLong("count")).sum();
        assertThat(total).isEqualTo(25);
    }

    long count(String metric) {
        return mongo.find(Query.query(Criteria.where("metric").is(metric)), Document.class, LearningActivityRecorder.COLLECTION)
                .stream().mapToLong(d -> d.getLong("count")).sum();
    }
}
