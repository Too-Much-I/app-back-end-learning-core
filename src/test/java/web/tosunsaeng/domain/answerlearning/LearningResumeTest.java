package web.tosunsaeng.domain.answerlearning;

import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static web.tosunsaeng.domain.answerlearning.LearningFixtures.*;

class LearningResumeTest {
    MongoTemplate mongo=mock(MongoTemplate.class);
    LearningStorage storage=mock(LearningStorage.class);
    LearningPaidAccess access=mock(LearningPaidAccess.class);
    LearningCatalog catalog=new LearningCatalog(mongo,config(),storage,access);
    @BeforeEach void setup() {
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_units"))).thenReturn(List.of(
                new Document("_id","u1").append("lessonIds",List.of("q1","q2")),
                new Document("_id","u2").append("lessonIds",List.of("q3"))));
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_lessons"))).thenReturn(List.of(
                new Document("_id","q1").append("unitId","u1"),new Document("_id","q2").append("unitId","u1"),
                new Document("_id","q3").append("unitId","u2")));
    }
    void done(String... ids) {
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_completions")))
                .thenReturn(Arrays.stream(ids).map(id -> new Document("lessonId",id)).toList());
    }
    @Test void firstPartialNextAndAllCompleted() {
        done(); assertThat(catalog.resume(OWNER,3,"IH")).containsEntry("unitId","u1").containsEntry("allCompleted",false);
        done("q1"); assertThat(catalog.resume(OWNER,3,"IH")).containsEntry("unitId","u1");
        done("q1","q2"); assertThat(catalog.resume(OWNER,3,"IH")).containsEntry("unitId","u2");
        done("q1","q2","q3"); assertThat(catalog.resume(OWNER,3,"IH")).containsEntry("unitId",null).containsEntry("allCompleted",true);
        verifyNoInteractions(storage,access);
    }
    @Test void queryScopesOwnerLevelVersionAndOrdering() {
        done(); catalog.resume(OWNER,1,"common");
        var queries=org.mockito.ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(queries.capture(),eq(Document.class),eq("learning_completions"));
        assertThat(queries.getValue().getQueryObject()).containsEntry("userId",OWNER).containsEntry("level","common").containsEntry("contentVersion","test-v1");
        verify(mongo).find(queries.capture(),eq(Document.class),eq("learning_units"));
        assertThat(queries.getValue().getSortObject()).isEqualTo(new Document("sortOrder",1).append("_id",1));
    }
    @Test void noContentAndMissingLessonAreNotCompletion() {
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_lessons"))).thenReturn(List.of());
        assertThatThrownBy(() -> catalog.resume(OWNER,3,"IH")).isInstanceOf(LearningFailure.class);
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_units"))).thenReturn(List.of());
        assertThatThrownBy(() -> catalog.resume(OWNER,3,"IH")).isInstanceOfSatisfying(LearningFailure.class,f -> assertThat(f.status).isEqualTo(404));
    }
    @Test void invalidPartLevelAndEmptySetAreRejected() {
        assertThatThrownBy(() -> catalog.resume(OWNER,6,"IH")).isInstanceOf(LearningFailure.class);
        assertThatThrownBy(() -> catalog.resume(OWNER,3,"common")).isInstanceOf(LearningFailure.class);
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_units"))).thenReturn(List.of(new Document("_id","u").append("lessonIds",List.of())));
        assertThatThrownBy(() -> catalog.resume(OWNER,3,"IH")).isInstanceOf(LearningFailure.class);
    }
}
