package web.tosunsaeng.domain.answerlearning;

import org.bson.Document;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static web.tosunsaeng.domain.answerlearning.LearningFixtures.*;

class LearningCatalogTest {
    @ParameterizedTest @ValueSource(ints={1,2,3,4,5})
    void partsProjectOnlySelectedAnswerAndKeepSetOrdering(int part) {
        var mongo=mock(MongoTemplate.class); var storage=mock(LearningStorage.class);
        var catalog=new LearningCatalog(mongo,config(),storage);
        List<String> ids=part==3 || part==4 ? List.of("q1","q2","q3") : List.of("q1");
        Document unit=new Document("_id","unit").append("part",part).append("title","연습 제목").append("publicationStatus","ready")
                .append("lessonIds",ids).append("thumbnailMediaId",part==2 ? "photo" : null);
        when(mongo.findOne(any(Query.class),eq(Document.class),eq("learning_units"))).thenReturn(unit);
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_units"))).thenReturn(List.of(unit));
        List<Document> lessons=new ArrayList<>(), answers=new ArrayList<>();
        Document table=new Document("tableType","schedule").append("title","Test schedule").append("columns",List.of()).append("items",List.of())
                .append("metadata",List.of(new Document("key","fee").append("label","Fee").append("value","$10"))).append("subtitles",List.of()).append("notes",List.of());
        for (String id : ids) {
            lessons.add(new Document("_id",id).append("unitId","unit").append("part",part).append("questionNumber",ids.indexOf(id)+1)
                    .append("contentVersion","test-v1").append("question","Example question?").append("passage",part==1?"Example passage.":null)
                    .append("photoMediaId",part==2?"photo":null).append("table",part==4?table:null).append("tableFocus",part==4?table:null)
                    .append("sourceQuestionId","private-source").append("answerSeconds",30).append("contextIntro",null));
            answers.add(new Document("_id","internal-versioned-id").append("sourceAnswerId",id+"-answer").append("lessonId",id)
                    .append("example","Example sentence.").append("fullAudioMediaId",part==1?"audio":null).append("durationSeconds",null)
                    .append("sentences",List.of(new Document("text","Example sentence.").append("label","문장 1").append("audioMediaId","audio")
                            .append("words",List.of(new Document("text","Example").append("start",0).append("end",.4),new Document("text","sentence.").append("start",.5).append("end",1))))));
        }
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_lessons"))).thenReturn(lessons);
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_answers"))).thenReturn(answers);
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_completions"))).thenReturn(List.of(new Document("lessonId","q1")));
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_media"))).thenReturn(List.of(new Document("_id","audio"),new Document("_id","photo")));
        when(storage.media(any())).thenAnswer(i->new Document("id",((Document)i.getArgument(0)).getString("_id")).append("url","https://media.example.test/file").append("expiresAt","2099-01-01T00:00:00Z"));
        Document detail=catalog.detail(OWNER,"unit","AL");
        assertThat(detail.getList("lessons",Document.class)).hasSize(ids.size());
        Document lesson=detail.getList("lessons",Document.class).getFirst();
        assertThat(lesson).doesNotContainKeys("sourceQuestionId","userId","datasetId","photoMediaId");
        assertThat(lesson.getString("level")).isEqualTo(part==1?"common":"AL");
        Document answer=lesson.get("answer",Document.class);
        assertThat(answer).doesNotContainKeys("fullAudioMediaId","durationSeconds","_id").containsEntry("id","q1-answer");
        if (part==1) assertThat(answer).containsKey("fullAudio");
        if (part==4) assertThat(lesson.get("tableFocus")).isEqualTo(table);
        var queries=org.mockito.ArgumentCaptor.forClass(Query.class); verify(mongo).find(queries.capture(),eq(Document.class),eq("learning_answers"));
        assertThat(queries.getValue().getQueryObject()).containsEntry("level",part==1?"common":"AL").containsEntry("contentVersion","test-v1");
        Document list=catalog.list(OWNER,part,"AL");
        var summary=list.getList("units",Document.class).getFirst();
        assertThat(summary).containsOnlyKeys("id","part","title","thumbnail","lessons");
        assertThat(summary.getList("lessons",Document.class).getFirst().getBoolean("completed")).isTrue();
    }
}
