package com.streakkeeper.service;

import com.streakkeeper.config.StreakKeeperProperties;
import com.streakkeeper.model.JobResult;
import com.streakkeeper.model.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class GitCommitServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private Path localRepoDir;
    private Path remoteRepoDir;
    private StreakKeeperProperties properties;
    private DefaultGitProcessRunner processRunner;
    private GitCommitService gitCommitService;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        localRepoDir = tempDir.resolve("local-repo");
        remoteRepoDir = tempDir.resolve("remote-repo.git");
        Files.createDirectories(localRepoDir);

        processRunner = new DefaultGitProcessRunner();

        // 1. Initialize bare remote repository
        executeProcess(tempDir.toFile(), "git", "init", "--bare", remoteRepoDir.toString());

        // 2. Initialize local repository
        executeProcess(localRepoDir.toFile(), "git", "init", "-b", "main");
        executeProcess(localRepoDir.toFile(), "git", "config", "user.name", "Integration Tester");
        executeProcess(localRepoDir.toFile(), "git", "config", "user.email", "test@example.com");
        executeProcess(localRepoDir.toFile(), "git", "remote", "add", "origin", remoteRepoDir.toUri().toString());

        // Configure properties
        properties = new StreakKeeperProperties();
        properties.getGit().setRepositoryPath(localRepoDir.toString());
        properties.getGit().setRemote("origin");
        properties.getGit().setBranch("main");
        properties.getGit().setActivityFile("activity/daily-log.md");
        properties.getGit().setTimeoutSeconds(15);

        gitCommitService = new GitCommitService(properties, processRunner);
    }

    private void executeProcess(File dir, String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).directory(dir).start();
        boolean finished = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(finished, "Command " + String.join(" ", cmd) + " timed out");
        assertEquals(0, p.exitValue(), "Command failed: " + String.join(" ", cmd));
    }

    @Test
    @DisplayName("End-to-end integration: validates repo, stages activity, commits, and pushes to local bare remote")
    void testEndToEndGitWorkflowWithLocalRemote() throws IOException {
        LocalDate testDate = LocalDate.of(2026, 10, 19);

        // Prepare activity file
        Path activityFile = localRepoDir.resolve("activity/daily-log.md");
        Files.createDirectories(activityFile.getParent());
        Files.writeString(activityFile, "# Daily Activity\n\n- 2026-10-19: Integration test execution\n", StandardCharsets.UTF_8);

        // Execute stage, commit, and push
        JobResult result = gitCommitService.stageCommitAndPush(testDate);

        assertEquals(JobStatus.COMMITTED_AND_PUSHED, result.getStatus(), "Expected commit and push to succeed: " + result.getMessage());
        assertNotNull(result.getCommitHash());
        assertFalse(result.getCommitHash().isBlank());
        assertTrue(result.isSuccessful());

        // Verify remote repository received the commit
        GitProcessResult remoteLog = processRunner.run(remoteRepoDir.toFile(), 10, "git", "log", "-1", "--oneline");
        assertTrue(remoteLog.isSuccess());
        assertTrue(remoteLog.stdout().contains("chore: record daily activity 2026-10-19"));

        // Second run without modifying activity file -> should detect no change
        JobResult secondResult = gitCommitService.stageCommitAndPush(testDate);
        assertEquals(JobStatus.NO_CHANGE, secondResult.getStatus());
        assertTrue(secondResult.isSuccessful());
    }
}
