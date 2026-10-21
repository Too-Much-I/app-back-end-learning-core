package web.tosunsaeng.domain.answerlearning;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Explicit wire projection: collection metadata/checklists/storage paths never leak to the app. */
public class LearningCatalog {
    private final MongoTemplate mongo;
    private final LearningProperties config;
    private final LearningStorage storage;
    public LearningCatalog(MongoTemplate mongo, LearningProperties config, LearningStorage storage) {
        this.mongo = mongo; this.config = config; this.storage = storage;
    }
    private Criteria current() { return Criteria.where("datasetId").is(config.getDatasetId()).and("contentVersion").is(config.getContentVersion()); }
    private Document require(String collection, String id) {
        Document doc = mongo.findOne(Query.query(current().and("_id").is(id)), Document.class, collection);
        if (doc == null) throw LearningFailure.missing();
        return doc;
    }
    public Document lesson(String id) {
        Document lesson = require("learning_lessons", id);
        unit(lesson.getString("unitId"));
        return lesson;
    }
    private Document unit(String id) {
        Document unit = require("learning_units", id);
        if (!"ready".equals(unit.getString("publicationStatus"))) throw LearningFailure.missing();
        return unit;
    }
    public static String level(int part, String level) {
        if (part < 1 || part > 5) throw LearningFailure.bad();
        if (!Set.of("common", "IM3", "IH", "AL").contains(level == null ? "" : level)) throw LearningFailure.bad();
        if (part == 1) return "common";
        if ("common".equals(level)) throw LearningFailure.bad();
        return level;
    }
    public void version(String version) {
        if (!config.getContentVersion().equals(version)) throw new LearningFailure(409, "CONTENT_VERSION_CHANGED");
    }
    public Document list(String owner, int part, String requestedLevel) {
        String level = level(part, requestedLevel);
        List<Document> units = mongo.find(Query.query(current().and("part").is(part).and("publicationStatus").is("ready"))
                .with(Sort.by("sortOrder", "_id")).limit(101), Document.class, "learning_units");
        if (units.size() > 100) throw LearningFailure.unavailable();
        List<String> ids = units.stream().flatMap(u -> u.getList("lessonIds", String.class).stream()).toList();
        Map<String, Document> lessons = lessonMap(ids);
        Set<String> done = completed(owner, level, ids);
        Map<String, Document> media = mediaMap(units.stream().map(u -> u.getString("thumbnailMediaId")).filter(Objects::nonNull).toList());
        return new Document("part", part).append("level", level).append("units", units.stream()
                .map(u -> summary(u, lessons, done, media)).toList());
    }
    private Map<String, Document> lessonMap(List<String> ids) {
        return mongo.find(Query.query(current().and("_id").in(ids)), Document.class, "learning_lessons").stream()
                .collect(Collectors.toMap(d -> d.getString("_id"), Function.identity()));
    }
    private Set<String> completed(String owner, String level, List<String> ids) {
        return mongo.find(Query.query(Criteria.where("userId").is(owner).and("level").is(level)
                .and("contentVersion").is(config.getContentVersion()).and("lessonId").in(ids)), Document.class, "learning_completions")
                .stream().map(d -> d.getString("lessonId")).collect(Collectors.toSet());
    }
    private Document summary(Document u, Map<String, Document> lessons, Set<String> done, Map<String, Document> media) {
        List<Document> refs = u.getList("lessonIds", String.class).stream().map(id -> {
            Document l = lessons.get(id);
            if (l == null || !u.getString("_id").equals(l.getString("unitId"))) throw LearningFailure.unavailable();
            return new Document("id", id).append("questionNumber", l.getInteger("questionNumber")).append("completed", done.contains(id));
        }).toList();
        return new Document("id", u.getString("_id")).append("part", u.getInteger("part")).append("title", u.getString("title"))
                .append("thumbnail", resolve(u.getString("thumbnailMediaId"), media)).append("lessons", refs);
    }
    public Document detail(String owner, String id, String requestedLevel) {
        Document u = unit(id);
        String level = level(u.getInteger("part"), requestedLevel);
        List<String> ids = u.getList("lessonIds", String.class);
        Map<String, Document> lessons = lessonMap(ids);
        Map<String, Document> answers = mongo.find(Query.query(current().and("lessonId").in(ids).and("level").is(level)),
                Document.class, "learning_answers").stream().collect(Collectors.toMap(d -> d.getString("lessonId"), Function.identity()));
        Set<String> mediaIds = new HashSet<>(); mediaIds.add(u.getString("thumbnailMediaId"));
        for (Document l : lessons.values()) mediaIds.add(l.getString("photoMediaId"));
        for (Document a : answers.values()) {
            validateWords(a);
            mediaIds.add(a.getString("fullAudioMediaId"));
            a.getList("sentences", Document.class).forEach(s -> mediaIds.add(s.getString("audioMediaId")));
        }
        mediaIds.remove(null);
        Map<String, Document> media = mediaMap(mediaIds);
        List<Document> result = new ArrayList<>();
        for (String lid : ids) {
            Document l = lessons.get(lid), a = answers.get(lid);
            if (l == null || a == null) throw LearningFailure.unavailable();
            Document wire = pick(l, "contentVersion", "part", "questionNumber", "question", "questionTranslation", "passageTranslation",
                    "answerSeconds", "passage", "contextIntro", "table", "tableFocus");
            wire.put("id", lid); wire.put("level", level); wire.put("photo", resolve(l.getString("photoMediaId"), media));
            Document answer = pick(a, "example", "durationSeconds");
            answer.put("id", a.getString("sourceAnswerId"));
            if (a.getString("fullAudioMediaId") != null) answer.put("fullAudio", resolve(a.getString("fullAudioMediaId"), media));
            answer.put("sentences", a.getList("sentences", Document.class).stream().map(s -> {
                Document sentence = pick(s, "text", "translation", "label", "words");
                sentence.put("audio", resolve(s.getString("audioMediaId"), media)); return sentence;
            }).toList());
            if (answer.get("durationSeconds") == null) answer.remove("durationSeconds");
            wire.put("answer", answer); result.add(wire);
        }
        return new Document("unit", summary(u, lessons, completed(owner, level, ids), media)).append("lessons", result);
    }
    /** Validate without normalizing or re-tokenizing: clients reference the original word positions. */
    static void validateWords(Document answer) {
        if (!(answer.get("sentences") instanceof List<?> sentences) || sentences.isEmpty())
            throw LearningFailure.unavailable();
        for (Object value : sentences) {
            if (!(value instanceof Document sentence)
                    || !(sentence.get("text") instanceof String text) || text.isBlank()
                    || !(sentence.get("words") instanceof List<?> words) || words.isEmpty())
                throw LearningFailure.unavailable();
            List<String> texts = new ArrayList<>();
            for (Object entry : words) {
                if (!(entry instanceof Document word)
                        || !(word.get("text") instanceof String token) || token.isBlank())
                    throw LearningFailure.unavailable();
                texts.add(token);
            }
            if (!String.join(" ", texts).equals(text)) throw LearningFailure.unavailable();
        }
    }
    private Map<String, Document> mediaMap(Collection<String> ids) {
        return mongo.find(Query.query(current().and("_id").in(ids).and("accessScope").is("authenticated-learning")), Document.class, "learning_media")
                .stream().collect(Collectors.toMap(d -> d.getString("_id"), Function.identity()));
    }
    private Document resolve(String id, Map<String, Document> media) {
        if (id == null) return null;
        Document row = media.get(id);
        if (row == null) throw LearningFailure.unavailable();
        return storage.media(row);
    }
    public Document media(String id) {
        Map<String, Document> rows = mediaMap(List.of(id));
        if (!rows.containsKey(id)) throw LearningFailure.missing();
        return resolve(id, rows);
    }
    public void validateDataset() {
        if (!mongo.exists(Query.query(current().and("status").is("ready")), "learning_datasets"))
            throw new IllegalStateException("The configured learning dataset is not ready");
    }
    public Document checklist(Document lesson) {
        List<Document> rows = mongo.find(Query.query(current().and("lessonId").is(lesson.getString("_id")).and("status").is("prepared"))
                .limit(2), Document.class, "learning_checklists");
        if (rows.isEmpty()) return null;
        if (rows.size() != 1) throw LearningFailure.unavailable();
        Document c = rows.getFirst();
        if (!Objects.equals(c.get("part"), lesson.get("part")) || !Objects.equals(c.get("question"), lesson.get("question")))
            throw LearningFailure.unavailable();
        return new Document("lesson_id", c.getString("lessonId")).append("version", c.getString("checklistVersion"))
                .append("part", c.getInteger("part")).append("question", c.getString("question"))
                .append("answer_seconds", c.getInteger("answerSeconds")).append("source", c.getString("source"))
                .append("met_threshold", c.getOrDefault("metThreshold", .75)).append("not_met_threshold", c.getOrDefault("notMetThreshold", .25))
                .append("items", c.get("items"));
    }
    private static Document pick(Document from, String... keys) {
        Document to = new Document();
        for (String key : keys) if (from.containsKey(key)) to.put(key, from.get(key));
        return to;
    }
}
