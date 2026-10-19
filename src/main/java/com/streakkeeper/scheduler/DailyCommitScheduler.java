package com.streakkeeper.scheduler;

import com.streakkeeper.config.StreakKeeperProperties;
import com.streakkeeper.model.JobResult;
import com.streakkeeper.service.StreakKeeperWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class DailyCommitScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyCommitScheduler.class);

    private final StreakKeeperProperties properties;
    private final StreakKeeperWorkflowService workflowService;

    public DailyCommitScheduler(StreakKeeperProperties properties, StreakKeeperWorkflowService workflowService) {
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
        this.workflowService = Objects.requireNonNull(workflowService, "workflowService cannot be null");
    }

    /**
     * Executes the daily commit workflow according to the configured cron schedule and time zone.
     * Default: 9:00 AM Indian Standard Time (Asia/Kolkata).
     */
    @Scheduled(cron = "${streak-keeper.scheduler.cron:0 0 9 * * *}", zone = "${streak-keeper.scheduler.zone:Asia/Kolkata}")
    public void runScheduledDailyCommit() {
        if (!properties.getScheduler().isEnabled()) {
            log.info("DailyCommitScheduler is disabled by configuration (streak-keeper.scheduler.enabled=false). Skipping execution.");
            return;
        }

        log.info("DailyCommitScheduler triggered by scheduled cron [{}] in zone [{}]",
                properties.getScheduler().getCron(), properties.getScheduler().getZone());

        JobResult result = workflowService.executeWorkflow(false);
        log.info("Scheduled job execution completed with status: {}", result.getStatus());
    }
}
