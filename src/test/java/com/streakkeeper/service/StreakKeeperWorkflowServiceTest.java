package com.streakkeeper.service;

import com.streakkeeper.model.DailyActivity;
import com.streakkeeper.model.JobResult;
import com.streakkeeper.model.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreakKeeperWorkflowServiceTest {

    private ActivityLogService activityLogService;
    private GitCommitService gitCommitService;
    private StreakKeeperWorkflowService workflowService;
    private final LocalDate testDate = LocalDate.of(2026, 10, 19);

    @BeforeEach
    void setUp() {
        activityLogService = mock(ActivityLogService.class);
        gitCommitService = mock(GitCommitService.class);
        workflowService = new StreakKeeperWorkflowService(activityLogService, gitCommitService);

        when(activityLogService.getCurrentDate()).thenReturn(testDate);
    }

    @Test
    @DisplayName("Workflow coordinates activity logging and Git commit/push")
    void testWorkflowCoordinatesExecution() throws IOException {
        DailyActivity activity = new DailyActivity(testDate, "Daily execution");
        when(activityLogService.recordDailyActivity())
                .thenReturn(new ActivityLogService.ActivityLogResult(true, activity, Paths.get("daily-log.md"), "created"));

        when(gitCommitService.stageCommitAndPush(testDate))
                .thenReturn(JobResult.success(testDate, "hash123", 50, "Success"));

        JobResult result = workflowService.executeWorkflow(false);

        assertEquals(JobStatus.COMMITTED_AND_PUSHED, result.getStatus());
        verify(activityLogService).recordDailyActivity();
        verify(gitCommitService).stageCommitAndPush(testDate);
    }

    @Test
    @DisplayName("13. Overlapping executions are rejected by lock")
    void testOverlappingExecutionsRejected() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(1);

        when(activityLogService.recordDailyActivity()).thenAnswer(invocation -> {
            startLatch.countDown();
            finishLatch.await(); // Hold execution
            return new ActivityLogService.ActivityLogResult(true, new DailyActivity(testDate, "test"), Paths.get(""), "created");
        });
        when(gitCommitService.stageCommitAndPush(any())).thenReturn(JobResult.success(testDate, "hash", 10, "ok"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<JobResult> firstCall = executor.submit(() -> workflowService.executeWorkflow(false));

            startLatch.await(); // Wait until first call is inside workflow

            // Second concurrent call must be rejected immediately
            JobResult secondCall = workflowService.executeWorkflow(true);
            assertEquals(JobStatus.ALREADY_RUNNING, secondCall.getStatus());

            finishLatch.countDown(); // Let first finish
            JobResult firstResult = firstCall.get();
            assertEquals(JobStatus.COMMITTED_AND_PUSHED, firstResult.getStatus());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("Exceptions during workflow return failure result rather than crashing")
    void testExceptionsHandledGracefully() throws IOException {
        when(activityLogService.recordDailyActivity())
                .thenThrow(new IOException("Disk I/O error"));

        JobResult result = workflowService.executeWorkflow(false);

        assertEquals(JobStatus.FAILED, result.getStatus());
        assertFalse(result.isSuccessful());
        assertTrue(result.getMessage().contains("Workflow error: Disk I/O error"));
    }
}
