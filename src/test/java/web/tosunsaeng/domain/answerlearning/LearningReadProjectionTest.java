package web.tosunsaeng.domain.answerlearning;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import java.util.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static web.tosunsaeng.domain.answerlearning.LearningFixtures.*;

class LearningReadProjectionTest {
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final LearningStorage storage = mock(LearningStorage.class);
    private final LearningCatalog catalog = new LearningCatalog(mongo, config(), storage);

    // Emulate the DB inclusion projection, so accidentally omitted fields affect the response.
    private void projected(String collection, List<Document> documents, String... fields) {
        doAnswer(call -> {
            Query query = call.getArgument(0);
            Document projection = query.getFieldsObject();
            Set<String> included = new HashSet<>();
            projection.forEach((key, value) -> { if (Integer.valueOf(1).equals(value)) included.add(key); });
            assertThat(included).containsExactlyInAnyOrder(fields);
            return documents.stream().map(source -> {
                Document result = new Document();
                for (String field : included) if (source.containsKey(field)) result.put(field, source.get(field));
                return result;
            }).toList();
        }).when(mongo).find(any(Query.class), eq(Document.class), eq(collection));
    }

    private Document lesson(String id, String unit, int part, int number) {
        return new Document("_id", id).append("unitId", unit).append("part", part).append("questionNumber", number)
                .append("question", "large question".repeat(1000)).append("passage", "large passage".repeat(1000))
                .append("table", new Document("rows", "large table".repeat(1000)))
                .append("tableFocus", new Document("rows", "large table".repeat(1000)));
    }

    @Test void partFourListAndLegacyResumeReadOnlyMetadataFor120Lessons() {
        List<Document> units = new ArrayList<>(), lessons = new ArrayList<>();
        for (int u = 0; u < 40; u++) {
            String unit = "unit" + u;
            List<String> ids = IntStream.range(0, 3).mapToObj(n -> unit + "-q" + n).toList();
            units.add(new Document("_id", unit).append("part", 4).append("title", "Title " + u)
                    .append("lessonIds", ids).append("privateMetadata", "unused"));
            for (int q = 0; q < 3; q++) lessons.add(lesson(ids.get(q), unit, 4, q + 8));
        }
        Collections.reverse(lessons); // Mongo $in result order must not control display order.
        projected("learning_units", units, "_id", "part", "title", "thumbnailMediaId", "lessonIds");
        projected("learning_lessons", lessons, "_id", "unitId", "questionNumber");
        projected("learning_completions", List.of(new Document("lessonId", "unit0-q0")), "lessonId");
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("learning_datasets")))
                .thenReturn(new Document("status", "ready"));

        List<Document> listed = catalog.list(OWNER, 4, "IH").getList("units", Document.class);
        assertThat(listed).hasSize(40);
        assertThat(listed.stream().flatMap(u -> u.getList("lessons", Document.class).stream())).hasSize(120);
        assertThat(listed.getFirst().getList("lessons", Document.class)).containsExactly(
                new Document("id", "unit0-q0").append("questionNumber", 8).append("completed", true),
                new Document("id", "unit0-q1").append("questionNumber", 9).append("completed", false),
                new Document("id", "unit0-q2").append("questionNumber", 10).append("completed", false));
        assertThat(listed.getLast()).containsEntry("id", "unit39");

        projected("learning_units", units, "_id", "lessonIds");
        assertThat(catalog.resume(OWNER, 4, "IH"))
                .isEqualTo(new Document("unitId", "unit0").append("allCompleted", false));
    }

    @Test void recentResumeReadsOnlyMetadataAcross360LessonsAndNoResultPayload() {
        List<Document> units = new ArrayList<>(), lessons = new ArrayList<>();
        for (int part = 1; part <= 5; part++) {
            String unit = "p" + part;
            List<String> ids = IntStream.range(0, 72).mapToObj(n -> unit + "-q" + n).toList();
            units.add(new Document("_id", unit).append("part", part).append("lessonIds", ids));
            for (int q = 0; q < 72; q++) lessons.add(lesson(ids.get(q), unit, part, q + 1));
        }
        Collections.reverse(lessons);
        projected("learning_units", units, "_id", "part", "lessonIds");
        projected("learning_lessons", lessons, "_id", "unitId", "part", "questionNumber");
        projected("learning_completions", List.of(), "lessonId", "lastPracticedAt", "completedAt");
        when(mongo.findOne(any(Query.class), eq(LearningModels.Submission.class))).thenAnswer(call -> {
            Query query = call.getArgument(0);
            assertThat(query.getFieldsObject()).isEqualTo(new Document("lessonId", 1).append("updatedAt", 1).append("_id", 0));
            assertThat(query.getQueryObject()).containsEntry("status", "completed")
                    .containsEntry("result", new Document("$ne", null)).containsEntry("userId", OWNER);
            assertThat(query.getSortObject()).isEqualTo(new Document("updatedAt", -1).append("_id", -1));
            var result = new LearningModels.Submission(); result.lessonId = "p4-q71"; result.updatedAt = NOW;
            return result;
        });
        assertThat(catalog.resumeRecent(OWNER, "IH")).isEqualTo(new Document("status", "READY").append("level", "IH")
                .append("part", 5).append("unitId", "p5").append("unitNumber", 1)
                .append("lessonId", "p5-q0").append("lessonNumber", 1).append("questionNumber", 1));
        verifyNoInteractions(storage);
    }
}
