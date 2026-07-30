package web.tosunsaeng.domain.exams.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionCompletionQuery;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamSessionManagerTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000031";
    private static final String OTHER_USER_ID = "00000000-0000-0000-0000-000000000099";
    private static final Instant NOW = Instant.parse("2026-07-29T06:30:00Z");

    @Mock
    private ExamSessionRepository examSessionRepository;

    @Mock
    private ExamSessionCompletionQuery examSessionCompletionQuery;

    @Mock
    private ExamCompletionEvidenceService completionEvidenceService;

    @Mock
    private MockExamCatalogService mockExamCatalogService;

    private ExamSessionManager manager;
    private List<MockExamCatalogService.CatalogExam> catalog;

    @BeforeEach
    void setUp() {
        manager = new ExamSessionManager(
                examSessionRepository,
                examSessionCompletionQuery,
                completionEvidenceService,
                mockExamCatalogService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        catalog = List.of(catalogExam(1), catalogExam(2), catalogExam(3));
    }

    @Test
    void noCompletionSelectsSequenceOne() {
        stubNewAssignment(List.of());

        ExamSessionManager.Assignment assignment = manager.findOrCreate(USER_ID);

        assertSelected(assignment, 1, 1);
    }

    @Test
    void completingOneSelectsSequenceTwo() {
        stubNewAssignment(List.of(completed(USER_ID, 1)));

        ExamSessionManager.Assignment assignment = manager.findOrCreate(USER_ID);

        assertSelected(assignment, 2, 1);
    }

    @Test
    void completingOneAndTwoSelectsSequenceThree() {
        stubNewAssignment(List.of(completed(USER_ID, 1), completed(USER_ID, 2)));

        ExamSessionManager.Assignment assignment = manager.findOrCreate(USER_ID);

        assertSelected(assignment, 3, 1);
    }

    @Test
    void completingEveryExamOnceStartsSequenceOneCycleTwo() {
        stubNewAssignment(List.of(
                completed(USER_ID, 1),
                completed(USER_ID, 2),
                completed(USER_ID, 3)
        ));

        ExamSessionManager.Assignment assignment = manager.findOrCreate(USER_ID);

        assertSelected(assignment, 1, 2);
    }

    @Test
    void completionCountsTwoOneOneSelectSequenceTwoCycleTwo() {
        stubNewAssignment(List.of(
                completed(USER_ID, 1),
                completed(USER_ID, 1),
                completed(USER_ID, 2),
                completed(USER_ID, 3)
        ));

        ExamSessionManager.Assignment assignment = manager.findOrCreate(USER_ID);

        assertSelected(assignment, 2, 2);
    }

    @Test
    void anotherUsersCompletionsDoNotAffectSelection() {
        when(examSessionRepository.findActiveOrLegacyCandidatesByUserId(USER_ID)).thenReturn(List.of());
        when(examSessionCompletionQuery.countCompletedByMockExamId(USER_ID)).thenReturn(List.of());
        when(mockExamCatalogService.findAssignableExams()).thenReturn(catalog);
        when(examSessionRepository.insert(any(ExamSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExamSessionManager.Assignment assignment = manager.findOrCreate(USER_ID);

        assertSelected(assignment, 1, 1);
        verify(examSessionCompletionQuery, never()).countCompletedByMockExamId(OTHER_USER_ID);
    }

    @Test
    void reusableActiveSessionKeepsSameExamIdAndPaper() {
        ExamSession existing = ExamSession.builder()
                .examId("ex_existing_001")
                .userId(USER_ID)
                .mockExamId("mock_exam_002")
                .cycleNumber(1)
                .active(true)
                .build();
        MockExam paper = mockExam(2);
        when(examSessionRepository.findActiveOrLegacyCandidatesByUserId(USER_ID)).thenReturn(List.of(existing));
        when(mockExamCatalogService.getRequiredExam("mock_exam_002")).thenReturn(paper);

        ExamSessionManager.Assignment assignment = manager.findOrCreate(USER_ID);

        assertAll(
                () -> assertFalse(assignment.created()),
                () -> assertEquals("ex_existing_001", assignment.session().getExamId()),
                () -> assertEquals("mock_exam_002", assignment.mockExam().getMockExamId())
        );
        verify(examSessionRepository, never()).insert(any(ExamSession.class));
        verify(mockExamCatalogService, never()).findAssignableExams();
        verify(completionEvidenceService, never()).findCompletionEvidence(any(), any());
    }

    @Test
    void legacyNullMockExamIdReusesFallbackPaper() {
        ExamSession legacy = ExamSession.builder()
                .examId("ex_legacy_001")
                .userId(USER_ID)
                .build();
        MockExam fallback = MockExam.builder()
                .mockExamId(GradingKeys.LEGACY_MOCK_EXAM_ID)
                .questions(List.of(Question.builder().questionNumber(1).build()))
                .build();
        when(examSessionRepository.findActiveOrLegacyCandidatesByUserId(USER_ID)).thenReturn(List.of(legacy));
        when(completionEvidenceService.findCompletionEvidence(legacy.getExamId(), legacy.getCreatedAt()))
                .thenReturn(noCompletionEvidence());
        when(mockExamCatalogService.getRequiredExam(GradingKeys.LEGACY_MOCK_EXAM_ID))
                .thenReturn(fallback);

        ExamSessionManager.Assignment assignment = manager.findOrCreate(USER_ID);

        assertEquals(GradingKeys.LEGACY_MOCK_EXAM_ID, assignment.mockExam().getMockExamId());
        verify(examSessionRepository, never()).insert(any(ExamSession.class));
    }

    @Test
    void summarizedLegacySessionIsBackfilledAndNextExamIsAssigned() {
        ExamSession legacy = ExamSession.builder()
                .examId("ex_legacy_completed_001")
                .userId(USER_ID)
                .mockExamId("mock_exam_001")
                .build();
        LocalDateTime completedAt = LocalDateTime.of(2024, 1, 31, 6, 30);
        when(examSessionRepository.findActiveOrLegacyCandidatesByUserId(USER_ID))
                .thenReturn(List.of(legacy));
        when(completionEvidenceService.findCompletionEvidence(legacy.getExamId(), legacy.getCreatedAt()))
                .thenReturn(completionEvidence(completedAt, 1, 0));
        when(examSessionRepository.backfillLegacyCompletionIfUnchanged(
                legacy.getExamId(), completedAt)).thenReturn(1L);
        when(examSessionCompletionQuery.countCompletedByMockExamId(USER_ID)).thenReturn(List.of(
                new ExamSessionCompletionQuery.CompletionCount("mock_exam_001", 1)
        ));
        when(mockExamCatalogService.findAssignableExams()).thenReturn(catalog);
        when(examSessionRepository.insert(any(ExamSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExamSessionManager.Assignment assignment = manager.findOrCreate(USER_ID);

        assertSelected(assignment, 2, 1);
        verify(examSessionRepository).backfillLegacyCompletionIfUnchanged(
                legacy.getExamId(), completedAt);
    }

    @Test
    void legacyTotalScoreCompletionIsBackfilledAndNextExamIsAssigned() {
        LocalDateTime sessionCreatedAt = LocalDateTime.of(2024, 1, 1, 9, 0);
        LocalDateTime completedAt = LocalDateTime.of(2024, 1, 31, 6, 30);
        ExamSession legacy = ExamSession.builder()
                .examId("ex_legacy_total_score")
                .userId(USER_ID)
                .mockExamId("mock_exam_001")
                .createdAt(sessionCreatedAt)
                .build();
        when(examSessionRepository.findActiveOrLegacyCandidatesByUserId(USER_ID))
                .thenReturn(List.of(legacy));
        when(completionEvidenceService.findCompletionEvidence(legacy.getExamId(), sessionCreatedAt))
                .thenReturn(completionEvidence(completedAt, 0, 1));
        when(examSessionRepository.backfillLegacyCompletionIfUnchanged(legacy.getExamId(), completedAt))
                .thenReturn(1L);
        when(examSessionCompletionQuery.countCompletedByMockExamId(USER_ID)).thenReturn(List.of(
                new ExamSessionCompletionQuery.CompletionCount("mock_exam_001", 1)
        ));
        when(mockExamCatalogService.findAssignableExams()).thenReturn(catalog);
        when(examSessionRepository.insert(any(ExamSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExamSessionManager.Assignment assignment = manager.findOrCreate(USER_ID);

        assertSelected(assignment, 2, 1);
        verify(examSessionRepository).backfillLegacyCompletionIfUnchanged(legacy.getExamId(), completedAt);
    }

    @Test
    void legacyExplicitActiveSessionWithCompletionEvidenceIsNotReused() {
        LocalDateTime sessionCreatedAt = LocalDateTime.of(2024, 1, 1, 9, 0);
        LocalDateTime completedAt = LocalDateTime.of(2024, 1, 31, 6, 30);
        ExamSession legacy = ExamSession.builder()
                .examId("ex_legacy_explicit_active")
                .userId(USER_ID)
                .mockExamId("mock_exam_001")
                .createdAt(sessionCreatedAt)
                .active(true)
                .cycleNumber(null)
                .build();
        when(examSessionRepository.findActiveOrLegacyCandidatesByUserId(USER_ID))
                .thenReturn(List.of(legacy));
        when(completionEvidenceService.findCompletionEvidence(legacy.getExamId(), sessionCreatedAt))
                .thenReturn(completionEvidence(completedAt, 0, 1));
        when(examSessionRepository.backfillLegacyActiveCompletionIfUnchanged(
                legacy.getExamId(), completedAt)).thenReturn(1L);
        when(examSessionCompletionQuery.countCompletedByMockExamId(USER_ID)).thenReturn(List.of(
                new ExamSessionCompletionQuery.CompletionCount("mock_exam_001", 1)
        ));
        when(mockExamCatalogService.findAssignableExams()).thenReturn(catalog);
        when(examSessionRepository.insert(any(ExamSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExamSessionManager.Assignment assignment = manager.findOrCreate(USER_ID);

        assertSelected(assignment, 2, 1);
        verify(examSessionRepository).backfillLegacyActiveCompletionIfUnchanged(
                legacy.getExamId(), completedAt);
    }

    @Test
    void legacyExplicitActiveSessionWithoutCompletionEvidenceIsReused() {
        ExamSession legacy = ExamSession.builder()
                .examId("ex_legacy_explicit_active_in_progress")
                .userId(USER_ID)
                .mockExamId("mock_exam_001")
                .createdAt(LocalDateTime.of(2024, 1, 1, 9, 0))
                .active(true)
                .cycleNumber(null)
                .build();
        when(examSessionRepository.findActiveOrLegacyCandidatesByUserId(USER_ID))
                .thenReturn(List.of(legacy));
        when(completionEvidenceService.findCompletionEvidence(legacy.getExamId(), legacy.getCreatedAt()))
                .thenReturn(noCompletionEvidence());
        when(mockExamCatalogService.getRequiredExam("mock_exam_001")).thenReturn(mockExam(1));

        ExamSessionManager.Assignment assignment = manager.findOrCreate(USER_ID);

        assertAll(
                () -> assertFalse(assignment.created()),
                () -> assertEquals(legacy.getExamId(), assignment.session().getExamId())
        );
        verify(examSessionRepository, never()).insert(any(ExamSession.class));
    }

    @Test
    void unsummarizedLegacySessionIsReusedAsInProgress() {
        ExamSession legacy = ExamSession.builder()
                .examId("ex_legacy_in_progress_001")
                .userId(USER_ID)
                .mockExamId("mock_exam_001")
                .build();
        when(examSessionRepository.findActiveOrLegacyCandidatesByUserId(USER_ID))
                .thenReturn(List.of(legacy));
        when(completionEvidenceService.findCompletionEvidence(legacy.getExamId(), legacy.getCreatedAt()))
                .thenReturn(noCompletionEvidence());
        when(mockExamCatalogService.getRequiredExam("mock_exam_001")).thenReturn(mockExam(1));

        ExamSessionManager.Assignment assignment = manager.findOrCreate(USER_ID);

        assertAll(
                () -> assertFalse(assignment.created()),
                () -> assertEquals(legacy.getExamId(), assignment.session().getExamId())
        );
        verify(examSessionRepository, never()).insert(any(ExamSession.class));
    }

    @Test
    void evidenceInBothCollectionsBackfillsAndCountsSessionOnlyOnce() {
        ExamSession legacy = ExamSession.builder()
                .examId("ex_legacy_both_evidence")
                .userId(USER_ID)
                .mockExamId("mock_exam_001")
                .build();
        LocalDateTime completedAt = LocalDateTime.of(2024, 1, 31, 6, 30);
        when(examSessionRepository.findActiveOrLegacyCandidatesByUserId(USER_ID))
                .thenReturn(List.of(legacy));
        when(completionEvidenceService.findCompletionEvidence(legacy.getExamId(), legacy.getCreatedAt()))
                .thenReturn(completionEvidence(completedAt, 1, 1));
        when(examSessionRepository.backfillLegacyCompletionIfUnchanged(legacy.getExamId(), completedAt))
                .thenReturn(1L);
        when(examSessionCompletionQuery.countCompletedByMockExamId(USER_ID)).thenReturn(List.of(
                new ExamSessionCompletionQuery.CompletionCount("mock_exam_001", 1)
        ));
        when(mockExamCatalogService.findAssignableExams()).thenReturn(catalog);
        when(examSessionRepository.insert(any(ExamSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExamSessionManager.Assignment assignment = manager.findOrCreate(USER_ID);

        assertSelected(assignment, 2, 1);
        verify(examSessionRepository, times(1))
                .backfillLegacyCompletionIfUnchanged(legacy.getExamId(), completedAt);
    }

    @Test
    void approximateSessionCreatedAtCanBackfillLegacyCompletion() {
        LocalDateTime sessionCreatedAt = LocalDateTime.of(2024, 1, 1, 9, 0);
        ExamSession legacy = ExamSession.builder()
                .examId("ex_approximate_completion")
                .userId(USER_ID)
                .mockExamId("mock_exam_001")
                .createdAt(sessionCreatedAt)
                .build();
        when(examSessionRepository.findActiveOrLegacyCandidatesByUserId(USER_ID))
                .thenReturn(List.of(legacy));
        when(completionEvidenceService.findCompletionEvidence(legacy.getExamId(), sessionCreatedAt))
                .thenReturn(new ExamCompletionEvidenceService.CompletionEvidence(
                        true,
                        sessionCreatedAt,
                        "exam_sessions.createdAt (approximate)",
                        true,
                        0,
                        1
                ));
        when(examSessionRepository.backfillLegacyCompletionIfUnchanged(legacy.getExamId(), sessionCreatedAt))
                .thenReturn(1L);
        when(examSessionCompletionQuery.countCompletedByMockExamId(USER_ID)).thenReturn(List.of(
                new ExamSessionCompletionQuery.CompletionCount("mock_exam_001", 1)
        ));
        when(mockExamCatalogService.findAssignableExams()).thenReturn(catalog);
        when(examSessionRepository.insert(any(ExamSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        manager.findOrCreate(USER_ID);

        verify(examSessionRepository).backfillLegacyCompletionIfUnchanged(
                legacy.getExamId(), sessionCreatedAt);
    }

    @Test
    void concurrentCreationReturnsOnePersistedActiveSession() throws Exception {
        Map<String, ExamSession> activeSessions = new ConcurrentHashMap<>();
        CountDownLatch initialLookups = new CountDownLatch(2);
        CountDownLatch releaseLookups = new CountDownLatch(1);
        AtomicInteger lookupCount = new AtomicInteger();
        when(examSessionRepository.findActiveOrLegacyCandidatesByUserId(USER_ID)).thenAnswer(invocation -> {
            if (lookupCount.incrementAndGet() <= 2) {
                initialLookups.countDown();
                assertTrue(releaseLookups.await(5, TimeUnit.SECONDS));
            }
            return new ArrayList<>(activeSessions.values());
        });
        when(examSessionCompletionQuery.countCompletedByMockExamId(USER_ID)).thenReturn(List.of());
        when(mockExamCatalogService.findAssignableExams()).thenReturn(catalog);
        when(mockExamCatalogService.getRequiredExam("mock_exam_001")).thenReturn(mockExam(1));
        when(examSessionRepository.insert(any(ExamSession.class))).thenAnswer(invocation -> {
            ExamSession candidate = invocation.getArgument(0);
            synchronized (activeSessions) {
                if (!activeSessions.isEmpty()) {
                    throw new DuplicateKeyException("partial unique active-user index");
                }
                activeSessions.put(candidate.getExamId(), candidate);
                return candidate;
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ExamSessionManager.Assignment> first = executor.submit(() -> manager.findOrCreate(USER_ID));
            Future<ExamSessionManager.Assignment> second = executor.submit(() -> manager.findOrCreate(USER_ID));
            assertTrue(initialLookups.await(5, TimeUnit.SECONDS));
            releaseLookups.countDown();

            ExamSessionManager.Assignment firstResult = first.get(5, TimeUnit.SECONDS);
            ExamSessionManager.Assignment secondResult = second.get(5, TimeUnit.SECONDS);

            assertAll(
                    () -> assertEquals(1, activeSessions.size()),
                    () -> assertEquals(firstResult.session().getExamId(), secondResult.session().getExamId()),
                    () -> assertTrue(firstResult.created() ^ secondResult.created())
            );
            verify(examSessionRepository, times(2)).insert(any(ExamSession.class));
        } finally {
            releaseLookups.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentLegacyCompletionBackfillUpdatesOnceAndDoesNotDoubleCount() throws Exception {
        LocalDateTime completedAt = LocalDateTime.of(2024, 1, 31, 6, 30);
        ExamSession legacy = ExamSession.builder()
                .examId("ex_concurrent_legacy_completion")
                .userId(USER_ID)
                .mockExamId("mock_exam_001")
                .createdAt(LocalDateTime.of(2024, 1, 1, 9, 0))
                .build();
        Map<String, ExamSession> activeSessions = new ConcurrentHashMap<>();
        CountDownLatch initialLookups = new CountDownLatch(2);
        CountDownLatch releaseLookups = new CountDownLatch(1);
        AtomicInteger lookupCount = new AtomicInteger();
        AtomicBoolean completionBackfilled = new AtomicBoolean();
        AtomicInteger successfulBackfills = new AtomicInteger();

        when(examSessionRepository.findActiveOrLegacyCandidatesByUserId(USER_ID)).thenAnswer(invocation -> {
            if (lookupCount.incrementAndGet() <= 2) {
                initialLookups.countDown();
                assertTrue(releaseLookups.await(5, TimeUnit.SECONDS));
                return List.of(legacy);
            }
            return new ArrayList<>(activeSessions.values());
        });
        when(completionEvidenceService.findCompletionEvidence(legacy.getExamId(), legacy.getCreatedAt()))
                .thenReturn(completionEvidence(completedAt, 0, 1));
        when(examSessionRepository.backfillLegacyCompletionIfUnchanged(legacy.getExamId(), completedAt))
                .thenAnswer(invocation -> {
                    if (completionBackfilled.compareAndSet(false, true)) {
                        successfulBackfills.incrementAndGet();
                        return 1L;
                    }
                    return 0L;
                });
        when(examSessionCompletionQuery.countCompletedByMockExamId(USER_ID)).thenReturn(List.of(
                new ExamSessionCompletionQuery.CompletionCount("mock_exam_001", 1)
        ));
        when(mockExamCatalogService.findAssignableExams()).thenReturn(catalog);
        when(mockExamCatalogService.getRequiredExam("mock_exam_002")).thenReturn(mockExam(2));
        when(examSessionRepository.insert(any(ExamSession.class))).thenAnswer(invocation -> {
            ExamSession candidate = invocation.getArgument(0);
            synchronized (activeSessions) {
                if (!activeSessions.isEmpty()) {
                    throw new DuplicateKeyException("partial unique active-user index");
                }
                activeSessions.put(candidate.getExamId(), candidate);
                return candidate;
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ExamSessionManager.Assignment> first = executor.submit(() -> manager.findOrCreate(USER_ID));
            Future<ExamSessionManager.Assignment> second = executor.submit(() -> manager.findOrCreate(USER_ID));
            assertTrue(initialLookups.await(5, TimeUnit.SECONDS));
            releaseLookups.countDown();

            ExamSessionManager.Assignment firstResult = first.get(5, TimeUnit.SECONDS);
            ExamSessionManager.Assignment secondResult = second.get(5, TimeUnit.SECONDS);

            assertAll(
                    () -> assertEquals(1, successfulBackfills.get()),
                    () -> assertEquals(1, activeSessions.size()),
                    () -> assertEquals(firstResult.session().getExamId(), secondResult.session().getExamId()),
                    () -> assertEquals("mock_exam_002", firstResult.session().getMockExamId()),
                    () -> assertEquals(1, firstResult.session().getCycleNumber())
            );
            verify(examSessionCompletionQuery, times(2)).countCompletedByMockExamId(USER_ID);
        } finally {
            releaseLookups.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void completionUsesClockAndChangesStateOnlyOnce() {
        AtomicReference<LocalDateTime> storedCompletion = new AtomicReference<>();
        AtomicBoolean active = new AtomicBoolean(true);
        when(examSessionRepository.completeIfIncomplete(eq("ex_complete_001"), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    if (!storedCompletion.compareAndSet(null, invocation.getArgument(1))) {
                        return 0L;
                    }
                    active.set(false);
                    return 1L;
                });

        boolean first = manager.completeIfIncomplete("ex_complete_001");
        boolean duplicate = manager.completeIfIncomplete("ex_complete_001");

        assertAll(
                () -> assertTrue(first),
                () -> assertFalse(duplicate),
                () -> assertFalse(active.get()),
                () -> assertEquals(LocalDateTime.of(2026, 7, 29, 6, 30), storedCompletion.get())
        );
    }

    private void stubNewAssignment(List<ExamSession> completed) {
        when(examSessionRepository.findActiveOrLegacyCandidatesByUserId(USER_ID)).thenReturn(List.of());
        when(examSessionCompletionQuery.countCompletedByMockExamId(USER_ID))
                .thenReturn(toCompletionCounts(completed));
        when(mockExamCatalogService.findAssignableExams()).thenReturn(catalog);
        when(examSessionRepository.insert(any(ExamSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static List<ExamSessionCompletionQuery.CompletionCount> toCompletionCounts(
            List<ExamSession> completed) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ExamSession session : completed) {
            counts.merge(GradingKeys.effectiveMockExamId(session.getMockExamId()), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new ExamSessionCompletionQuery.CompletionCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static void assertSelected(
            ExamSessionManager.Assignment assignment,
            int sequence,
            int cycleNumber) {
        ExamSession session = assignment.session();
        assertAll(
                () -> assertTrue(assignment.created()),
                () -> assertEquals("mock_exam_%03d".formatted(sequence), session.getMockExamId()),
                () -> assertEquals(cycleNumber, session.getCycleNumber()),
                () -> assertTrue(session.isActive()),
                () -> assertNull(session.getCompletedAt()),
                () -> assertEquals(USER_ID, session.getUserId()),
                () -> assertEquals(LocalDateTime.of(2026, 7, 29, 6, 30), session.getCreatedAt()),
                () -> assertTrue(session.getExamId().matches("^ex_[0-9a-f]{10}_0729_0630$"))
        );
    }

    private static MockExamCatalogService.CatalogExam catalogExam(int sequence) {
        return new MockExamCatalogService.CatalogExam(mockExam(sequence), sequence);
    }

    private static MockExam mockExam(int sequence) {
        return MockExam.builder()
                .mockExamId("mock_exam_%03d".formatted(sequence))
                .sequence(sequence)
                .active(true)
                .questions(List.of(Question.builder().questionNumber(1).build()))
                .build();
    }

    private static ExamSession completed(String userId, int sequence) {
        return ExamSession.builder()
                .examId("completed-" + userId + "-" + sequence + "-" + System.nanoTime())
                .userId(userId)
                .mockExamId("mock_exam_%03d".formatted(sequence))
                .active(false)
                .completedAt(LocalDateTime.of(2026, 7, 28, 12, 0))
                .build();
    }

    private static ExamCompletionEvidenceService.CompletionEvidence noCompletionEvidence() {
        return new ExamCompletionEvidenceService.CompletionEvidence(false, null, "none", false, 0, 0);
    }

    private static ExamCompletionEvidenceService.CompletionEvidence completionEvidence(
            LocalDateTime completedAt,
            int summaryCount,
            int legacyTotalScoreCount) {
        return new ExamCompletionEvidenceService.CompletionEvidence(
                true,
                completedAt,
                summaryCount > 0 ? "exam_summaries.createdAt" : "exam_results.createdAt",
                false,
                summaryCount,
                legacyTotalScoreCount
        );
    }
}
