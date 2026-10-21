package web.tosunsaeng.domain.exams;

import com.mongodb.client.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import web.tosunsaeng.domain.exams.domain.entity.QuestionGradingJob;
import web.tosunsaeng.domain.exams.reanswer.ReanswerApproval;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class ReanswerApprovalMongoTest {
    @Container static final MongoDBContainer MONGO=new MongoDBContainer("mongo:7.0.14");
    @Test void proofStringsRoundTripAndRollbackWithJob() {
        try (var client=MongoClients.create(MONGO.getReplicaSetUrl())) {
            var factory=new SimpleMongoClientDatabaseFactory(client,"isolated-reanswer-proof");
            var mongo=new MongoTemplate(factory);
            mongo.createCollection(QuestionGradingJob.class);
            var tx=new TransactionTemplate(new MongoTransactionManager(factory));
            var checked="2026-10-21T08:00:00.123456789Z";
            var until="2026-10-21T08:00:05.123456789Z";
            var proof=new ReanswerApproval(ReanswerApproval.TYPE,ReanswerApproval.REVISION,
                    "11111111-1111-4111-8111-111111111111",checked,until);
            var job=QuestionGradingJob.pending("question:exam:1:1","exam",1,1,"key","mock",Instant.parse(checked))
                    .markUserSubmission(Instant.parse(checked));
            job.approveReanswer(proof);
            assertThrows(IllegalStateException.class,()->tx.execute(status->{mongo.insert(job); throw new IllegalStateException("rollback");}));
            assertNull(mongo.findById(job.getJobId(),QuestionGradingJob.class));
            tx.execute(status->mongo.insert(job));
            var read=mongo.findById(job.getJobId(),QuestionGradingJob.class);
            assertNotNull(read);
            assertEquals(proof,read.getReanswerApproval());
            assertTrue(read.getReanswerApproval().valid());
            var raw=mongo.getCollection("question_grading_jobs").find(new Document("_id",job.getJobId())).first();
            assertEquals(checked,raw.get("reanswerApproval",Document.class).getString("checkedAt"));
            assertEquals(until,raw.get("reanswerApproval",Document.class).getString("validUntil"));
            assertInstanceOf(java.util.Date.class,raw.get("firstUserSubmittedAt"));
        }
    }
}
