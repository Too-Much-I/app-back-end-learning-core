package web.tosunsaeng.domain.challenge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import static org.assertj.core.api.Assertions.*;
import static web.tosunsaeng.domain.challenge.ChallengeContractTest.*;
import static web.tosunsaeng.domain.challenge.ChallengeModels.*;

class ChallengeDetailedFeedbackTest {
    static Map<String, Object> extendedLegacy() {
        var f = new LinkedHashMap<String, Object>();
        f.put("meaning", "의미 피드백"); f.put("grammar", "문법 피드백"); f.put("pronunciation", "발음 피드백");
        f.put("correctionItems", List.of(item()));
        return rich(f);
    }
    @Test void extendedLegacyItemsSurviveMongoRoundTripAndResponse() throws Exception {
        var expected = parse(extendedLegacy()).result();
        var context = new MongoMappingContext(); context.afterPropertiesSet();
        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context); converter.afterPropertiesSet();
        var stored = new Document(); converter.write(expected, stored);
        var a = attempt(); a.state = State.SUBMITTED; a.result = converter.read(Result.class, stored);
        assertThat(a.result).isEqualTo(expected);
        var view = ChallengeViews.detail(a).aiResult().feedback();
        assertThat(view.summary()).isEqualTo("의미 피드백\n문법 피드백\n발음 피드백");
        assertThat(view.correctedAnswer()).isEqualTo("She is wearing black shoes.");
        assertThat(new ObjectMapper().<com.fasterxml.jackson.databind.JsonNode>valueToTree(view.correctionItems()))
                .isEqualTo(new ObjectMapper().valueToTree(List.of(item())));
    }
    @Test void extendedLegacyEmptyItemsArePreservedAndRetriesAreStable() throws Exception {
        var n = extendedLegacy(); var first = parse(n).digest();
        n.put("callback_id", UUID.randomUUID().toString());
        assertThat(parse(new TreeMap<>(n)).digest()).isEqualTo(first);
        @SuppressWarnings("unchecked") var f = (Map<String, Object>) n.get("feedback");
        f.put("correctionItems", List.of());
        assertThat(parse(n).result().detailedFeedback().correctionItems()).isEmpty();
        assertThat(parse(n).digest()).isNotEqualTo(first);
        f.remove("correctionItems");
        assertThat(parse(n).result().detailedFeedback()).isNull();
    }
    @ParameterizedTest @ValueSource(strings = {"type", "original", "issue", "explanation", "suggested", "severity"})
    void extendedLegacyValidatesItemsAndIncludesEveryFieldInDigest(String field) throws Exception {
        var n = extendedLegacy(); var digest = parse(n).digest();
        @SuppressWarnings("unchecked") var f = (Map<String, Object>) n.get("feedback");
        var i = item(); i.remove(field); f.put("correctionItems", List.of(i));
        assertInvalid(n, 400);
        i.put(field, field.equals("severity") ? "low" : "Changed");
        assertThat(parse(n).digest()).isNotEqualTo(digest);
        f.put("correctionItems", null); assertInvalid(n, 400);
        f.put("correctionItems", Map.of()); assertInvalid(n, 400);
        f.put("correctionItems", Collections.nCopies(21, item())); assertInvalid(n, 413);
    }
    @Test void sharedAiFixtureIsAcceptedAndProjectedWithoutLosingItems() throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "docs/contracts/fixtures/ten-second-challenge-detailed-feedback.json"));
        var callback = ChallengeCallback.parse(bytes, "v1");
        var a = attempt(); a.state = State.SUBMITTED; a.result = callback.result();
        var expected = new ObjectMapper().readTree(bytes).path("feedback");
        com.fasterxml.jackson.databind.JsonNode actual = new ObjectMapper().valueToTree(ChallengeViews.detail(a).aiResult().feedback());
        assertThat(actual).isEqualTo(expected);
    }
    static Map<String, Object> item() {
        return new LinkedHashMap<>(Map.of("type", "GRAMMAR", "original", "wear", "issue", "진행형 필요",
                "explanation", "진행 중인 상태예요.", "suggested", "is wearing", "severity", "high"));
    }
    static Map<String, Object> feedback() {
        return new LinkedHashMap<>(Map.of("summary", "현재진행형을 사용해 주세요.",
                "correctedAnswer", "She is wearing black shoes.", "correctionItems", List.of(item())));
    }
    static Map<String, Object> rich(Map<String, Object> feedback) {
        var n = callback("completed");
        n.put("verdict", "needs_improvement");
        n.put("corrected_answer", "She is wearing black shoes.");
        n.put("feedback", feedback);
        return n;
    }
    @Test void forwardsActualAiFeedbackAndPreservesSnapshot() throws Exception {
        var a = attempt(); a.state = State.SUBMITTED; a.gradingStatus = "completed";
        a.result = parse(rich(feedback())).result();
        var json = new ObjectMapper().registerModule(new JavaTimeModule());
        var response = json.valueToTree(new ChallengeViews.Results(a.challengeDate, 1, ChallengeViews.detail(a)));
        var ai = response.path("question").path("aiResult");
        assertThat(ai.path("feedback")).isEqualTo(json.valueToTree(feedback()));
        assertThat(ai.path("feedback").has("meaning")).isFalse();
        assertThat(response.path("question").path("referenceAnswer").asText()).isEqualTo(a.question.referenceAnswer());
        assertThat(ai.has("referenceAnswer")).isFalse();
        assertThat(ai.has("correctedAnswer")).isFalse();
        assertThat(response.path("solvedQuestionCount").asInt()).isEqualTo(1);
    }
    @Test void legacyDigestMatchesPreMigrationBytesAndLegacyViewDoesNotInventCorrections() throws Exception {
        var c = parse(callback("completed"));
        // Exact pre-migration Result/Feedback property order including nulls.
        var result = new LinkedHashMap<String, Object>();
        result.put("transcript", c.result().transcript()); result.put("verdict", c.result().verdict());
        result.put("correctedAnswer", null);
        var f = new LinkedHashMap<String, Object>();
        f.put("meaning", "의미 피드백"); f.put("grammar", "문법 피드백"); f.put("pronunciation", "발음 피드백");
        result.put("feedback", f);
        byte[] previous = new ObjectMapper().writeValueAsBytes(Arrays.asList(
                "v1", c.attemptId(), c.jobId(), 1, "completed", result, null, false));
        assertThat(c.digest()).isEqualTo(ChallengeCallback.sha256(previous));
        var a = attempt(); a.state = State.SUBMITTED; a.result = c.result();
        var view = ChallengeViews.detail(a).aiResult().feedback();
        assertThat(view.summary()).isEqualTo("의미 피드백\n문법 피드백\n발음 피드백");
        assertThat(view.correctionItems()).isNull();
        assertThat(view.correctedAnswer()).isNull();
    }
    @Test void mongoReadsHistoricalDocumentsAndRoundTripsDetailedFeedbackWithoutExternalDb() throws Exception {
        var context = new MongoMappingContext(); context.afterPropertiesSet();
        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context); converter.afterPropertiesSet();
        var legacy = new Document("transcript", "Fixture").append("verdict", "correct")
                .append("correctedAnswer", null).append("feedback",
                        new Document("meaning", "의미").append("grammar", "문법").append("pronunciation", "발음"));
        var old = converter.read(Result.class, legacy);
        assertThat(old.detailedFeedback()).isNull(); assertThat(old.feedback().meaning()).isEqualTo("의미");
        var expected = parse(rich(feedback())).result(); var stored = new Document();
        converter.write(expected, stored);
        assertThat(converter.read(Result.class, stored)).isEqualTo(expected);
    }
    @Test void nullableCorrectAnswerAndEmptyCorrectionsAreAllowedForCorrectResult() throws Exception {
        var f = feedback(); f.put("correctedAnswer", null); f.put("correctionItems", List.of());
        var n = rich(f); n.put("verdict", "correct"); n.put("corrected_answer", null);
        assertThat(parse(n).result().detailedFeedback().correctionItems()).isEmpty();
        assertThat(parse(n).result().detailedFeedback().correctedAnswer()).isNull();
    }
    @ParameterizedTest @ValueSource(strings = {"summary", "correctedAnswer", "correctionItems"})
    void rejectsMissingFeedbackFields(String field) {
        var f = feedback(); f.remove(field);
        assertThatThrownBy(() -> parse(rich(f))).isInstanceOf(ChallengeFailure.class);
    }
    @ParameterizedTest @ValueSource(strings = {"type", "original", "issue", "explanation", "suggested", "severity"})
    void rejectsMissingItemFieldsAndIncludesEveryFieldInDigest(String field) throws Exception {
        var originalDigest = parse(rich(feedback())).digest();
        var i = item(); i.remove(field); var f = feedback(); f.put("correctionItems", List.of(i));
        assertThatThrownBy(() -> parse(rich(f))).isInstanceOf(ChallengeFailure.class);
        i.put(field, field.equals("severity") ? "low" : "Different");
        assertThat(parse(rich(f)).digest()).isNotEqualTo(originalDigest);
    }
    @Test void richRetriesIgnoreCallbackIdOrderAndUnknownExtensions() throws Exception {
        var n = rich(feedback()); var first = parse(n).digest();
        var f = feedback(); f.put("future_extension", "ignored");
        var i = item(); i.put("future_extension", "ignored"); f.put("correctionItems", List.of(new TreeMap<>(i)));
        n.put("feedback", new TreeMap<>(f)); n.put("callback_id", UUID.randomUUID().toString());
        assertThat(parse(new TreeMap<>(n)).digest()).isEqualTo(first);
        f.put("summary", "Changed"); n.put("feedback", f);
        assertThat(parse(n).digest()).isNotEqualTo(first);
    }
    @Test void rejectsInvalidShapeMismatchAndLimits() {
        var f = feedback(); f.put("correctedAnswer", "Mismatch");
        assertInvalid(rich(f), 400);
        f = feedback(); f.put("correctionItems", Map.of()); assertInvalid(rich(f), 400);
        f = feedback(); f.put("correctionItems", List.of("not object")); assertInvalid(rich(f), 400);
        var i = item(); i.put("severity", "critical");
        f = feedback(); f.put("correctionItems", List.of(i)); assertInvalid(rich(f), 400);
        f = feedback(); f.put("summary", " "); assertInvalid(rich(f), 400);
        f = feedback(); f.put("summary", "a".repeat(501)); assertInvalid(rich(f), 413);
        f = feedback(); f.put("correctionItems", Collections.nCopies(21, item())); assertInvalid(rich(f), 413);
        i = item(); i.put("explanation", "a".repeat(501));
        f = feedback(); f.put("correctionItems", List.of(i)); assertInvalid(rich(f), 413);
        var n = callback("no_speech"); n.put("feedback", feedback()); assertInvalid(n, 400);
        n = callback("failed"); n.put("feedback", feedback()); assertInvalid(n, 400);
    }
    private static void assertInvalid(Map<String, Object> n, int status) {
        assertThatThrownBy(() -> parse(n)).isInstanceOfSatisfying(ChallengeFailure.class, e -> assertThat(e.status).isEqualTo(status));
    }
}
