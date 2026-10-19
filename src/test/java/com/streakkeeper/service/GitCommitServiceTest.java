package com.streakkeeper.service;

import com.streakkeeper.config.StreakKeeperProperties;
import com.streakkeeper.model.JobResult;
import com.streakkeeper.model.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GitCommitServiceTest {

    @TempDir
    Path tempDir;

    private StreakKeeperProperties properties;
    private GitProcessRunner processRunner;
    private GitCommitService gitCommitService;
    private final LocalDate testDate = LocalDate.of(2026, 10, 19);

    @BeforeEach
    void setUp() {
        properties = new StreakKeeperProperties();
        properties.getGit().setRepositoryPath(tempDir.toString());
        properties.getGit().setRemote("origin");
        properties.getGit().setBranch("main");
        properties.getGit().setActivityFile("activity/daily-log.md");
        properties.getGit().setTimeoutSeconds(30);

        processRunner = mock(GitProcessRunner.class);
        gitCommitService = new GitCommitService(properties, processRunner);
    }

    private void mockValidRepository() {
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("rev-parse"), eq("--is-inside-work-tree")))
                .thenReturn(GitProcessResult.success("true"));
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("remote"), eq("get-url"), eq("origin")))
                .thenReturn(GitProcessResult.success("https://github.com/Samidha0811/github-streak-keeper.git"));
    }

    @Test
    @DisplayName("7. Only the intended activity file is staged")
    void testOnlyIntendedFileIsStaged() {
        mockValidRepository();

        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("add"), anyString()))
                .thenReturn(GitProcessResult.success(""));
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("diff"), eq("--cached"), eq("--quiet"), anyString()))
                .thenReturn(GitProcessResult.success("")); // no change

        gitCommitService.stageCommitAndPush(testDate);

        // Verify git add was called strictly with "activity/daily-log.md"
        verify(processRunner).run(any(File.class), anyLong(), eq("git"), eq("add"), eq("activity/daily-log.md"));
        // Verify never called git add with "." or "-A"
        verify(processRunner, never()).run(any(File.class), anyLong(), eq("git"), eq("add"), eq("."));
        verify(processRunner, never()).run(any(File.class), anyLong(), eq("git"), eq("add"), eq("-A"));
    }

    @Test
    @DisplayName("6. No empty commit is attempted when no staged changes exist")
    void testNoEmptyCommitAttemptedWhenNoChanges() {
        mockValidRepository();

        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("add"), eq("activity/daily-log.md")))
                .thenReturn(GitProcessResult.success(""));
        // git diff --cached --quiet returns 0 when no changes are staged
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("diff"), eq("--cached"), eq("--quiet"), eq("activity/daily-log.md")))
                .thenReturn(GitProcessResult.success(""));

        JobResult result = gitCommitService.stageCommitAndPush(testDate);

        assertEquals(JobStatus.NO_CHANGE, result.getStatus());
        assertTrue(result.isSuccessful());
        // Verify git commit was never invoked
        verify(processRunner, never()).run(any(File.class), anyLong(), argThat(args -> {
            List<String> list = List.of(args);
            return list.contains("commit");
        }));
    }

    @Test
    @DisplayName("5. A Git commit is created and pushed when the tracked file changes")
    void testCommitAndPushWhenChangesExist() {
        mockValidRepository();

        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("add"), eq("activity/daily-log.md")))
                .thenReturn(GitProcessResult.success(""));
        // git diff --cached --quiet returns exit code 1 when differences exist
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("diff"), eq("--cached"), eq("--quiet"), eq("activity/daily-log.md")))
                .thenReturn(GitProcessResult.failure(1, "", ""));

        // git commit succeeds
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("commit"), eq("-m"), anyString(), eq("--"), eq("activity/daily-log.md")))
                .thenReturn(GitProcessResult.success("[main abc1234] chore: record daily activity 2026-10-19"));

        // git rev-parse HEAD
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("rev-parse"), eq("HEAD")))
                .thenReturn(GitProcessResult.success("abc1234def5678"));

        // git push succeeds
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("push"), eq("origin"), eq("main")))
                .thenReturn(GitProcessResult.success("To https://github.com/Samidha0811/github-streak-keeper.git\n   1234..abc12 main -> main"));

        JobResult result = gitCommitService.stageCommitAndPush(testDate);

        assertEquals(JobStatus.COMMITTED_AND_PUSHED, result.getStatus());
        assertEquals("abc1234def5678", result.getCommitHash());
        assertTrue(result.isSuccessful());
    }

    @Test
    @DisplayName("8 & 9. A failed push is not reported as success")
    void testFailedPushNotReportedAsSuccess() {
        mockValidRepository();

        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("add"), eq("activity/daily-log.md")))
                .thenReturn(GitProcessResult.success(""));
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("diff"), eq("--cached"), eq("--quiet"), eq("activity/daily-log.md")))
                .thenReturn(GitProcessResult.failure(1, "", ""));
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("commit"), eq("-m"), anyString(), eq("--"), eq("activity/daily-log.md")))
                .thenReturn(GitProcessResult.success(""));
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("rev-parse"), eq("HEAD")))
                .thenReturn(GitProcessResult.success("abc1234"));

        // Push fails with general error
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("push"), eq("origin"), eq("main")))
                .thenReturn(GitProcessResult.failure(1, "", "fatal: remote error occurred"));

        JobResult result = gitCommitService.stageCommitAndPush(testDate);

        assertNotEquals(JobStatus.COMMITTED_AND_PUSHED, result.getStatus());
        assertEquals(JobStatus.COMMIT_SUCCEEDED_PUSH_FAILED, result.getStatus());
        assertFalse(result.isSuccessful());
        assertEquals("abc1234", result.getCommitHash());
    }

    @Test
    @DisplayName("Authentication failure during push is accurately categorized")
    void testAuthenticationFailureCategorized() {
        mockValidRepository();

        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("add"), eq("activity/daily-log.md")))
                .thenReturn(GitProcessResult.success(""));
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("diff"), eq("--cached"), eq("--quiet"), eq("activity/daily-log.md")))
                .thenReturn(GitProcessResult.failure(1, "", ""));
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("commit"), eq("-m"), anyString(), eq("--"), eq("activity/daily-log.md")))
                .thenReturn(GitProcessResult.success(""));
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("rev-parse"), eq("HEAD")))
                .thenReturn(GitProcessResult.success("abc1234"));

        // Push fails with authentication error
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("push"), eq("origin"), eq("main")))
                .thenReturn(GitProcessResult.failure(128, "", "git@github.com: Permission denied (publickey)."));

        JobResult result = gitCommitService.stageCommitAndPush(testDate);

        assertEquals(JobStatus.AUTHENTICATION_ERROR, result.getStatus());
        assertFalse(result.isSuccessful());
        assertTrue(result.getMessage().contains("Authentication failed"));
    }

    @Test
    @DisplayName("Network failure during push is accurately categorized")
    void testNetworkFailureCategorized() {
        mockValidRepository();

        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("add"), eq("activity/daily-log.md")))
                .thenReturn(GitProcessResult.success(""));
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("diff"), eq("--cached"), eq("--quiet"), eq("activity/daily-log.md")))
                .thenReturn(GitProcessResult.failure(1, "", ""));
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("commit"), eq("-m"), anyString(), eq("--"), eq("activity/daily-log.md")))
                .thenReturn(GitProcessResult.success(""));
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("rev-parse"), eq("HEAD")))
                .thenReturn(GitProcessResult.success("abc1234"));

        // Push fails with network error
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("push"), eq("origin"), eq("main")))
                .thenReturn(GitProcessResult.failure(128, "", "fatal: Could not resolve host: github.com"));

        JobResult result = gitCommitService.stageCommitAndPush(testDate);

        assertEquals(JobStatus.NETWORK_ERROR, result.getStatus());
        assertFalse(result.isSuccessful());
        assertTrue(result.getMessage().contains("Network error"));
    }

    @Test
    @DisplayName("Remote diverged during push is accurately categorized")
    void testRemoteDivergedCategorized() {
        mockValidRepository();

        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("add"), eq("activity/daily-log.md")))
                .thenReturn(GitProcessResult.success(""));
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("diff"), eq("--cached"), eq("--quiet"), eq("activity/daily-log.md")))
                .thenReturn(GitProcessResult.failure(1, "", ""));
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("commit"), eq("-m"), anyString(), eq("--"), eq("activity/daily-log.md")))
                .thenReturn(GitProcessResult.success(""));
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("rev-parse"), eq("HEAD")))
                .thenReturn(GitProcessResult.success("abc1234"));

        // Push fails because non-fast-forward
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("push"), eq("origin"), eq("main")))
                .thenReturn(GitProcessResult.failure(1, "", "error: failed to push some refs to '...'\n ! [rejected] main -> main (fetch first)"));

        JobResult result = gitCommitService.stageCommitAndPush(testDate);

        assertEquals(JobStatus.REMOTE_DIVERGED, result.getStatus());
        assertFalse(result.isSuccessful());
        assertTrue(result.getMessage().contains("remote has diverged"));
    }

    @Test
    @DisplayName("10. A Git command timeout is handled")
    void testGitTimeoutHandled() {
        mockValidRepository();

        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("add"), eq("activity/daily-log.md")))
                .thenReturn(GitProcessResult.timeout("", "Command timed out"));

        JobResult result = gitCommitService.stageCommitAndPush(testDate);

        assertEquals(JobStatus.TIMEOUT, result.getStatus());
        assertFalse(result.isSuccessful());
    }

    @Test
    @DisplayName("11. A missing or incorrect remote is handled")
    void testMissingRemoteHandled() {
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("rev-parse"), eq("--is-inside-work-tree")))
                .thenReturn(GitProcessResult.success("true"));
        // Remote check fails
        when(processRunner.run(any(File.class), anyLong(), eq("git"), eq("remote"), eq("get-url"), eq("origin")))
                .thenReturn(GitProcessResult.failure(128, "", "fatal: No such remote 'origin'"));

        JobResult result = gitCommitService.stageCommitAndPush(testDate);

        assertEquals(JobStatus.INVALID_REPOSITORY, result.getStatus());
        assertFalse(result.isSuccessful());
        assertTrue(result.getMessage().contains("Configured remote 'origin' was not found"));
    }

    @Test
    @DisplayName("15. Secrets do not appear in DefaultGitProcessRunner output")
    void testSecretsSanitizedInLogs() {
        String tokenUrl = "https://oauth2:ghp_SuperSecretToken123456@github.com/Samidha0811/repo.git";
        String sanitized = DefaultGitProcessRunner.sanitize(tokenUrl);

        assertFalse(sanitized.contains("ghp_SuperSecretToken123456"));
        assertTrue(sanitized.contains("https://oauth2:******@github.com"));
    }
}
