package com.streakkeeper.service;

import com.streakkeeper.config.StreakKeeperProperties;
import com.streakkeeper.model.JobResult;
import com.streakkeeper.model.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
public class GitCommitService {

    private static final Logger log = LoggerFactory.getLogger(GitCommitService.class);

    private final StreakKeeperProperties properties;
    private final GitProcessRunner processRunner;

    public GitCommitService(StreakKeeperProperties properties, GitProcessRunner processRunner) {
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner cannot be null");
    }

    public File getRepositoryDirectory() {
        Path repoPath = Paths.get(properties.getGit().getRepositoryPath()).toAbsolutePath().normalize();
        return repoPath.toFile();
    }

    /**
     * Validates that the configured directory is a Git repository, the remote exists,
     * and the target branch is valid.
     */
    public RepositoryValidationResult validateRepository() {
        File repoDir = getRepositoryDirectory();
        if (!repoDir.exists() || !repoDir.isDirectory()) {
            return new RepositoryValidationResult(false, "Configured repository path does not exist: " + repoDir.getAbsolutePath());
        }

        long timeout = properties.getGit().getTimeoutSeconds();

        // 1. Verify inside work tree
        GitProcessResult revParse = processRunner.run(repoDir, timeout, "git", "rev-parse", "--is-inside-work-tree");
        if (!revParse.isSuccess()) {
            return new RepositoryValidationResult(false, "Directory is not a valid Git repository: " + repoDir.getAbsolutePath());
        }

        // 2. Verify remote exists
        String remote = properties.getGit().getRemote();
        GitProcessResult remoteCheck = processRunner.run(repoDir, timeout, "git", "remote", "get-url", remote);
        if (!remoteCheck.isSuccess()) {
            return new RepositoryValidationResult(false, "Configured remote '" + remote + "' was not found in repository");
        }

        return new RepositoryValidationResult(true, "Repository and remote '" + remote + "' are valid");
    }

    /**
     * Stages the activity file, commits if changes exist, and pushes to the configured remote.
     *
     * @param targetDate the execution date
     * @return JobResult reflecting the outcome
     */
    public JobResult stageCommitAndPush(LocalDate targetDate) {
        long startTime = System.currentTimeMillis();
        File repoDir = getRepositoryDirectory();
        long timeout = properties.getGit().getTimeoutSeconds();
        String remote = properties.getGit().getRemote();
        String branch = properties.getGit().getBranch();
        String activityFile = properties.getGit().getActivityFile();

        log.info("Starting Git workflow for date {} in repo [{}]", targetDate, repoDir.getAbsolutePath());

        // Step 1: Validate repository
        RepositoryValidationResult validation = validateRepository();
        if (!validation.isValid()) {
            log.error("Repository validation failed: {}", validation.message());
            long duration = System.currentTimeMillis() - startTime;
            return JobResult.failure(targetDate, JobStatus.INVALID_REPOSITORY, validation.message(), null, duration);
        }

        // Step 2: Stage ONLY the specified activity file
        log.info("Staging activity file: {}", activityFile);
        GitProcessResult addResult = processRunner.run(repoDir, timeout, "git", "add", activityFile);
        if (addResult.timedOut()) {
            long duration = System.currentTimeMillis() - startTime;
            return JobResult.failure(targetDate, JobStatus.TIMEOUT, "Git add timed out", addResult.stderr(), duration);
        }
        if (!addResult.isSuccess()) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to stage activity file: {}", addResult.stderr());
            return JobResult.failure(targetDate, JobStatus.FAILED, "Failed to stage " + activityFile, addResult.stderr(), duration);
        }

        // Step 3: Check whether there are actual cached changes to commit
        // git diff --cached --quiet returns exit code 0 if NO differences, 1 if differences exist
        GitProcessResult diffResult = processRunner.run(repoDir, timeout, "git", "diff", "--cached", "--quiet", activityFile);
        if (diffResult.timedOut()) {
            long duration = System.currentTimeMillis() - startTime;
            return JobResult.failure(targetDate, JobStatus.TIMEOUT, "Git diff check timed out", diffResult.stderr(), duration);
        }

        if (diffResult.exitCode() == 0) {
            long duration = System.currentTimeMillis() - startTime;
            log.info("No staged changes detected for {}. Skipping commit and push.", activityFile);
            return JobResult.noChange(targetDate, duration, "No staged changes detected for " + activityFile);
        }

        // Step 4: Commit the change
        String commitMsg = "chore: record daily activity " + targetDate;
        log.info("Committing changes with message: '{}'", commitMsg);

        List<String> commitCmd = new ArrayList<>();
        commitCmd.add("git");

