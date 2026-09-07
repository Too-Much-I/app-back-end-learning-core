package web.tosunsaeng.domain.challenge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static web.tosunsaeng.domain.challenge.ChallengeModels.*;

class ChallengeContractTest {
    @Test void sharedWireFixturesRemainParsable() throws Exception {
        var fixtures = new ObjectMapper().readTree(java.nio.file.Path.of("docs/contracts/fixtures/ten-second-challenge-v1.json").toFile());
        assertThat(fixtures.path("callbacks").size()).isEqualTo(3);
        for (var callback : fixtures.path("callbacks"))
            assertThat(ChallengeCallback.parse(new ObjectMapper().writeValueAsBytes(callback), "v1").attemptId()).isEqualTo(ID);
    }
    static final String ID = "10000000-0000-4000-8000-000000000001";
    static final String OWNER = "20000000-0000-4000-8000-000000000001";
    static final Instant NOW = Instant.parse("2026-09-07T14:59:50Z");
    static Attempt attempt() { return Attempt.create(ID, OWNER, "2026-09-07", new Question(1, 1, "opaque-1", "테스트 문장", "Fixture answer", -2), NOW); }
    static Map<String, Object> callback(String outcome) {
        Map<String, Object> n = new LinkedHashMap<>(); n.put("contract_version", "v1"); n.put("callback_id", UUID.randomUUID().toString());
        n.put("attempt_id", ID); n.put("job_id", Job.id(ID, 1)); n.put("grading_attempt", 1); n.put("outcome", outcome);
        n.put("transcript", null); n.put("verdict", null); n.put("corrected_answer", null); n.put("feedback", null); n.put("error", null);
        if (outcome.equals("completed")) {
            n.put("transcript", "Fixture alternative"); n.put("verdict", "correct");
            n.put("feedback", Map.of("meaning", "의미 피드백", "grammar", "문법 피드백", "pronunciation", "발음 피드백"));
        }
        if (outcome.equals("failed")) n.put("error", Map.of("code", "MODEL_TIMEOUT", "retryable", true));
        return n;
    }
    static ChallengeCallback parse(Map<String, Object> n) throws Exception { return ChallengeCallback.parse(new ObjectMapper().writeValueAsBytes(n), "v1"); }
    @Test void exactKeyAndHourSurviveMidnight() {
        Attempt a = attempt(); assertThat(a.uploadKey).isEqualTo("temp/challenges/" + ID + "/q_1.m4a");
        assertThat(a.submissionDeadlineAt).isEqualTo(Instant.parse("2026-09-07T15:59:50Z"));
        assertThat(a.challengeDate).isEqualTo("2026-09-07");
    }
    @Test void noAnswerLeakBeforeSubmissionAndNoSpeechKeepsSnapshot() throws Exception {
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule()); Attempt a = attempt();
        assertThat(json.writeValueAsString(ChallengeViews.start(a))).doesNotContain("referenceAnswer", "userId", "uploadKey", "difficulty");
        assertThat(ChallengeViews.detail(a)).isNull();
        a.state = State.SUBMITTED; a.result = new Result(null, null, null, null); a.gradingStatus = "completed";
        var detail = json.readTree(json.writeValueAsBytes(ChallengeViews.detail(a)));
        assertThat(detail.path("aiResult").isObject()).isTrue();
        assertThat(detail.path("aiResult").path("referenceAnswer").textValue()).isEqualTo(a.question.referenceAnswer());
        assertThat(detail.path("aiResult").path("transcript").isNull()).isTrue();
        assertThat(detail.path("aiResult").path("feedback").isNull()).isTrue();
    }
    @Test void expiryHasNoFakeSubmitTimeAndCountShapeOmitsQuestion() throws Exception {
        Attempt a = attempt(); a.state = State.EXPIRED;
        var d = ChallengeViews.detail(a); assertThat(d.submittedAt()).isNull(); assertThat(d.gradedAt()).isNull();
        assertThat(d.referenceAnswer()).isEqualTo(a.question.referenceAnswer()); assertThat(d.aiResult()).isNull();
        ObjectMapper json = new ObjectMapper();
        assertThat(json.writeValueAsString(new ChallengeViews.Count(a.challengeDate, 0))).doesNotContain("question");
        assertThat(json.readTree(json.writeValueAsBytes(new ChallengeViews.Results(a.challengeDate, 0, null))).get("question").isNull()).isTrue();
    }
    @ParameterizedTest @ValueSource(strings = {"completed", "no_speech", "failed"})
    void parsesKnownOutcomes(String outcome) throws Exception { assertThat(parse(callback(outcome)).outcome()).isEqualTo(outcome); }
    @Test void digestIgnoresExtensionsJsonOrderAndCallbackId() throws Exception {
        Map<String, Object> n = callback("completed"); String digest = parse(n).digest();
        n.put("callback_id", UUID.randomUUID().toString()); n.put("future_extension", "ignored");
        assertThat(parse(new TreeMap<>(n)).digest()).isEqualTo(digest);
        n.put("transcript", "Another answer"); assertThat(parse(n).digest()).isNotEqualTo(digest);
    }
    @Test void strictTypesAndConditionalFields() throws Exception {
        Map<String, Object> n = callback("no_speech"); n.put("grading_attempt", "1"); assertThatThrownBy(() -> parse(n)).isInstanceOf(ChallengeFailure.class);
        n.put("grading_attempt", 1.0); assertThatThrownBy(() -> parse(n)).isInstanceOf(ChallengeFailure.class);
        n.put("grading_attempt", 1); n.remove("feedback"); assertThatThrownBy(() -> parse(n)).isInstanceOf(ChallengeFailure.class);
        var c = callback("completed"); c.put("verdict", "needs_improvement"); assertThatThrownBy(() -> parse(c)).isInstanceOf(ChallengeFailure.class);
        c.put("corrected_answer", "Fixture correction"); assertThat(parse(c).result().correctedAnswer()).isNotBlank();
        var f = callback("failed"); f.put("error", Map.of("code", "MODEL_TIMEOUT", "retryable", "true")); assertThatThrownBy(() -> parse(f)).isInstanceOf(ChallengeFailure.class);
    }
    @Test void rejectsDuplicateKeysTrailingJsonVersionAndOversize() throws Exception {
        byte[] valid = new ObjectMapper().writeValueAsBytes(callback("no_speech"));
        assertThatThrownBy(() -> ChallengeCallback.parse(valid, "v2")).isInstanceOf(ChallengeFailure.class);
        assertThatThrownBy(() -> ChallengeCallback.parse((new String(valid, StandardCharsets.UTF_8) + "{}").getBytes(StandardCharsets.UTF_8), "v1")).isInstanceOf(ChallengeFailure.class);
        assertThatThrownBy(() -> ChallengeCallback.parse("{\"outcome\":1,\"outcome\":2}".getBytes(), "v1")).isInstanceOf(ChallengeFailure.class);
        assertThatThrownBy(() -> ChallengeCallback.parse(new byte[16385], "v1")).satisfies(e -> assertThat(((ChallengeFailure)e).status).isEqualTo(413));
        var n = callback("completed"); n.put("transcript", "a".repeat(1001));
        assertThatThrownBy(() -> parse(n)).satisfies(e -> assertThat(((ChallengeFailure)e).status).isEqualTo(413));
    }
    @Test void multipartUsesSnapshotAndNoIdentityOrS3Fields() {
        Attempt a = attempt(); a.generation = 1; Job j = Job.pending(a, NOW);
        String body = new String(ChallengeAiClient.multipart(a, j, new byte[]{1,2,3}, "fixture-boundary"), StandardCharsets.UTF_8);
        for (String field : List.of("contract_version", "attempt_id", "job_id", "grading_attempt", "question_id", "question_number", "prompt_ko", "reference_answer", "audio_file"))
            assertThat(body).contains("name=\"" + field + "\"");
        assertThat(body).doesNotContain(OWNER, a.uploadKey, "difficulty", "dayNumber", "challengeDate", "Authorization");
        assertThat(body).contains("filename=\"audio.m4a\"", "Content-Type: audio/mp4", a.question.referenceAnswer());
    }
    @ParameterizedTest @ValueSource(strings = {"GUEST", "UNKNOWN", "member", "missing"})
    void nonMemberDenied(String type) {
        var builder = Jwt.withTokenValue("synthetic-token").header("alg", "RS256").subject(OWNER);
        if (!type.equals("missing")) builder.claim("account_type", type);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(), List.of()));
        try { assertThatThrownBy(ChallengeController::member).satisfies(e -> assertThat(((ChallengeFailure)e).status).isEqualTo(403)); }
        finally { SecurityContextHolder.clearContext(); }
    }
    @Test void authenticatedMemberOnlyAndLegacyDenied() {
        SecurityContextHolder.clearContext(); assertThatThrownBy(ChallengeController::member).isInstanceOf(ChallengeFailure.class);
        var jwt = Jwt.withTokenValue("synthetic-token").header("alg", "RS256").subject(OWNER).claim("account_type", "MEMBER").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        try { assertThat(ChallengeController.member()).isEqualTo(OWNER); } finally { SecurityContextHolder.clearContext(); }
    }
    @Test void catalogRejectsBsonCoercionAndDuplicateIds() {
        List<Document> questions = new ArrayList<>();
        for (int q = 1; q <= 3; q++) questions.add(new Document("questionNumber", q).append("questionId", "opaque" + q).append("korean", "문장").append("referenceAnswer", "Fixture").append("difficulty", -99));
        Document day = new Document("dayNumber", 1).append("questions", questions);
        assertThat(ChallengeCatalog.validate(day)).hasSize(3);
        questions.getFirst().put("difficulty", 2.0); assertThatThrownBy(() -> ChallengeCatalog.validate(day)).isInstanceOf(ChallengeFailure.class);
        questions.getFirst().put("difficulty", 2); questions.get(1).put("questionId", "opaque1");
        assertThatThrownBy(() -> ChallengeCatalog.validate(day)).isInstanceOf(ChallengeFailure.class);
    }
    @Test void audioMetadataBoundsAndRetryAfter() {
        ChallengeAudioStorage.metadata("audio/mp4", 2097152L);
        assertThatThrownBy(() -> ChallengeAudioStorage.metadata("audio/wav", 1L)).isInstanceOf(ChallengeFailure.class);
        assertThatThrownBy(() -> ChallengeAudioStorage.metadata("audio/mp4", 2097153L)).isInstanceOf(ChallengeFailure.class);
        assertThatThrownBy(() -> ChallengeAudioStorage.metadata("audio/mp4", 0L)).isInstanceOf(ChallengeFailure.class);
        assertThat(ChallengeAiClient.retryAfter("3600", NOW)).isEqualTo(NOW.plusSeconds(3600));
        assertThat(ChallengeAiClient.retryAfter("Mon, 07 Sep 2026 15:00:00 GMT", NOW)).isEqualTo(Instant.parse("2026-09-07T15:00:00Z"));
    }
}
