package web.tosunsaeng.domain.answerlearning;

import jakarta.validation.constraints.*;
import org.bson.Document;
import org.springframework.data.annotation.Id;
import java.time.Instant;
import java.util.Set;

public final class LearningModels {
    private LearningModels() {}
    public static final Set<String> TERMINAL = Set.of("completed", "failed", "no_speech");
    public record Create(@NotBlank String submissionId, @NotBlank @Size(max=160) String lessonId,
                         @NotBlank String level, @NotBlank @Size(max=160) String contentVersion) {}
    public record Upload(@NotBlank String contentType, @Min(1) @Max(8388608) long sizeBytes) {}
    public record Completion(@NotBlank String level, @NotBlank @Size(max=160) String contentVersion, String submissionId) {}
    public record Audio(String bucket, String key, String etag, String sha256) {}
    public record Error(String code, String message, boolean retryable) {}
    public record Job(String id, String lessonId, String contentVersion, String level,
                      String status, Document result, Error error) {}
    public record UploadUrl(String submissionId, String url, Instant expiresAt, int maxBytes) {}

    @org.springframework.data.mongodb.core.mapping.Document("learning_submissions")
    public static class Submission {
        @Id public String id;
        public String userId;
        public String lessonId;
        public String contentVersion;
        public String level;
        public String status = "awaiting_audio";
        public int attempt = 1;
        public int transportFailures;
        public long uploadSize;
        public Document checklist;
        public Audio audio;
        public Document result;
        public Error error;
        public Instant createdAt;
        public Instant queuedAt;
        public Instant updatedAt;
        public Instant externalWriteUntil;
        public Instant nextRunAt;
        public Instant leaseUntil;
        public String leaseToken;
        public boolean dispatched;
        public boolean completionPending;
        public Job view() { return new Job(id, lessonId, contentVersion, level, status, result, error); }
    }
}
