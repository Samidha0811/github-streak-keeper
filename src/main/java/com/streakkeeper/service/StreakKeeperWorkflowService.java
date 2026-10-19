package com.streakkeeper.service;

import com.streakkeeper.model.JobResult;
import com.streakkeeper.model.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class StreakKeeperWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(StreakKeeperWorkflowService.class);

    private final ActivityLogService activityLogService;
    private final GitCommitService gitCommitService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public StreakKeeperWorkflowService(ActivityLogService activityLogService, GitCommitService gitCommitService) {
        this.activityLogService = Objects.requireNonNull(activityLogService, "activityLogService cannot be null");
        this.gitCommitService = Objects.requireNonNull(gitCommitService, "gitCommitService cannot be null");
    }

    public JobResult executeWorkflow(boolean isManual) {
        LocalDate currentDate = activityLogService.getCurrentDate();

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Job trigger rejected: an existing streak keeper workflow is already running for date {}", currentDate);
            return JobResult.alreadyRunning(currentDate);
        }

        long startTime = System.currentTimeMillis();
        String triggerType = isManual ? "MANUAL" : "SCHEDULED";
        log.info("========== Starting {} GitHub Streak Keeper Job for Date [{}] ==========", triggerType, currentDate);

        try {
            // Step 1: Record daily activity log entry
            ActivityLogService.ActivityLogResult logResult = activityLogService.recordDailyActivity();

            if (!logResult.created()) {
                log.info("Activity entry for date {} already exists in log. Checking Git status for pending changes.", currentDate);
            }

            // Step 2: Stage, commit, and push to Git
            JobResult gitResult = gitCommitService.stageCommitAndPush(currentDate);

            long totalDuration = System.currentTimeMillis() - startTime;
            log.info("========== Completed {} Job for [{}] with status [{}] in {}ms ==========",
                    triggerType, currentDate, gitResult.getStatus(), totalDuration);

            return gitResult;

        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - startTime;
            log.error("Unhandled error during {} streak keeper workflow execution for date {}", triggerType, currentDate, e);
            return JobResult.failure(currentDate, JobStatus.FAILED, "Workflow error: " + e.getMessage(), e.toString(), totalDuration);
        } finally {
            isRunning.set(false);
        }
    }

    public boolean isCurrentlyRunning() {
        return isRunning.get();
    }
}
