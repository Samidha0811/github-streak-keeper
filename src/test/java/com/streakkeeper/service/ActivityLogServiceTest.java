package com.streakkeeper.service;

import com.streakkeeper.config.StreakKeeperProperties;
import com.streakkeeper.model.DailyActivity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActivityLogServiceTest {

    @TempDir
    Path tempDir;

    private StreakKeeperProperties properties;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        properties = new StreakKeeperProperties();
        properties.getGit().setRepositoryPath(tempDir.toString());
        properties.getGit().setActivityFile("activity/daily-log.md");
        properties.getScheduler().setZone("Asia/Kolkata");

        // 2026-10-19 22:30:00 UTC is 2026-10-20 04:00:00 IST (+05:30)
        Instant instant = Instant.parse("2026-10-19T22:30:00Z");
        fixedClock = Clock.fixed(instant, ZoneId.of("UTC"));
    }

    @Test
    @DisplayName("1. A daily activity entry is created when file does not exist")
    void testDailyActivityEntryCreated() throws IOException {
        ActivityLogService service = new ActivityLogService(properties, fixedClock);

        ActivityLogService.ActivityLogResult result = service.recordDailyActivity();

        assertTrue(result.created(), "Expected entry to be created");
        Path logFile = service.resolveActivityFilePath();
        assertTrue(Files.exists(logFile), "Log file should exist");

        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        assertTrue(lines.contains("# Daily Activity"));
        // In IST, 2026-10-19T22:30:00Z is 2026-10-20
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("- 2026-10-20:")));
    }

    @Test
    @DisplayName("2. Duplicate execution on the same date does not duplicate the entry")
    void testDuplicateExecutionOnSameDate() throws IOException {
        ActivityLogService service = new ActivityLogService(properties, fixedClock);

        // First run creates entry
        ActivityLogService.ActivityLogResult firstResult = service.recordDailyActivity();
        assertTrue(firstResult.created());

        // Second run on same date must skip
        ActivityLogService.ActivityLogResult secondResult = service.recordDailyActivity();
        assertFalse(secondResult.created(), "Duplicate execution must not create new entry");

        Path logFile = service.resolveActivityFilePath();
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        long matchingLines = lines.stream().filter(l -> l.startsWith("- 2026-10-20:")).count();
        assertEquals(1, matchingLines, "There must be exactly one entry for date 2026-10-20");
    }

    @Test
    @DisplayName("3. Entries for previous dates are preserved")
    void testPreviousDatesPreserved() throws IOException {
        Path logFile = tempDir.resolve("activity/daily-log.md");
        Files.createDirectories(logFile.getParent());
        Files.write(logFile, List.of(
                "# Daily Activity",
                "",
                "- 2026-10-18: Previous activity 1",
                "- 2026-10-19: Previous activity 2"
        ), StandardCharsets.UTF_8);

        ActivityLogService service = new ActivityLogService(properties, fixedClock);
        ActivityLogService.ActivityLogResult result = service.recordDailyActivity();

        assertTrue(result.created());
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        assertTrue(lines.contains("- 2026-10-18: Previous activity 1"));
        assertTrue(lines.contains("- 2026-10-19: Previous activity 2"));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("- 2026-10-20:")));
    }

    @Test
    @DisplayName("4. The configured time zone determines the current activity date")
    void testConfiguredTimeZoneDeterminesDate() {
        // At 2026-10-19T22:30:00Z:
        // In UTC: date is 2026-10-19
        // In Asia/Kolkata (+05:30): date is 2026-10-20
        properties.getScheduler().setZone("Asia/Kolkata");
        ActivityLogService istService = new ActivityLogService(properties, fixedClock);
        assertEquals(LocalDate.of(2026, 10, 20), istService.getCurrentDate());

        properties.getScheduler().setZone("UTC");
        ActivityLogService utcService = new ActivityLogService(properties, fixedClock);
        assertEquals(LocalDate.of(2026, 10, 19), utcService.getCurrentDate());
    }

    @Test
    @DisplayName("5. Repeated executions do not corrupt log file")
    void testRepeatedExecutionsDoNotCorruptLog() throws IOException {
        ActivityLogService service = new ActivityLogService(properties, fixedClock);

        for (int i = 0; i < 5; i++) {
            service.recordDailyActivity();
        }

        Path logFile = service.resolveActivityFilePath();
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        assertTrue(lines.size() >= 3);
        assertEquals("# Daily Activity", lines.get(0));
    }
}
