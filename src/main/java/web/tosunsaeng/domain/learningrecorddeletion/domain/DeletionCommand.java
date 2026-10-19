package web.tosunsaeng.domain.learningrecorddeletion.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document("learning_record_deletion_commands")
public record DeletionCommand(@Id String id, String deletionId, String semanticDigest,
                              Instant createdAt, Instant expiresAt) {
    public static final String SEMANTIC_DIGEST = DeletionIdentifiers.sha256(DeletionOperation.SCOPE + ":v1");

    public static DeletionCommand create(String owner, String key, String deletionId, Instant now) {
        return new DeletionCommand(DeletionIdentifiers.commandId(owner, key),
                DeletionIdentifiers.uuidV4(deletionId), SEMANTIC_DIGEST, now, null);
    }
}
