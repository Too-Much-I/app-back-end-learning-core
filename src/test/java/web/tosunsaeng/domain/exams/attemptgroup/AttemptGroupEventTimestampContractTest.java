package web.tosunsaeng.domain.exams.attemptgroup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import web.tosunsaeng.domain.exams.attemptgroup.domain.*;
import web.tosunsaeng.domain.exams.attemptgroup.infrastructure.AttemptGroupEventCanonicalizer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class AttemptGroupEventTimestampContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final AttemptGroupEventCanonicalizer canonicalizer = new AttemptGroupEventCanonicalizer(mapper);

    @ParameterizedTest
    @EnumSource(AttemptGroupEventTarget.class)
    void newEventsMeetBillingMillisContractBeforeCanonicalization(AttemptGroupEventTarget target) throws Exception {
        var payload = payload(Instant.parse("2026-10-20T09:52:37.552146195Z"), target);
        var canonical = canonicalizer.canonicalize(payload);
        var wire = mapper.readTree(canonical.payload());
        assertEquals("2026-10-20T09:52:37.552Z", wire.get("occurredAt").asText());
        Instant decoded = Instant.parse(wire.get("occurredAt").asText());
        // Mirrors Billing AttemptGroupEventDecoder.requiredInstant's acceptance condition.
        assertEquals(decoded.truncatedTo(ChronoUnit.MILLIS), decoded);
        assertEquals(payload.occurredAt(), decoded);
        assertEquals(canonical, canonicalizer.canonicalize(payload));
        assertEquals(target.name(), wire.get("targetStatus").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-20T09:52:37Z", "2026-10-20T09:52:37.552Z"})
    void validPrecisionKeepsInstantAndWritesExactlyThreeFractionDigits(String timestamp) throws Exception {
        var payload = payload(Instant.parse(timestamp), AttemptGroupEventTarget.GRADING);
        assertEquals(Instant.parse(timestamp), payload.occurredAt());
        String wireTime = mapper.readTree(canonicalizer.canonicalize(payload).payload()).get("occurredAt").asText();
        assertEquals(timestamp.contains(".552") ? "2026-10-20T09:52:37.552Z" : "2026-10-20T09:52:37.000Z", wireTime);
        assertEquals(Instant.parse(timestamp), Instant.parse(wireTime));
    }

    private AttemptGroupEventPayload payload(Instant time, AttemptGroupEventTarget target) {
        return new AttemptGroupEventPayload("00000000-0000-4000-8000-000000000001",
                "AttemptGroupStatusChanged", 1, "learning-core", time,
                "00000000-0000-4000-8000-000000000002",
                "00000000-0000-4000-8000-000000000003", "ex_contract", target,
                target == AttemptGroupEventTarget.COMPLETED ? AttemptGroupCompletionEvidence.complete() : null,
                target == AttemptGroupEventTarget.RETAKE_AVAILABLE
                        ? AttemptGroupFailureCode.SUMMARY_UNAVAILABLE : null);
    }
}
