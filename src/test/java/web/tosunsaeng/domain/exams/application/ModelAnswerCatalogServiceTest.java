package web.tosunsaeng.domain.exams.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelAnswerCatalogServiceTest {

    private ModelAnswerCatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new ModelAnswerCatalogService(new ObjectMapper());
    }

    @Test
    void mockExamFourQuestionOneKeepsEverySpokenWordValueAndNumericType() {
        List<ModelAnswerCatalogService.SpokenWord> sequence = catalogService
                .findSpokenWordSequence("mock_exam_004", 1)
                .orElseThrow();

        ModelAnswerCatalogService.SpokenWord first = sequence.getFirst();
        ModelAnswerCatalogService.SpokenWord last = sequence.getLast();
        assertAll(
                () -> assertEquals(55, sequence.size()),
                () -> assertEquals(0, first.index()),
                () -> assertEquals(0, first.segmentIndex()),
                () -> assertEquals(0, first.wordIndex()),
                () -> assertEquals("welcome", first.word()),
                () -> assertEquals(400000L, first.offset()),
                () -> assertEquals(7500000L, first.duration()),
                () -> assertEquals(94.0, first.accuracyScore()),
                () -> assertEquals(94.0, first.pronunciationScore()),
                () -> assertEquals("None", first.errorType()),
                () -> assertEquals(54, last.index()),
                () -> assertEquals(244900000L, last.offset()),
                () -> assertEquals(9000000L, last.duration())
        );
    }

    @Test
    void mockExamFourQuestionTwoUsesItsOwnSpokenWordSequence() {
        List<ModelAnswerCatalogService.SpokenWord> questionOne = catalogService
                .findSpokenWordSequence("mock_exam_004", 1)
                .orElseThrow();
        List<ModelAnswerCatalogService.SpokenWord> questionTwo = catalogService
                .findSpokenWordSequence("mock_exam_004", 2)
                .orElseThrow();

        ModelAnswerCatalogService.SpokenWord first = questionTwo.getFirst();
        ModelAnswerCatalogService.SpokenWord last = questionTwo.getLast();
        assertAll(
                () -> assertEquals(53, questionTwo.size()),
                () -> assertEquals("please", first.word()),
                () -> assertEquals(700000L, first.offset()),
                () -> assertEquals(4800000L, first.duration()),
                () -> assertEquals(94.0, first.accuracyScore()),
                () -> assertEquals(94.0, first.pronunciationScore()),
                () -> assertEquals(52, last.index()),
                () -> assertEquals(2, last.segmentIndex()),
                () -> assertEquals(13, last.wordIndex()),
                () -> assertEquals(221100000L, last.offset()),
                () -> assertEquals(5000000L, last.duration()),
                () -> assertFalse(questionOne.equals(questionTwo))
        );
    }

    @Test
    void unknownExamAndUnsupportedQuestionDoNotReuseAnotherAnswer() {
        assertAll(
                () -> assertTrue(catalogService
                        .findSpokenWordSequence("mock_exam_without_metadata", 1)
                        .isEmpty()),
                () -> assertTrue(catalogService
                        .findSpokenWordSequence("mock_exam_004", 3)
                        .isEmpty()),
                () -> assertTrue(catalogService
                        .findSpokenWordSequence(null, 1)
                        .isEmpty())
        );
    }
}
