package web.tosunsaeng.domain.exams.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.client.ResourceAccessException;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.entity.SummaryGradingJob;
import web.tosunsaeng.domain.exams.domain.enums.ExamSessionStatus;
import web.tosunsaeng.domain.exams.domain.enums.GradingJobStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamSessionRepository;
import web.tosunsaeng.domain.exams.domain.repository.SummaryGradingJobRepository;
import web.tosunsaeng.global.config.GradingProperties;

import java.time.Clock;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SummaryDispatchSchedulerTest {

    private static final String EXAM_ID = "ex_summary_scheduler_001";
    private static final String JOB_ID = "summary:" + EXAM_ID + ":v1";
    private static final Instant NOW = Instant.parse("2026-07-29T03:00:00Z");
    private static final GradingProperties PROPERTIES = new GradingProperties(
            Duration.ofMinutes(1),
            Duration.ofMinutes(3),
            3,
            URI.create("http://test-ai:8000"),
            Duration.ofSeconds(3),
            Duration.ofSeconds(30),
            2,
            100
    );

    @Mock
    private SummaryGradingJobRepository summaryJobRepository;

    @Mock
    private ExamSessionRepository examSessionRepository;

    @Mock
    private GradingDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        lenient().when(examSessionRepository.findById(EXAM_ID))
                .thenReturn(Optional.of(inProgressSession()));
    }

    @Test
    void duplicatePendingTasksDispatchSummaryOnlyOnce(CapturedOutput output) {
        CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
        AtomicReference<SummaryGradingJob> store = installRepositoryStore(
                SummaryGradingJob.pending(JOB_ID, EXAM_ID, "mock_exam_002", NOW)
        );
        SummaryDispatchScheduler scheduler = scheduler(taskExecutor);

        assertTrue(scheduler.schedulePending(JOB_ID));
        assertTrue(scheduler.schedulePending(JOB_ID));
        assertEquals(2, taskExecutor.taskCount());

        taskExecutor.runAll();

        ArgumentCaptor<SummaryDispatchClaim> claimCaptor =
                ArgumentCaptor.forClass(SummaryDispatchClaim.class);
        verify(dispatchService, times(1)).dispatchSummary(claimCaptor.capture());
        SummaryGradingJob stored = store.get();
        assertAll(
                () -> assertEquals(JOB_ID, claimCaptor.getValue().jobId()),
                () -> assertEquals(1, claimCaptor.getValue().dispatchAttempt()),
                () -> assertEquals("mock_exam_002", claimCaptor.getValue().mockExamId()),
                () -> assertEquals(GradingJobStatus.PROCESSING, stored.getStatus()),
                () -> assertEquals(1, stored.getDispatchAttempt()),
                () -> assertEquals(
                        1,
                        countOccurrences(
                                output.getOut(),
                                "요약 채점 요청 전송 완료 "
                                        + "event=grading.summary.dispatch outcome=success "
                                        + "jobId=" + JOB_ID
                        )
                )
        );
    }

    @Test
    void legacySummaryJobUsesSessionMockExamId() {
        CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
        installRepositoryStore(SummaryGradingJob.pending(JOB_ID, EXAM_ID, NOW));
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(ExamSession.builder()
                .examId(EXAM_ID)
                .mockExamId("mock_exam_002")
                .build()));
        SummaryDispatchScheduler scheduler = scheduler(taskExecutor);

        assertTrue(scheduler.schedulePending(JOB_ID));
        taskExecutor.runAll();

        ArgumentCaptor<SummaryDispatchClaim> claimCaptor =
                ArgumentCaptor.forClass(SummaryDispatchClaim.class);
        verify(dispatchService).dispatchSummary(claimCaptor.capture());
        assertEquals("mock_exam_002", claimCaptor.getValue().mockExamId());
    }

    @Test
    void summaryJobWithoutSessionIsNotDispatched() {
        CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
        AtomicReference<SummaryGradingJob> store = installRepositoryStore(
                SummaryGradingJob.pending(JOB_ID, EXAM_ID, NOW));
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.empty());
        SummaryDispatchScheduler scheduler = scheduler(taskExecutor);

        assertTrue(scheduler.schedulePending(JOB_ID));
        taskExecutor.runAll();

        assertAll(
                () -> assertEquals(GradingJobStatus.PENDING, store.get().getStatus()),
                () -> assertEquals(0, store.get().getDispatchAttempt())
        );
        verify(dispatchService, never()).dispatchSummary(any());
    }

    @Test
    void abandonedSummaryJobIsNotDispatched() {
        CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
        AtomicReference<SummaryGradingJob> store = installRepositoryStore(
                SummaryGradingJob.pending(JOB_ID, EXAM_ID, NOW));
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(Optional.of(abandonedSession()));
        SummaryDispatchScheduler scheduler = scheduler(taskExecutor);

        assertTrue(scheduler.schedulePending(JOB_ID));
        taskExecutor.runAll();

        assertAll(
                () -> assertEquals(GradingJobStatus.PENDING, store.get().getStatus()),
                () -> assertEquals(0, store.get().getDispatchAttempt())
        );
        verify(dispatchService, never()).dispatchSummary(any());
    }

    @Test
    void abandonmentAfterClaimFailsClaimWithoutDispatch() {
        CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
        AtomicReference<SummaryGradingJob> store = installRepositoryStore(
                SummaryGradingJob.pending(JOB_ID, EXAM_ID, "mock_exam_002", NOW));
        when(examSessionRepository.findById(EXAM_ID)).thenReturn(
                Optional.of(inProgressSession()),
                Optional.of(abandonedSession())
        );
        SummaryDispatchScheduler scheduler = scheduler(taskExecutor);

        assertTrue(scheduler.schedulePending(JOB_ID));
        taskExecutor.runAll();

        assertAll(
                () -> assertEquals(GradingJobStatus.FAILED, store.get().getStatus()),
                () -> assertEquals(1, store.get().getDispatchAttempt()),
                () -> assertEquals(SummaryDispatchScheduler.EXAM_ABANDONED, store.get().getFailureReason())
        );
        verify(summaryJobRepository).failClaimedAttempt(
                JOB_ID, 1, NOW, SummaryDispatchScheduler.EXAM_ABANDONED);
        verify(dispatchService, never()).dispatchSummary(any());
    }

    @Test
    void rejectedTaskLeavesPendingSummaryRecoverable(CapturedOutput output) {
        SummaryGradingJob pending = SummaryGradingJob.pending(JOB_ID, EXAM_ID, NOW);
        TaskExecutor rejectingExecutor = task -> {
            throw new TaskRejectedException("queue full");
        };
        SummaryDispatchScheduler scheduler = scheduler(rejectingExecutor);

        boolean scheduled = scheduler.schedulePending(JOB_ID);

        assertAll(
                () -> assertFalse(scheduled),
                () -> assertEquals(GradingJobStatus.PENDING, pending.getStatus()),
                () -> assertEquals(0, pending.getDispatchAttempt()),
                () -> assertTrue(output.getOut().contains(
                        "요약 채점 실행 예약 거절 "
                                + "event=grading.summary.schedule outcome=rejected reason=executor_rejected "
                                + "jobId=" + JOB_ID + " mode=PENDING_ONLY"
                ))
        );
        verifyNoInteractions(summaryJobRepository, dispatchService);
    }

    @Test
    void staleSummaryAttemptFailureDoesNotOverwriteNewerProcessingAttempt(
            CapturedOutput output) throws Exception {
        CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
        AtomicReference<SummaryGradingJob> store = installRepositoryStore(
                SummaryGradingJob.pending(JOB_ID, EXAM_ID, NOW)
        );
        MutableClock clock = new MutableClock(NOW);
        SummaryDispatchScheduler scheduler = scheduler(taskExecutor, clock);
        CountDownLatch attemptOneStarted = new CountDownLatch(1);
        CountDownLatch releaseAttemptOne = new CountDownLatch(1);
        doAnswer(invocation -> {
            SummaryDispatchClaim claim = invocation.getArgument(0);
            if (claim.dispatchAttempt() == 1) {
                attemptOneStarted.countDown();
                if (!releaseAttemptOne.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("attempt 1 was not released");
                }
                throw new ResourceAccessException("attempt 1 failed after attempt 2 was claimed");
            }
            return null;
        }).when(dispatchService).dispatchSummary(any(SummaryDispatchClaim.class));

        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            assertTrue(scheduler.schedulePending(JOB_ID));
            Future<?> attemptOne = worker.submit(taskExecutor.takeNext());
            assertTrue(attemptOneStarted.await(5, TimeUnit.SECONDS));

            Instant retryAt = NOW.plus(Duration.ofMinutes(3));
            clock.set(retryAt);
            assertTrue(scheduler.scheduleRetry(JOB_ID));
            taskExecutor.runAll();
            releaseAttemptOne.countDown();
            attemptOne.get(5, TimeUnit.SECONDS);

            SummaryGradingJob stored = store.get();
            assertAll(
                    () -> assertEquals(GradingJobStatus.PROCESSING, stored.getStatus()),
                    () -> assertEquals(2, stored.getDispatchAttempt()),
                    () -> assertNull(stored.getFailedAt()),
                    () -> assertNull(stored.getFailureReason()),
                    () -> assertFalse(output.getOut().contains(
                            "event=grading.summary.dispatch outcome=failure"
                    ))
            );
            verify(summaryJobRepository).failClaimedAttempt(
                    JOB_ID,
                    1,
                    retryAt,
                    SummaryDispatchScheduler.SUMMARY_DISPATCH_FAILED
            );
        } finally {
            releaseAttemptOne.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void summaryHttpTimeoutFailsOnlyTheClaimedAttempt(CapturedOutput output) {
        CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
        AtomicReference<SummaryGradingJob> store = installRepositoryStore(
                SummaryGradingJob.pending(JOB_ID, EXAM_ID, NOW.minusSeconds(61))
        );
        SummaryDispatchScheduler scheduler = scheduler(taskExecutor);
        doThrow(GradingDispatchException.at(
                GradingDispatchException.Stage.AI_POST,
                System.nanoTime(),
                new ResourceAccessException("read timed out at https://example.com?token=secret")
        ))
                .when(dispatchService).dispatchSummary(any(SummaryDispatchClaim.class));

        assertTrue(scheduler.schedulePending(JOB_ID));
        taskExecutor.runAll();

        SummaryGradingJob stored = store.get();
        assertAll(
                () -> assertEquals(GradingJobStatus.FAILED, stored.getStatus()),
                () -> assertEquals(1, stored.getDispatchAttempt()),
                () -> assertEquals(NOW, stored.getFailedAt()),
                () -> assertEquals(SummaryDispatchScheduler.SUMMARY_DISPATCH_FAILED, stored.getFailureReason()),
                () -> assertTrue(output.getOut().contains(
                        "요약 채점 요청 전송 실패 "
                                + "event=grading.summary.dispatch outcome=failure "
                                + "reason=SUMMARY_DISPATCH_FAILED jobId=" + JOB_ID
                )),
                () -> assertTrue(output.getOut().contains(
                        "stage=ai_post stageDurationMs="
                )),
                () -> assertTrue(output.getOut().contains(
                        "errorType=web.tosunsaeng.domain.exams.application.GradingDispatchException "
                                + "rootCauseType=org.springframework.web.client.ResourceAccessException"
                )),
                () -> assertFalse(output.getOut().contains("read timed out")),
                () -> assertFalse(output.getOut().contains("https://example.com")),
                () -> assertFalse(output.getOut().contains("token=secret"))
        );
        verify(summaryJobRepository).failClaimedAttempt(
                JOB_ID,
                1,
                NOW,
                SummaryDispatchScheduler.SUMMARY_DISPATCH_FAILED
        );
    }

    @Test
    void retryWorkerDoesNotExceedMaximumDispatchAttempts() {
        CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
        installRepositoryStore(summaryJob(
                GradingJobStatus.FAILED,
                PROPERTIES.maxDispatchAttempts(),
                NOW.minusSeconds(600),
                0L
        ));
        SummaryDispatchScheduler scheduler = scheduler(taskExecutor);

        assertTrue(scheduler.scheduleRetry(JOB_ID));
        taskExecutor.runAll();

        verify(summaryJobRepository, never()).save(any(SummaryGradingJob.class));
        verify(dispatchService, never()).dispatchSummary(any());
    }

    private SummaryDispatchScheduler scheduler(TaskExecutor taskExecutor) {
        return scheduler(taskExecutor, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ExamSession inProgressSession() {
        return ExamSession.builder()
                .examId(EXAM_ID)
                .mockExamId("mock_exam_002")
                .active(true)
                .status(ExamSessionStatus.IN_PROGRESS)
                .build();
    }

    private static ExamSession abandonedSession() {
        return ExamSession.builder()
                .examId(EXAM_ID)
                .mockExamId("mock_exam_002")
                .active(false)
                .status(ExamSessionStatus.ABANDONED)
                .build();
    }

    private SummaryDispatchScheduler scheduler(TaskExecutor taskExecutor, Clock clock) {
        return new SummaryDispatchScheduler(
                taskExecutor,
                summaryJobRepository,
                examSessionRepository,
                dispatchService,
                PROPERTIES,
                clock
        );
    }

    private AtomicReference<SummaryGradingJob> installRepositoryStore(SummaryGradingJob initial) {
        Long initialVersion = initial.getVersion() == null ? 0L : initial.getVersion();
        AtomicReference<SummaryGradingJob> store = new AtomicReference<>(copy(initial, initialVersion));
        lenient().when(summaryJobRepository.findById(JOB_ID))
                .thenAnswer(invocation -> Optional.of(copy(store.get(), store.get().getVersion())));
        lenient().when(summaryJobRepository.save(any(SummaryGradingJob.class)))
                .thenAnswer(invocation -> {
                    SummaryGradingJob candidate = invocation.getArgument(0);
                    SummaryGradingJob current = store.get();
                    if (!Objects.equals(candidate.getVersion(), current.getVersion())) {
                        throw new OptimisticLockingFailureException("summary job version conflict");
                    }
                    SummaryGradingJob saved = copy(candidate, current.getVersion() + 1);
                    store.set(saved);
                    return copy(saved, saved.getVersion());
                });
        lenient().when(summaryJobRepository.failClaimedAttempt(
                        eq(JOB_ID), anyInt(), any(Instant.class), anyString()))
                .thenAnswer(invocation -> {
                    int claimedAttempt = invocation.getArgument(1);
                    Instant failedAt = invocation.getArgument(2);
                    String reason = invocation.getArgument(3);
                    SummaryGradingJob current = store.get();
                    if (current.getStatus() != GradingJobStatus.PROCESSING
                            || current.getDispatchAttempt() != claimedAttempt) {
                        return 0L;
                    }
                    SummaryGradingJob failed = copy(current, current.getVersion());
                    failed.fail(failedAt, reason);
                    store.set(copy(failed, current.getVersion() + 1));
                    return 1L;
                });
        return store;
    }

    private static SummaryGradingJob summaryJob(
            GradingJobStatus status,
            int dispatchAttempt,
            Instant statusAt,
            Long version) {
        return SummaryGradingJob.builder()
                .jobId(JOB_ID)
                .examId(EXAM_ID)
                .summaryVersion(1)
                .status(status)
                .dispatchAttempt(dispatchAttempt)
                .pendingAt(statusAt)
                .processingStartedAt(status == GradingJobStatus.PROCESSING ? statusAt : null)
                .lastDispatchedAt(dispatchAttempt > 0 ? statusAt : null)
                .failedAt(status == GradingJobStatus.FAILED ? statusAt : null)
                .failureReason(status == GradingJobStatus.FAILED ? "TEST_FAILURE" : null)
                .version(version)
                .build();
    }

    private static SummaryGradingJob copy(SummaryGradingJob source, Long version) {
        return SummaryGradingJob.builder()
                .jobId(source.getJobId())
                .examId(source.getExamId())
                .mockExamId(source.getMockExamId())
                .summaryVersion(source.getSummaryVersion())
                .status(source.getStatus())
                .dispatchAttempt(source.getDispatchAttempt())
                .pendingAt(source.getPendingAt())
                .processingStartedAt(source.getProcessingStartedAt())
                .lastDispatchedAt(source.getLastDispatchedAt())
                .completedAt(source.getCompletedAt())
                .failedAt(source.getFailedAt())
                .failureReason(source.getFailureReason())
                .version(version)
                .build();
    }

    private static final class CapturingTaskExecutor implements TaskExecutor {

        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        int taskCount() {
            return tasks.size();
        }

        void runAll() {
            List<Runnable> scheduled = new ArrayList<>(tasks);
            tasks.clear();
            scheduled.forEach(Runnable::run);
        }

        Runnable takeNext() {
            return tasks.removeFirst();
        }
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;

        private MutableClock(Instant initialInstant) {
            instant = new AtomicReference<>(initialInstant);
        }

        void set(Instant newInstant) {
            instant.set(newInstant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    private static int countOccurrences(String source, String target) {
        return source.split(java.util.regex.Pattern.quote(target), -1).length - 1;
    }
}
