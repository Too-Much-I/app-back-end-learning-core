package web.tosunsaeng.domain.exams.attemptgroup.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.exams.attemptgroup.domain.AttemptGroupEventPayload;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class AttemptGroupEventCanonicalizer {
    private final ObjectMapper objectMapper;

    public AttemptGroupEventCanonicalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
    }

    public CanonicalEvent canonicalize(AttemptGroupEventPayload payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new CanonicalEvent(
                    json,
                    "sha256:" + HexFormat.of().formatHex(digest.digest(json.getBytes(StandardCharsets.UTF_8)))
            );
        } catch (JsonProcessingException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("AttemptGroup event canonicalization failed", failure);
        }
    }

    public record CanonicalEvent(String payload, String digest) {
    }
}
