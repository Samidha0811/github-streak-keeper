package com.streakkeeper.controller;

import com.streakkeeper.config.StreakKeeperProperties;
import com.streakkeeper.model.JobResult;
import com.streakkeeper.service.GitCommitService;
import com.streakkeeper.service.StreakKeeperWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobControllerTest {

    private MockMvc mockMvc;
    private StreakKeeperProperties properties;
    private StreakKeeperWorkflowService workflowService;
    private GitCommitService gitCommitService;
    private final LocalDate testDate = LocalDate.of(2026, 10, 19);

    @BeforeEach
    void setUp() {
        properties = new StreakKeeperProperties();
        workflowService = mock(StreakKeeperWorkflowService.class);
        gitCommitService = mock(GitCommitService.class);

        JobController controller = new JobController(properties, workflowService, gitCommitService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(gitCommitService.validateRepository())
                .thenReturn(new GitCommitService.RepositoryValidationResult(true, "Valid repository"));
    }

    @Test
    @DisplayName("GET /api/health returns 200 and expected health metadata")
    void testGetHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("github-streak-keeper"))
                .andExpect(jsonPath("$.schedulerEnabled").value(true))
                .andExpect(jsonPath("$.cron").value("0 0 9 * * *"))
                .andExpect(jsonPath("$.zone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.repositoryValid").value(true));
    }

    @Test
    @DisplayName("POST /api/jobs/daily-commit without API key succeeds when auth is not configured")
    void testTriggerWorkflowWithoutAuthWhenUnconfigured() throws Exception {
        properties.getApi().setKey(""); // no key configured

        when(workflowService.executeWorkflow(true))
                .thenReturn(JobResult.success(testDate, "commit123", 100, "Success"));

        mockMvc.perform(post("/api/jobs/daily-commit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMMITTED_AND_PUSHED"))
                .andExpect(jsonPath("$.commitHash").value("commit123"));

        verify(workflowService, times(1)).executeWorkflow(true);
    }

    @Test
    @DisplayName("POST /api/jobs/daily-commit returns 401 when API key configured but header missing")
    void testTriggerWorkflowUnauthorizedWhenKeyMissing() throws Exception {
        properties.getApi().setKey("my-secret-key-12345");

        mockMvc.perform(post("/api/jobs/daily-commit"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verify(workflowService, never()).executeWorkflow(anyBoolean());
    }

    @Test
    @DisplayName("POST /api/jobs/daily-commit returns 401 when wrong API key provided")
    void testTriggerWorkflowUnauthorizedWhenKeyWrong() throws Exception {
        properties.getApi().setKey("my-secret-key-12345");

        mockMvc.perform(post("/api/jobs/daily-commit")
                        .header("X-API-KEY", "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verify(workflowService, never()).executeWorkflow(anyBoolean());
    }

    @Test
    @DisplayName("POST /api/jobs/daily-commit succeeds with valid X-API-KEY header")
    void testTriggerWorkflowAuthorized() throws Exception {
        properties.getApi().setKey("my-secret-key-12345");

        when(workflowService.executeWorkflow(true))
                .thenReturn(JobResult.success(testDate, "abc789", 50, "Success"));

        mockMvc.perform(post("/api/jobs/daily-commit")
                        .header("X-API-KEY", "my-secret-key-12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMMITTED_AND_PUSHED"))
                .andExpect(jsonPath("$.commitHash").value("abc789"));

        verify(workflowService, times(1)).executeWorkflow(true);
    }

    @Test
    @DisplayName("14. The manual trigger cannot bypass duplicate protection and returns 409 if already running")
    void testManualTriggerCannotBypassLock() throws Exception {
        when(workflowService.executeWorkflow(true))
                .thenReturn(JobResult.alreadyRunning(testDate));

        mockMvc.perform(post("/api/jobs/daily-commit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ALREADY_RUNNING"));
    }
}
