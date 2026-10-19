package web.tosunsaeng.domain.exams.application;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExamCreationIdempotencyKeyTest {

    @Test
    void acceptsCanonicalLowercaseUuidV4() {
        String key = "018f6f36-2f42-4bf5-8c17-0be35de4872c";
        assertEquals(key, ExamCreationIdempotencyKey.parse(key));
    }

    @Test
    void rejectsMissingUppercaseAndNonV4Keys() {
        for (String invalid : new String[]{
                null,
                "",
                "018F6F36-2F42-4BF5-8C17-0BE35DE4872C",
                "018f6f36-2f42-3bf5-8c17-0be35de4872c",
                "not-a-uuid"
        }) {
            ExamsException exception = assertThrows(
                    ExamsException.class,
                    () -> ExamCreationIdempotencyKey.parse(invalid)
            );
            assertEquals(ErrorStatus._IDEMPOTENCY_KEY_INVALID, exception.getCode());
        }
    }
}
