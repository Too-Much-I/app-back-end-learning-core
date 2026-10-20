package web.tosunsaeng.domain.answerlearning;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class LearningWordContractTest {
    private static Document answer(Object sentence) {
        return new Document("sentences", Arrays.asList(sentence));
    }

    private static Document sentence(String text) {
        return new Document("text", text).append("words", List.of(
                new Document("text", "It's"), new Document("text", "fine.")));
    }

    @Test
    void preservesPunctuationAndWordPositionsWithoutMutation() {
        Document input = answer(sentence("It's fine."));
        String before = input.toJson();
        LearningCatalog.validateWords(input);
        assertThat(input.toJson()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"It's  fine.", " It's fine.", "It's fine. ", "It's fine", "it's fine.", "fine. It's", "It's\tfine.", "It's\u00a0fine."})
    void rejectsNonExactMatch(String text) {
        assertUnavailable(answer(sentence(text)));
    }

    static Stream<Document> malformed() {
        return Stream.of(
                new Document(), new Document("sentences", "invalid"),
                new Document("sentences", List.of()), answer(null), answer("invalid"),
                answer(new Document("text", "It's fine.")),
                answer(sentence("")), answer(sentence(" ")), answer(sentence(null)),
                answer(sentence("It's fine.").append("text", 1)),
                answer(sentence("It's fine.").append("words", "invalid")),
                answer(sentence("It's fine.").append("words", List.of())),
                answer(sentence("It's fine.").append("words", Arrays.asList((Object) null))),
                answer(sentence("It's fine.").append("words", List.of("invalid"))),
                answer(sentence("It's fine.").append("words", List.of(new Document()))),
                answer(sentence("It's fine.").append("words", List.of(new Document("text", 1)))),
                answer(sentence("It's fine.").append("words", List.of(new Document("text", " ")))));
    }

    @ParameterizedTest
    @MethodSource("malformed")
    void rejectsMalformedDataWithStableError(Document input) {
        assertUnavailable(input);
    }

    @Test
    void checksEverySentence() {
        assertUnavailable(new Document("sentences", List.of(sentence("It's fine."), sentence("Mismatch"))));
    }

    private static void assertUnavailable(Document input) {
        assertThatThrownBy(() -> LearningCatalog.validateWords(input))
                .isInstanceOfSatisfying(LearningFailure.class, failure -> {
                    assertThat(failure.status).isEqualTo(503);
                    assertThat(failure.code).isEqualTo("LEARNING_UNAVAILABLE");
                });
    }
}
