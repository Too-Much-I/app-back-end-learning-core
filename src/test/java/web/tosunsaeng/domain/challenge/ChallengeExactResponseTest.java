package web.tosunsaeng.domain.challenge;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.SuccessStatus;
import static org.assertj.core.api.Assertions.*;
import static web.tosunsaeng.domain.challenge.ChallengeContractTest.*;
import static web.tosunsaeng.domain.challenge.ChallengeModels.*;

class ChallengeExactResponseTest {
    // Explicit field annotations must win even when the application omits nulls globally.
    private final ObjectMapper json = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private JsonNode response(Attempt attempt) {
        return json.valueToTree(BaseResponse.onSuccess(SuccessStatus.OK,
                new ChallengeViews.Results("2026-08-25", 1, ChallengeViews.detail(attempt))));
    }
    private static List<String> keys(JsonNode n) {
        var keys = new ArrayList<String>(); n.fieldNames().forEachRemaining(keys::add); return keys;
    }
    @Test void completedResponseHasExactlyRequestedFieldsAndAiValues() throws Exception {
        var a = attempt(); a.state = State.SUBMITTED; a.gradingStatus = "completed";
        a.question = new Question(1, 1, "opaque-1", "그녀는 검은색 신발을 신고 있어요.", "She's wearing black shoes.", 1);
        byte[] bytes = Files.readAllBytes(Path.of("docs/contracts/fixtures/ten-second-challenge-detailed-feedback.json"));
        a.result = ChallengeCallback.parse(bytes, "v1").result();
        var actual = response(a);
        var expected = json.readTree("""
                {
                  "isSuccess": true, "code": "COMMON_200", "message": "성공입니다.",
                  "result": {
                    "challengeDate": "2026-08-25", "solvedQuestionCount": 1,
                    "question": {
                      "questionNumber": 1, "promptKo": "그녀는 검은색 신발을 신고 있어요.",
                      "attemptStatus": "submitted", "gradingStatus": "completed",
                      "referenceAnswer": "She's wearing black shoes.",
                      "aiResult": {"transcript": "She wear black shoe.", "feedback": null}
                    }
                  }
                }
                """);
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected.at("/result/question/aiResult"))
                .set("feedback", json.readTree(bytes).get("feedback"));
        assertThat(actual).isEqualTo(expected);
        assertThat(keys(actual.at("/result/question/aiResult/feedback")))
                .containsExactly("summary", "correctedAnswer", "correctionItems");
        assertThat(keys(actual.at("/result/question/aiResult/feedback/correctionItems/0")))
                .containsExactly("type", "original", "issue", "explanation", "suggested", "severity");
    }
    @Test void noSpeechRetainsTranscriptNullAndAllFeedbackFieldsAsExplicitNulls() {
        var a = attempt(); a.state = State.SUBMITTED; a.gradingStatus = "completed";
        a.result = new Result(null, null, null, null);
        var question = response(a).at("/result/question");
        assertThat(keys(question)).containsExactly("questionNumber", "promptKo", "attemptStatus",
                "gradingStatus", "referenceAnswer", "aiResult");
        assertThat(question.get("referenceAnswer").asText()).isEqualTo(a.question.referenceAnswer());
        var expectedAi = json.createObjectNode().putNull("transcript");
        expectedAi.set("feedback", json.createObjectNode().putNull("summary")
                .putNull("correctedAnswer").putNull("correctionItems"));
        assertThat(question.get("aiResult")).isEqualTo(expectedAi);
    }
    @ParameterizedTest @ValueSource(strings = {"pending", "processing", "failed", "not_requested"})
    void missingResultIsExplicitNullWithoutExtraFields(String status) {
        var a = attempt(); a.state = status.equals("not_requested") ? State.EXPIRED : State.SUBMITTED;
        a.gradingStatus = status;
        var question = response(a).at("/result/question");
        assertThat(keys(question)).containsExactly("questionNumber", "promptKo", "attemptStatus",
                "gradingStatus", "referenceAnswer", "aiResult");
        assertThat(question.has("aiResult")).isTrue();
        assertThat(question.get("aiResult").isNull()).isTrue();
    }
    @Test void absentAttemptIsExplicitNullQuestion() {
        assertThat(response(null).at("/result").has("question")).isTrue();
        assertThat(response(null).at("/result/question").isNull()).isTrue();
        assertThat(response(attempt()).at("/result/question").isNull()).isTrue();
    }
    @Test void unavailableFeedbackFieldsAreNullButKnownEmptyCorrectionsStayEmpty() {
        var a = attempt(); a.state = State.SUBMITTED;
        a.result = new Result("Fixture", "correct", null, new Feedback(null, null, null));
        var feedback = response(a).at("/result/question/aiResult/feedback");
        assertThat(feedback).isEqualTo(json.createObjectNode().putNull("summary")
                .putNull("correctedAnswer").putNull("correctionItems"));
        a.result = new Result("Fixture", "correct", null, null, new DetailedFeedback("좋아요.", null, List.of()));
        feedback = response(a).at("/result/question/aiResult/feedback");
        assertThat(feedback.has("correctedAnswer")).isTrue();
        assertThat(feedback.get("correctedAnswer").isNull()).isTrue();
        assertThat(feedback.get("correctionItems").isArray()).isTrue();
        assertThat(feedback.get("correctionItems").isEmpty()).isTrue();
    }
}