        // If author fallback is configured, pass author config flags if needed
        String authorName = properties.getGit().getAuthorName();
        String authorEmail = properties.getGit().getAuthorEmail();
        if (authorName != null && !authorName.isBlank()) {
            commitCmd.add("-c");
            commitCmd.add("user.name=" + authorName);
        }
        if (authorEmail != null && !authorEmail.isBlank()) {
            commitCmd.add("-c");
            commitCmd.add("user.email=" + authorEmail);
        }

        commitCmd.addAll(Arrays.asList("commit", "-m", commitMsg, "--", activityFile));

        GitProcessResult commitResult = processRunner.run(repoDir, timeout, commitCmd.toArray(new String[0]));
        if (commitResult.timedOut()) {
            long duration = System.currentTimeMillis() - startTime;
            return JobResult.failure(targetDate, JobStatus.TIMEOUT, "Git commit timed out", commitResult.stderr(), duration);
        }
        if (!commitResult.isSuccess()) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Git commit failed: {}", commitResult.stderr());
            return JobResult.failure(targetDate, JobStatus.FAILED, "Git commit failed: " + commitResult.stderr(), commitResult.stderr(), duration);
        }

        // Extract commit hash
        GitProcessResult revParseHead = processRunner.run(repoDir, timeout, "git", "rev-parse", "HEAD");
        String commitHash = revParseHead.isSuccess() ? revParseHead.stdout().trim() : "unknown";
        log.info("Created commit {} for date {}", commitHash, targetDate);

        // Step 5: Push to remote
        log.info("Pushing commit {} to remote [{}] branch [{}]", commitHash, remote, branch);
        GitProcessResult pushResult = processRunner.run(repoDir, timeout, "git", "push", remote, branch);
        long duration = System.currentTimeMillis() - startTime;

        if (pushResult.timedOut()) {
            log.error("Git push timed out after {}s for commit {}", timeout, commitHash);
            return JobResult.commitSucceededPushFailed(targetDate, JobStatus.TIMEOUT, commitHash,
                    "Git push timed out after " + timeout + "s", pushResult.stderr(), duration);
        }

        if (pushResult.isSuccess()) {
            log.info("Successfully pushed commit {} to {}/{} in {}ms", commitHash, remote, branch, duration);
            return JobResult.success(targetDate, commitHash, duration, "Successfully recorded, committed, and pushed daily activity");
        }

        // Step 6: Diagnose push failure
        String errorOutput = pushResult.stderr() + "\n" + pushResult.stdout();
        JobStatus failureStatus = diagnosePushFailure(errorOutput);
        String diagnostics = buildDiagnosticMessage(failureStatus, errorOutput, remote, branch);

        log.error("Push failed for commit {}. Diagnosed as: {}. Details:\n{}", commitHash, failureStatus, diagnostics);
        return JobResult.commitSucceededPushFailed(targetDate, failureStatus, commitHash, diagnostics, errorOutput, duration);
    }

    private JobStatus diagnosePushFailure(String output) {
        String lower = output.toLowerCase();
        if (lower.contains("permission denied")
                || lower.contains("publickey")
                || lower.contains("could not read username")
                || lower.contains("authentication failed")
                || lower.contains("invalid username or password")) {
            return JobStatus.AUTHENTICATION_ERROR;
        }
        if (lower.contains("could not resolve host")
                || lower.contains("connection timed out")
                || lower.contains("failed to connect")
                || lower.contains("network is unreachable")
                || lower.contains("ssh: connect to host")) {
            return JobStatus.NETWORK_ERROR;
        }
        if (lower.contains("[rejected]")
                || lower.contains("fetch first")
                || lower.contains("non-fast-forward")
                || lower.contains("updates were rejected")) {
            return JobStatus.REMOTE_DIVERGED;
        }
        return JobStatus.COMMIT_SUCCEEDED_PUSH_FAILED;
    }

    private String buildDiagnosticMessage(JobStatus status, String errorOutput, String remote, String branch) {
        return switch (status) {
            case AUTHENTICATION_ERROR ->
                    "Authentication failed while pushing to " + remote + "/" + branch +
                            ". Ensure SSH key is added to GitHub (`ssh -T git@github.com`) or Git credentials are configured.";
            case NETWORK_ERROR ->
                    "Network error occurred while connecting to remote " + remote +
                            ". Verify internet connectivity and DNS resolution.";
            case REMOTE_DIVERGED ->
                    "Push rejected because remote has diverged: branch " + branch + " contains commits not present locally. " +
                            "A manual fast-forward pull or rebase is required.";
            default ->
                    "Git push to " + remote + "/" + branch + " failed. Inspect stderr details for root cause.";
        };
    }

    public record RepositoryValidationResult(boolean isValid, String message) {
    }
}
