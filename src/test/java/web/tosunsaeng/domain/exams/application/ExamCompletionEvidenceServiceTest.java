package web.tosunsaeng.domain.exams.application;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamCompletionEvidenceServiceTest {

    private static final String EXAM_ID = "ex_legacy_evidence";

    @Mock
    private MongoTemplate mongoTemplate;

    private ExamCompletionEvidenceService service;

    @BeforeEach
    void setUp() {
        service = new ExamCompletionEvidenceService(
                mongoTemplate,
                Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void examSummaryIsCompletionEvidence() {
        Instant createdAt = Instant.parse("2025-01-02T03:04:05Z");
        stubEvidence(
                List.of(new Document("_id", "summary:" + EXAM_ID + ":v1")
                        .append("createdAt", Date.from(createdAt))),
                List.of()
        );

        ExamCompletionEvidenceService.CompletionEvidence evidence =
                service.findCompletionEvidence(EXAM_ID, LocalDateTime.of(2025, 1, 1, 0, 0));

        assertAll(
                () -> assertTrue(evidence.completed()),
                () -> assertEquals(LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC), evidence.completedAt()),
                () -> assertEquals("exam_summaries.createdAt", evidence.timestampSource()),
                () -> assertEquals(1, evidence.summaryCount()),
                () -> assertEquals(0, evidence.legacyTotalScoreCount())
        );
    }

    @Test
    void legacyTotalScoreResultIsCompletionEvidence() {
        Instant completedAt = Instant.parse("2025-02-03T04:05:06Z");
        stubEvidence(
                List.of(),
                List.of(new Document("_id", "legacy-result")
                        .append("completedAt", Date.from(completedAt)))
        );

        ExamCompletionEvidenceService.CompletionEvidence evidence =
                service.findCompletionEvidence(EXAM_ID, null);

        assertAll(
                () -> assertTrue(evidence.completed()),
                () -> assertEquals(LocalDateTime.ofInstant(completedAt, ZoneOffset.UTC), evidence.completedAt()),
                () -> assertEquals("exam_results.completedAt", evidence.timestampSource()),
                () -> assertEquals(0, evidence.summaryCount()),
                () -> assertEquals(1, evidence.legacyTotalScoreCount())
        );
    }

    @Test
    void evidenceAcrossBothCollectionsUsesEarliestExplicitTimestampOnce() {
        Instant summaryTime = Instant.parse("2025-03-02T00:00:00Z");
        Instant legacyResultTime = Instant.parse("2025-03-01T00:00:00Z");
        stubEvidence(
                List.of(new Document("_id", "summary")
                        .append("createdAt", Date.from(summaryTime))),
                List.of(new Document("_id", "legacy-result")
                        .append("updatedAt", Date.from(legacyResultTime)))
        );

        ExamCompletionEvidenceService.CompletionEvidence evidence =
                service.findCompletionEvidence(EXAM_ID, LocalDateTime.of(2024, 1, 1, 0, 0));

        assertAll(
                () -> assertEquals(LocalDateTime.ofInstant(legacyResultTime, ZoneOffset.UTC), evidence.completedAt()),
                () -> assertEquals("exam_results.updatedAt", evidence.timestampSource()),
                () -> assertEquals(1, evidence.summaryCount()),
                () -> assertEquals(1, evidence.legacyTotalScoreCount())
        );
    }

    @Test
    void actualObjectIdTimestampIsUsedButStringIdFallsBackToSessionCreatedAt() {
        ObjectId actualObjectId = new ObjectId("65b9f4000000000000000031");
        stubEvidence(List.of(new Document("_id", actualObjectId)), List.of());

        ExamCompletionEvidenceService.CompletionEvidence objectIdEvidence =
                service.findCompletionEvidence(EXAM_ID, LocalDateTime.of(2020, 1, 1, 0, 0));

        assertEquals(
                LocalDateTime.ofInstant(actualObjectId.getDate().toInstant(), ZoneOffset.UTC),
                objectIdEvidence.completedAt()
        );

        LocalDateTime sessionCreatedAt = LocalDateTime.of(2024, 2, 1, 12, 0);
        stubEvidence(List.of(new Document("_id", "65b9f4000000000000000031")), List.of());

        ExamCompletionEvidenceService.CompletionEvidence stringIdEvidence =
                service.findCompletionEvidence(EXAM_ID, sessionCreatedAt);

        assertAll(
                () -> assertEquals(sessionCreatedAt, stringIdEvidence.completedAt()),
                () -> assertEquals("exam_sessions.createdAt (approximate)", stringIdEvidence.timestampSource()),
                () -> assertTrue(stringIdEvidence.approximateTimestamp())
        );
    }

    @Test
    void evidenceWithoutAnyTimestampStillBlocksReuseWithoutBackfillTime() {
        stubEvidence(List.of(), List.of(new Document("_id", "deterministic-result-id")));

        ExamCompletionEvidenceService.CompletionEvidence evidence =
                service.findCompletionEvidence(EXAM_ID, null);

        assertAll(
                () -> assertTrue(evidence.completed()),
                () -> assertNull(evidence.completedAt()),
                () -> assertEquals("unresolved", evidence.timestampSource())
        );
    }

    @Test
    void noSummaryOrLegacyTotalScoreEvidenceLeavesSessionInProgress() {
        stubEvidence(List.of(), List.of());

        ExamCompletionEvidenceService.CompletionEvidence evidence =
                service.findCompletionEvidence(EXAM_ID, LocalDateTime.of(2024, 1, 1, 0, 0));

        assertFalse(evidence.completed());
    }

    @Test
    void legacyResultQueryRequiresNonNullTotalScoreAndProjectsOnlyEvidenceFields() {
        stubEvidence(List.of(), List.of());

        service.findCompletionEvidence(EXAM_ID, null);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Document.class), eq("exam_results"));
        Document query = queryCaptor.getValue().getQueryObject();
        Document fields = queryCaptor.getValue().getFieldsObject();
        String queryJson = query.toJson();

        assertAll(
                () -> assertTrue(queryJson.contains("totalScore")),
                () -> assertTrue(queryJson.contains("$exists")),
                () -> assertTrue(queryJson.contains("$ne")),
                () -> assertEquals(1, fields.getInteger("_id")),
                () -> assertEquals(1, fields.getInteger("completedAt")),
                () -> assertEquals(1, fields.getInteger("createdAt")),
                () -> assertEquals(1, fields.getInteger("updatedAt")),
                () -> assertEquals(4, fields.size())
        );
    }

    private void stubEvidence(List<Document> summaries, List<Document> legacyResults) {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("exam_summaries")))
                .thenReturn(summaries);
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("exam_results")))
                .thenReturn(legacyResults);
    }
}
