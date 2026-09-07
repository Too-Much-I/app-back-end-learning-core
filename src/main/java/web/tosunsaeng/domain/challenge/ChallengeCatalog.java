package web.tosunsaeng.domain.challenge;

import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static web.tosunsaeng.domain.challenge.ChallengeModels.Question;

public class ChallengeCatalog {
    static final String QUESTIONS = "challenge_10s_questions";
    static final String STATE = "challenge_10s_catalog_state";
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final MongoTemplate mongo;
    private final Clock clock;
    public ChallengeCatalog(MongoTemplate mongo, Clock clock) { this.mongo = mongo; this.clock = clock; }
    public LocalDate today() { return LocalDate.ofInstant(clock.instant(), KST); }
    public ChallengeViews.DateInfo dateInfo() {
        Instant now = clock.instant(); LocalDate date = LocalDate.ofInstant(now, KST);
        Instant end = date.plusDays(1).atStartOfDay(KST).toInstant();
        return new ChallengeViews.DateInfo(date.toString(), end, Math.max(0, Duration.between(now, end).toSeconds()));
    }
    public LocalDate baseDate() {
        Document state = mongo.findById("active:v1", Document.class, STATE);
        if (state == null || !"Asia/Seoul".equals(state.get("zoneId"))) throw ChallengeFailure.internal();
        return date(state.getString("contentBaseDate"));
    }
    public void initialize() {
        validateAll();
        Query query = Query.query(Criteria.where("_id").is("active:v1"));
        Update insert = new Update().setOnInsert("contentBaseDate", today().toString())
                .setOnInsert("zoneId", "Asia/Seoul").setOnInsert("initializedAt", clock.instant());
        try { mongo.findAndModify(query, insert, FindAndModifyOptions.options().upsert(true).returnNew(true), Document.class, STATE); }
        catch (DuplicateKeyException concurrentInitializer) { /* Outside a transaction; read the winning singleton. */ }
        if (baseDate().isAfter(today())) throw new IllegalStateException("Challenge base date is in the future");
    }
    public List<Question> questions(LocalDate date) {
        long day = ChronoUnit.DAYS.between(baseDate(), date) + 1;
        if (day < 1 || day > Integer.MAX_VALUE) throw missing();
        Document document = mongo.findOne(Query.query(Criteria.where("dayNumber").is((int) day)), Document.class, QUESTIONS);
        List<Question> questions = validate(document);
        List<String> ids = questions.stream().map(Question::questionId).toList();
        if (mongo.count(Query.query(Criteria.where("questions.questionId").in(ids)), QUESTIONS) != 1) throw missing();
        return questions;
    }
    void validateAll() {
        Set<String> ids = new HashSet<>(); Set<Integer> days = new HashSet<>();
        try (var stream = mongo.stream(new Query(), Document.class, QUESTIONS)) {
            stream.forEach(d -> {
                List<Question> questions = validate(d);
                if (!days.add(questions.getFirst().dayNumber())) throw missing();
                for (Question q : questions) if (!ids.add(q.questionId())) throw missing();
            });
        }
        if (!days.contains(1)) throw missing();
    }
    public static List<Question> validate(Document d) {
        if (d == null || !(d.get("dayNumber") instanceof Integer day) || day <= 0
                || !(d.get("questions") instanceof List<?> raw) || raw.size() != 3) throw missing();
        Set<Integer> numbers = new HashSet<>(); Set<String> ids = new HashSet<>(); List<Question> result = new ArrayList<>();
        for (Object value : raw) {
            if (!(value instanceof Document q) || !(q.get("questionNumber") instanceof Integer n) || n < 1 || n > 3
                    || !(q.get("difficulty") instanceof Integer difficulty) || !numbers.add(n)) throw missing();
            String id = nonblank(q.get("questionId")), ko = nonblank(q.get("korean")), answer = nonblank(q.get("referenceAnswer"));
            if (!ids.add(id)) throw missing();
            result.add(new Question(day, n, id, ko, answer, difficulty));
        }
        result.sort(Comparator.comparingInt(Question::questionNumber)); return result;
    }
    private static String nonblank(Object value) {
        if (!(value instanceof String s) || s.isBlank()) throw missing(); return s;
    }
    static ChallengeFailure missing() { return new ChallengeFailure(404, "CHALLENGE_CONTENT_NOT_FOUND"); }
    public static LocalDate date(String value) {
        try { if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw ChallengeFailure.badRequest(); return LocalDate.parse(value); }
        catch (DateTimeException e) { throw ChallengeFailure.badRequest(); }
    }
    public static String uuid(String value) {
        try {
            if (value == null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw ChallengeFailure.badRequest();
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException e) { throw ChallengeFailure.badRequest(); }
    }
    public void requireToday(String value) {
        LocalDate requested = date(value);
        ChallengeViews.DateInfo current = dateInfo();
        if (!requested.toString().equals(current.challengeDate())) throw new ChallengeFailure(409, "CHALLENGE_DATE_CHANGED", current);
    }
    public static void number(int q) { if (q < 1 || q > 3) throw ChallengeFailure.badRequest(); }
}
