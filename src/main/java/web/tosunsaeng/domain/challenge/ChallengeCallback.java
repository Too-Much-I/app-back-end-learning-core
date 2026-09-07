package web.tosunsaeng.domain.challenge;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import static web.tosunsaeng.domain.challenge.ChallengeModels.*;

public record ChallengeCallback(String callbackId, String attemptId, String jobId, int generation,
                                String outcome, Result result, String errorCode, boolean retryable, String digest) {
    static final ObjectMapper JSON = JsonMapper.builder().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public static ChallengeCallback parse(byte[] bytes, String version) {
        if (bytes.length > 16_384) throw oversized();
        try {
            JsonNode n = JSON.readTree(bytes);
            if (n == null || !n.isObject()) throw invalid();
            if (!"v1".equals(version) || !"v1".equals(text(n, "contract_version", 16, true)))
                throw new ChallengeFailure(400, "UNSUPPORTED_CONTRACT_VERSION");
            String callback = uuid(text(n, "callback_id", 36, true)), attempt = uuid(text(n, "attempt_id", 36, true));
            String job = text(n, "job_id", 160, true), outcome = text(n, "outcome", 32, true);
            JsonNode generationNode = n.get("grading_attempt");
            if (generationNode == null || !generationNode.isIntegralNumber() || !generationNode.canConvertToInt() || generationNode.intValue() < 1) throw invalid();
            int generation = generationNode.intValue();
            String transcript = text(n, "transcript", 1000, false), verdict = text(n, "verdict", 32, false);
            String corrected = text(n, "corrected_answer", 1000, false);
            JsonNode feedback = required(n, "feedback"), error = required(n, "error");
            Result result = null; String code = null; boolean retryable = false;
            if ("completed".equals(outcome)) {
                if (transcript == null || !("correct".equals(verdict) || "needs_improvement".equals(verdict))
                        || ("needs_improvement".equals(verdict) && corrected == null) || !feedback.isObject() || !error.isNull()) throw invalid();
                result = new Result(transcript, verdict, corrected, new Feedback(text(feedback, "meaning", 500, true),
                        text(feedback, "grammar", 500, true), text(feedback, "pronunciation", 500, true)));
            } else if ("no_speech".equals(outcome) || "failed".equals(outcome)) {
                if (transcript != null || verdict != null || corrected != null || !feedback.isNull()) throw invalid();
                if ("no_speech".equals(outcome)) { if (!error.isNull()) throw invalid(); result = new Result(null, null, null, null); }
                else {
                    if (!error.isObject()) throw invalid(); code = text(error, "code", 128, true);
                    JsonNode retry = required(error, "retryable"); if (!retry.isBoolean()) throw invalid(); retryable = retry.booleanValue();
                }
            } else throw invalid();
            // Known semantic fields only, stable field order, independent of callback ID and ignored extensions.
            String semantic = JSON.writeValueAsString(Arrays.asList("v1", attempt, job, generation, outcome, result, code, retryable));
            return new ChallengeCallback(callback, attempt, job, generation, outcome, result, code, retryable,
                    sha256(semantic.getBytes(StandardCharsets.UTF_8)));
        } catch (ChallengeFailure e) { throw e; }
        catch (Exception e) { throw invalid(); }
    }
    private static JsonNode required(JsonNode n, String field) { JsonNode value = n.get(field); if (value == null) throw invalid(); return value; }
    private static String text(JsonNode n, String field, int max, boolean mandatory) {
        JsonNode value = required(n, field);
        if (value.isNull() && !mandatory) return null;
        if (!value.isTextual() || value.textValue().isBlank()) throw invalid();
        String s = value.textValue(); if (s.codePointCount(0, s.length()) > max) throw oversized(); return s;
    }
    private static String uuid(String value) { try { return ChallengeCatalog.uuid(value); } catch (ChallengeFailure e) { throw invalid(); } }
    static ChallengeFailure invalid() { return new ChallengeFailure(400, "INVALID_CALLBACK"); }
    static ChallengeFailure oversized() { return new ChallengeFailure(413, "CALLBACK_PAYLOAD_TOO_LARGE"); }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
