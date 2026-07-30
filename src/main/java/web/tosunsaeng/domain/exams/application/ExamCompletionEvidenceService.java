package web.tosunsaeng.domain.exams.application;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExamCompletionEvidenceService {

    private static final String EXAM_SUMMARIES = "exam_summaries";
    private static final String EXAM_RESULTS = "exam_results";
    private static final List<String> EXPLICIT_TIMESTAMP_FIELDS =
            List.of("completedAt", "createdAt", "updatedAt");

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public CompletionEvidence findCompletionEvidence(String examId, LocalDateTime sessionCreatedAt) {
        List<EvidenceDocument> evidenceDocuments = new ArrayList<>();

        Query summaryQuery = Query.query(new Criteria().orOperator(
                Criteria.where("examId").is(examId),
                Criteria.where("_id").is(GradingKeys.summaryJobId(examId))
        ));
        includeEvidenceFields(summaryQuery);
        List<Document> summaries = safeFind(summaryQuery, EXAM_SUMMARIES);
        summaries.forEach(document -> evidenceDocuments.add(new EvidenceDocument(EXAM_SUMMARIES, document)));

        Query legacyResultQuery = Query.query(new Criteria().andOperator(
                Criteria.where("examId").is(examId),
                Criteria.where("totalScore").exists(true).ne(null)
        ));
        includeEvidenceFields(legacyResultQuery);
        List<Document> legacyResults = safeFind(legacyResultQuery, EXAM_RESULTS);
        legacyResults.forEach(document -> evidenceDocuments.add(new EvidenceDocument(EXAM_RESULTS, document)));

        if (evidenceDocuments.isEmpty()) {
            return CompletionEvidence.none();
        }

        Optional<TimestampCandidate> explicitTimestamp = evidenceDocuments.stream()
                .flatMap(evidence -> EXPLICIT_TIMESTAMP_FIELDS.stream()
                        .map(field -> timestampCandidate(evidence, field))
                        .flatMap(Optional::stream))
                .min(TimestampCandidate.ORDER);
        if (explicitTimestamp.isPresent()) {
            TimestampCandidate selected = explicitTimestamp.get();
            return CompletionEvidence.completed(
                    toLocalDateTime(selected.instant()),
                    selected.source(),
                    false,
                    summaries.size(),
                    legacyResults.size()
            );
        }

        Optional<TimestampCandidate> objectIdTimestamp = evidenceDocuments.stream()
                .filter(evidence -> evidence.document().get("_id") instanceof ObjectId)
                .map(evidence -> new TimestampCandidate(
                        ((ObjectId) evidence.document().get("_id")).getDate().toInstant(),
                        evidence.collection() + " ObjectId timestamp",
                        0
                ))
                .min(TimestampCandidate.ORDER);
        if (objectIdTimestamp.isPresent()) {
            TimestampCandidate selected = objectIdTimestamp.get();
            return CompletionEvidence.completed(
                    toLocalDateTime(selected.instant()),
                    selected.source(),
                    false,
                    summaries.size(),
                    legacyResults.size()
            );
        }

        if (sessionCreatedAt != null) {
            return CompletionEvidence.completed(
                    sessionCreatedAt,
                    "exam_sessions.createdAt (approximate)",
                    true,
                    summaries.size(),
                    legacyResults.size()
            );
        }

        return CompletionEvidence.completed(
                null,
                "unresolved",
                true,
                summaries.size(),
                legacyResults.size()
        );
    }

    private List<Document> safeFind(Query query, String collection) {
        List<Document> documents = mongoTemplate.find(query, Document.class, collection);
        return documents == null ? List.of() : documents;
    }

    private static void includeEvidenceFields(Query query) {
        query.fields()
                .include("_id")
                .include("completedAt")
                .include("createdAt")
                .include("updatedAt");
    }

    private Optional<TimestampCandidate> timestampCandidate(EvidenceDocument evidence, String field) {
        return asInstant(evidence.document().get(field))
                .map(instant -> new TimestampCandidate(
                        instant,
                        evidence.collection() + "." + field,
                        EXPLICIT_TIMESTAMP_FIELDS.indexOf(field)
                ));
    }

    private Optional<Instant> asInstant(Object value) {
        if (value instanceof Date date) {
            return Optional.of(date.toInstant());
        }
        if (value instanceof Instant instant) {
            return Optional.of(instant);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return Optional.of(localDateTime.atZone(clock.getZone()).toInstant());
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return Optional.of(offsetDateTime.toInstant());
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return Optional.of(zonedDateTime.toInstant());
        }
        return Optional.empty();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, clock.getZone());
    }

    public record CompletionEvidence(
            boolean completed,
            LocalDateTime completedAt,
            String timestampSource,
            boolean approximateTimestamp,
            int summaryCount,
            int legacyTotalScoreCount) {

        private static CompletionEvidence none() {
            return new CompletionEvidence(false, null, "none", false, 0, 0);
        }

        private static CompletionEvidence completed(
                LocalDateTime completedAt,
                String timestampSource,
                boolean approximateTimestamp,
                int summaryCount,
                int legacyTotalScoreCount) {
            return new CompletionEvidence(
                    true,
                    completedAt,
                    timestampSource,
                    approximateTimestamp,
                    summaryCount,
                    legacyTotalScoreCount
            );
        }
    }

    private record EvidenceDocument(String collection, Document document) {
    }

    private record TimestampCandidate(Instant instant, String source, int fieldPriority) {
        private static final Comparator<TimestampCandidate> ORDER = Comparator
                .comparing(TimestampCandidate::instant)
                .thenComparingInt(TimestampCandidate::fieldPriority)
                .thenComparing(TimestampCandidate::source);
    }
}
