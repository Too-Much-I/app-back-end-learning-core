package web.tosunsaeng.domain.exams.rollout;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;

/** Verified inventory membership, never permission to submit, restore or bypass owner/lifecycle checks. */
public final class LegacyExamManifest {
    public static final int MAX_ENTRIES = 100_000;
    public record Summary(String environment, String database, String revision, String sha256, int count) {
        @Override public String toString() {
            return "LegacyManifest[revision=" + revision + ", sha256=" + sha256 + ", count=" + count + "]";
        }
    }
    private final Summary summary;
    private final Set<String> ids;

    private LegacyExamManifest(Summary summary, Set<String> ids) {
        this.summary = summary;
        this.ids = Set.copyOf(ids);
    }

    static LegacyExamManifest build(String environment, String database, String revision, Collection<String> values) {
        try {
            if (!Set.of("test", "staging", "production").contains(environment)
                    || database == null || !database.matches("[A-Za-z0-9_-]{1,64}")
                    || Set.of("admin", "local", "config").contains(database.toLowerCase(Locale.ROOT))
                    || revision == null || !revision.matches("[a-z0-9][a-z0-9._-]{0,79}")
                    || values == null || values.size() > MAX_ENTRIES) throw invalid();
            var sorted = new TreeSet<String>();
            for (String id : values) {
                if (id == null || id.isBlank() || id.length() > 128 || id.chars().anyMatch(Character::isISOControl)) throw invalid();
                // Reject malformed UTF-16 rather than hash replacement characters.
                StandardCharsets.UTF_8.newEncoder().encode(java.nio.CharBuffer.wrap(id));
                if (!sorted.add(id)) throw invalid();
            }
            var digest = MessageDigest.getInstance("SHA-256");
            frame(digest, "lc-legacy-manifest-v1");
            frame(digest, environment); frame(digest, database); frame(digest, revision);
            digest.update(ByteBuffer.allocate(4).putInt(sorted.size()).array());
            for (String id : sorted) frame(digest, id);
            return new LegacyExamManifest(new Summary(environment, database, revision,
                    HexFormat.of().formatHex(digest.digest()), sorted.size()), sorted);
        } catch (Exception failure) { throw invalid(); }
    }

    private static void frame(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
    }

    public Summary summary() { return summary; }

    public boolean containsLegacy(ExamSession current) {
        if (current == null || !ids.contains(current.getExamId())) return false;
        if (current.getPaidReservation() != null || current.getCreationOperationId() != null
                || current.getBillingReservationId() != null || current.getAttemptGroupId() != null)
            throw invalid();
        return true;
    }

    static IllegalStateException invalid() { return new IllegalStateException("Legacy manifest verification failed"); }
    @Override public String toString() { return summary.toString(); }
}
