package web.tosunsaeng.domain.exams.application;

import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.util.UUID;
import java.util.regex.Pattern;

public final class ExamCreationIdempotencyKey {

    private static final Pattern LOWERCASE_UUID_V4 = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
    );

    private ExamCreationIdempotencyKey() {
    }

    public static String parse(String raw) {
        if (raw == null || !LOWERCASE_UUID_V4.matcher(raw).matches()) {
            throw new ExamsException(ErrorStatus._IDEMPOTENCY_KEY_INVALID);
        }
        try {
            UUID parsed = UUID.fromString(raw);
            if (parsed.version() != 4 || !parsed.toString().equals(raw)) {
                throw new IllegalArgumentException("not canonical UUID v4");
            }
            return raw;
        } catch (IllegalArgumentException invalid) {
            throw new ExamsException(ErrorStatus._IDEMPOTENCY_KEY_INVALID);
        }
    }
}
