package web.tosunsaeng.domain.answerlearning;

import org.bson.Document;
import java.time.*;
import java.util.List;
import static web.tosunsaeng.domain.answerlearning.LearningModels.*;

final class LearningFixtures {
    static final Instant NOW=Instant.parse("2026-10-12T07:00:00Z");
    static final Clock CLOCK=Clock.fixed(NOW,ZoneOffset.UTC);
    static final String OWNER="00000000-0000-4000-8000-000000000001";
    static final String ID="00000000-0000-4000-8000-000000000002";
    static LearningProperties config() {
        var p=new LearningProperties(); p.setEnabled(true); p.setGradingEnabled(true); p.setDatasetId("test-dataset");
        p.setContentVersion("test-v1"); p.setBucket("test-learning-bucket"); p.setLambdaUrl("https://test.lambda-url.ap-northeast-2.on.aws/"); return p;
    }
    static Document lesson(int part) {
        return new Document("_id","lesson").append("unitId","unit").append("part",part).append("question","Example question?").append("answerSeconds",30);
    }
    static Submission submission() {
        Submission s=new Submission(); s.id=ID; s.userId=OWNER; s.lessonId="lesson"; s.level="IH"; s.contentVersion="test-v1";
        s.createdAt=NOW; s.queuedAt=NOW; s.updatedAt=NOW; s.leaseToken="claim";
        s.audio=new Audio("test-learning-bucket", LearningStorage.frozenKey(ID), "a".repeat(32), "b".repeat(64));
        s.checklist=new Document("lesson_id","lesson").append("version","check-v1").append("part",2)
                .append("question","Example question?").append("source","Synthetic test fixture")
                .append("answer_seconds",30).append("met_threshold",.75).append("not_met_threshold",.25)
                .append("items",List.of(new Document("id","detail").append("label","내용").append("requirement","Include the requested detail.")
                        .append("met_feedback","내용을 전달했어요.").append("missing_feedback","내용을 더해 주세요.")));
        return s;
    }
    static Document remote(Submission s, String status) {
        return new Document("id",s.id).append("jobId",s.id+":"+s.attempt).append("attempt",s.attempt).append("lessonId",s.lessonId)
                .append("level",s.level).append("contentVersion",s.contentVersion).append("status",status).append("result",null).append("error",null);
    }
    static Document completed(Submission s) {
        return remote(s,"completed").append("result",new Document("checklistVersion","check-v1").append("transcript","Synthetic test response.")
                .append("metCount",1).append("totalCount",1).append("models",new Document("private","internal"))
                .append("items",List.of(new Document("id","detail").append("label","내용").append("verdict","met").append("probability",.95).append("feedback","내용을 전달했어요."))));
    }
}
