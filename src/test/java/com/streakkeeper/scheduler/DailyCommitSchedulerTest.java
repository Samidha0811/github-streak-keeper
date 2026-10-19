package com.streakkeeper.scheduler;

import com.streakkeeper.config.StreakKeeperProperties;
import com.streakkeeper.model.JobResult;
import com.streakkeeper.service.StreakKeeperWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.mockito.Mockito.*;

class DailyCommitSchedulerTest {

    private StreakKeeperProperties properties;
    private StreakKeeperWorkflowService workflowService;
    private DailyCommitScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new StreakKeeperProperties();
        workflowService = mock(StreakKeeperWorkflowService.class);
        scheduler = new DailyCommitScheduler(properties, workflowService);
    }

    @Test
    @DisplayName("12. Scheduler execution invokes the correct service when enabled")
    void testSchedulerInvokesWorkflowWhenEnabled() {
        properties.getScheduler().setEnabled(true);
        when(workflowService.executeWorkflow(false))
                .thenReturn(JobResult.noChange(LocalDate.now(), 10, "No change"));

        scheduler.runScheduledDailyCommit();

        verify(workflowService, times(1)).executeWorkflow(false);
    }

    @Test
    @DisplayName("Scheduler skips execution when disabled by configuration")
    void testSchedulerSkipsWhenDisabled() {
        properties.getScheduler().setEnabled(false);

        scheduler.runScheduledDailyCommit();

        verify(workflowService, never()).executeWorkflow(anyBoolean());
    }
}
