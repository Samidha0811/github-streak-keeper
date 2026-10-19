package com.streakkeeper.controller;

import com.streakkeeper.config.StreakKeeperProperties;
import com.streakkeeper.model.JobResult;
import com.streakkeeper.model.JobStatus;
import com.streakkeeper.service.GitCommitService;
import com.streakkeeper.service.StreakKeeperWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api")
public class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final StreakKeeperProperties properties;
    private final StreakKeeperWorkflowService workflowService;
    private final GitCommitService gitCommitService;

    public JobController(StreakKeeperProperties properties,
                         StreakKeeperWorkflowService workflowService,
                         GitCommitService gitCommitService) {
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
        this.workflowService = Objects.requireNonNull(workflowService, "workflowService cannot be null");
        this.gitCommitService = Objects.requireNonNull(gitCommitService, "gitCommitService cannot be null");
    }

    /**
     * Basic health check and configuration summary endpoint.
     * Never exposes credentials, tokens, or private paths.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> healthInfo = new LinkedHashMap<>();
        healthInfo.put("status", "UP");
        healthInfo.put("application", "github-streak-keeper");
        healthInfo.put("schedulerEnabled", properties.getScheduler().isEnabled());
        healthInfo.put("cron", properties.getScheduler().getCron());
        healthInfo.put("zone", properties.getScheduler().getZone());
        healthInfo.put("remote", properties.getGit().getRemote());
        healthInfo.put("branch", properties.getGit().getBranch());
        healthInfo.put("isJobRunning", workflowService.isCurrentlyRunning());

        GitCommitService.RepositoryValidationResult repoValidation = gitCommitService.validateRepository();
        healthInfo.put("repositoryValid", repoValidation.isValid());

        return ResponseEntity.ok(healthInfo);
    }

    /**
     * Manual trigger for the daily commit workflow.
     * Protected by optional API key header 'X-API-KEY'.
     */
    @PostMapping("/jobs/daily-commit")
    public ResponseEntity<?> triggerDailyCommit(
            @RequestHeader(value = "X-API-KEY", required = false) String providedApiKey) {

        String configuredKey = properties.getApi().getKey();
        if (configuredKey != null && !configuredKey.isBlank()) {
            if (providedApiKey == null || !constantTimeEquals(configuredKey, providedApiKey)) {
                log.warn("Unauthorized attempt to trigger daily commit endpoint.");
                Map<String, String> error = Map.of(
                        "error", "Unauthorized",
                        "message", "Invalid or missing X-API-KEY header"
                );
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }
        }

        log.info("Manual trigger received for daily commit workflow.");
        JobResult result = workflowService.executeWorkflow(true);

        if (result.getStatus() == JobStatus.ALREADY_RUNNING) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
        }

        if (result.getStatus() == JobStatus.FAILED || result.getStatus() == JobStatus.INVALID_REPOSITORY) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Constant-time string comparison to prevent timing attack side-channels.
     */
    private boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}
