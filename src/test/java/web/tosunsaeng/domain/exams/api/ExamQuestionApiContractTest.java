package web.tosunsaeng.domain.exams.api;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamQuestionApiContractTest {

    @Test
    void questionPromptUsesExamAndQuestionPathVariables() throws NoSuchMethodException {
        Method method = ExamRestController.class.getDeclaredMethod(
                "getQuestionPrompt",
                String.class,
                Integer.class
        );
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        Parameter[] parameters = method.getParameters();
        PathVariable examId = parameters[0].getAnnotation(PathVariable.class);
        PathVariable questionNumber = parameters[1].getAnnotation(PathVariable.class);

        assertAll(
                () -> assertEquals(
                        "/{examId}/questions/{questionNumber}/prompt",
                        mapping.value()[0]
                ),
                () -> assertEquals("examId", examId.value()),
                () -> assertEquals("questionNumber", questionNumber.value())
        );
    }

    @Test
    void questionResultRemainsSingleQuestionGetWithRequiredNumberAndDefaultRetryZero()
            throws NoSuchMethodException {
        Method method = ExamRestController.class.getDeclaredMethod(
                "getExamQuestion",
                String.class,
                Integer.class,
                Integer.class
        );
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        Parameter[] parameters = method.getParameters();
        RequestParam questionNumber = parameters[1].getAnnotation(RequestParam.class);
        RequestParam retryCount = parameters[2].getAnnotation(RequestParam.class);

        long exactQuestionMappings = Arrays.stream(ExamRestController.class.getDeclaredMethods())
                .map(candidate -> candidate.getAnnotation(GetMapping.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .filter("/{examId}/questions"::equals)
                .count();

        assertAll(
                () -> assertEquals(1, exactQuestionMappings),
                () -> assertEquals("/{examId}/questions", mapping.value()[0]),
                () -> assertTrue(questionNumber.required()),
                () -> assertEquals("0", retryCount.defaultValue())
        );
    }

    @Test
    void summaryEndpointRemainsSeparateFromQuestionResult() throws NoSuchMethodException {
        Method method = ExamRestController.class.getDeclaredMethod("getExamSummary", String.class);
        GetMapping mapping = method.getAnnotation(GetMapping.class);

        assertEquals("/{examId}/summary", mapping.value()[0]);
    }
}
