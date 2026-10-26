package web.tosunsaeng.domain.answerlearning;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static web.tosunsaeng.domain.answerlearning.LearningFixtures.*;
import static web.tosunsaeng.domain.answerlearning.LearningModels.*;

class LearningRecentResumeTest {
    MongoTemplate mongo=mock(MongoTemplate.class);
    LearningStorage storage=mock(LearningStorage.class);
    LearningPaidAccess access=mock(LearningPaidAccess.class);
    LearningCatalog catalog=new LearningCatalog(mongo,config(),storage,access);
    List<Document> units=List.of(unit("p1",1,"c1","c2"),unit("a",3,"q1","q2"),unit("b",3,"q3"),unit("p5",5,"z1","z2"));
    List<Document> lessons=List.of(lesson("c1","p1",1,1),lesson("c2","p1",1,2),lesson("q1","a",3,5),
            lesson("q2","a",3,6),lesson("q3","b",3,5),lesson("z1","p5",5,11),lesson("z2","p5",5,12));
    static Document unit(String id,int part,String... ids) { return new Document("_id",id).append("part",part).append("lessonIds",List.of(ids)); }
    static Document lesson(String id,String unit,int part,int number) { return new Document("_id",id).append("unitId",unit).append("part",part).append("questionNumber",number); }
    @BeforeEach void setup() {
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_units"))).thenReturn(units);
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_lessons"))).thenReturn(lessons);
    }
    void submitted(String lesson,String status) {
        Submission s=submission(); s.lessonId=lesson; s.status=status; s.queuedAt=NOW; s.updatedAt=NOW.plusSeconds(100);
        when(mongo.findOne(any(Query.class),eq(Submission.class))).thenReturn(s);
    }
    @Test void noHistoryDoesNotChooseAnArbitraryPart() {
        assertThat(catalog.resumeRecent(OWNER,"IH")).containsEntry("status","NO_HISTORY").containsEntry("part",null).containsEntry("lessonId",null);
        verifyNoInteractions(storage,access);
    }
    @Test void nextLessonInSameSetAndNextSetFirstLesson() {
        submitted("q1","queued");
        assertThat(catalog.resumeRecent(OWNER,"IH")).containsEntry("status","READY").containsEntry("part",3)
                .containsEntry("unitId","a").containsEntry("unitNumber",1).containsEntry("lessonId","q2")
                .containsEntry("lessonNumber",2).containsEntry("questionNumber",6);
        submitted("q2","failed");
        assertThat(catalog.resumeRecent(OWNER,"IH")).containsEntry("unitId","b").containsEntry("unitNumber",2)
                .containsEntry("lessonId","q3").containsEntry("lessonNumber",1);
        verifyNoInteractions(storage,access);
    }
    @Test void latestPartWinsAndEndDoesNotSwitchPartOrWrap() {
        submitted("z1","grading");
        assertThat(catalog.resumeRecent(OWNER,"IH")).containsEntry("part",5).containsEntry("lessonId","z2");
        submitted("q3","completed");
        assertThat(catalog.resumeRecent(OWNER,"IH")).containsEntry("status","END_OF_PART").containsEntry("lessonId",null);
        submitted("z2","no_speech");
        assertThat(catalog.resumeRecent(OWNER,"IH")).containsEntry("status","END_OF_PART");
    }
    @Test void recentCommonPartOneCompletionCompetesWithRequestedLevelOnly() {
        submitted("z1","completed");
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_completions"))).thenReturn(List.of(
                new Document("lessonId","c1").append("completedAt",Date.from(NOW.minusSeconds(100)))
                        .append("lastPracticedAt",Date.from(NOW.plusSeconds(1)))));
        assertThat(catalog.resumeRecent(OWNER,"AL")).containsEntry("level","AL").containsEntry("part",1).containsEntry("lessonId","c2");
    }
    @Test void existingPartOneEvidenceAndEqualTimeHaveDeterministicOrdering() {
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_completions"))).thenReturn(List.of(
                new Document("lessonId","c2").append("completedAt",Date.from(NOW)),
                new Document("lessonId","c1").append("completedAt",Date.from(NOW))));
        assertThat(catalog.resumeRecent(OWNER,"IH")).containsEntry("status","END_OF_PART");
    }
    @Test void queryUsesOwnerLevelVersionCurrentLessonsAndFirstQueueTimeNotWorkerTime() {
        catalog.resumeRecent(OWNER,"IH");
        var query=org.mockito.ArgumentCaptor.forClass(Query.class);
        verify(mongo).findOne(query.capture(),eq(Submission.class));
        assertThat(query.getValue().getQueryObject()).containsEntry("userId",OWNER).containsEntry("level","IH")
                .containsEntry("contentVersion","test-v1").containsEntry("queuedAt",new Document("$ne",null));
        assertThat(query.getValue().getQueryObject().get("lessonId",Document.class).getList("$in",String.class))
                .containsExactly("q1","q2","q3","z1","z2");
        assertThat(query.getValue().getSortObject()).isEqualTo(new Document("queuedAt",-1).append("_id",-1));
        verify(mongo).find(query.capture(),eq(Document.class),eq("learning_completions"));
        assertThat(query.getValue().getQueryObject()).containsEntry("userId",OWNER).containsEntry("level","common");
    }
    @Test void malformedOrMissingCatalogIsNotNoHistory() {
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_lessons"))).thenReturn(List.of());
        assertThatThrownBy(()->catalog.resumeRecent(OWNER,"IH")).hasMessage("LEARNING_UNAVAILABLE");
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_units"))).thenReturn(List.of());
        assertThatThrownBy(()->catalog.resumeRecent(OWNER,"IH")).hasMessage("LEARNING_NOT_FOUND");
    }
    @Test void duplicateLessonAndInvalidLevelAreRejected() {
        for (String level:List.of("common","IM","","ih")) assertThatThrownBy(()->catalog.resumeRecent(OWNER,level)).isInstanceOf(LearningFailure.class);
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_units"))).thenReturn(List.of(unit("a",3,"q1","q1")));
        assertThatThrownBy(()->catalog.resumeRecent(OWNER,"IH")).hasMessage("LEARNING_UNAVAILABLE");
    }
}
