package com.streakkeeper.service;

import com.streakkeeper.config.StreakKeeperProperties;
import com.streakkeeper.model.DailyActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ActivityLogService {

    private static final Logger log = LoggerFactory.getLogger(ActivityLogService.class);
    private static final String HEADER = "# Daily Activity";

    private final StreakKeeperProperties properties;
    private final Clock clock;
    private final Object fileLock = new Object();

    @org.springframework.beans.factory.annotation.Autowired
    public ActivityLogService(StreakKeeperProperties properties) {
        this(properties, Clock.systemDefaultZone());
    }

    public ActivityLogService(StreakKeeperProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public LocalDate getCurrentDate() {
        return LocalDate.now(clock.withZone(properties.getScheduler().getZoneId()));
    }

    public Path resolveActivityFilePath() {
        Path repoPath = Paths.get(properties.getGit().getRepositoryPath()).toAbsolutePath().normalize();
        return repoPath.resolve(properties.getGit().getActivityFile()).normalize();
    }

    public ActivityLogResult recordDailyActivity() throws IOException {
        LocalDate currentDate = getCurrentDate();
        String message = properties.getActivity().getMessageTemplate();
        DailyActivity activity = new DailyActivity(currentDate, message);
        return recordDailyActivity(activity);
    }

    public ActivityLogResult recordDailyActivity(DailyActivity activity) throws IOException {
        Path filePath = resolveActivityFilePath();
        LocalDate targetDate = activity.getDate();

        synchronized (fileLock) {
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.info("Created activity directory: {}", parentDir);
            }

            List<String> lines = new ArrayList<>();
            boolean fileExists = Files.exists(filePath);

            if (fileExists) {
                lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (DailyActivity.matchesDate(line, targetDate)) {
                        log.info("Daily activity for date {} already exists in {}. Skipping addition.", targetDate, filePath);
                        return new ActivityLogResult(false, activity, filePath, "Entry already exists for date " + targetDate);
                    }
                }
            }

            // Prepare updated content
            List<String> updatedLines = new ArrayList<>();
            if (lines.isEmpty()) {
                updatedLines.add(HEADER);
                updatedLines.add("");
            } else {
                updatedLines.addAll(lines);
                // Ensure proper spacing if needed
                if (!updatedLines.isEmpty() && !updatedLines.get(updatedLines.size() - 1).isBlank()) {
                    // previous lines had content
                }
            }

            updatedLines.add(activity.toLogLine());

            // Write atomically using temporary file in the same directory
            Path tempFile = Files.createTempFile(parentDir != null ? parentDir : Paths.get("."), "daily-log-", ".tmp");
            try {
                Files.write(tempFile, updatedLines, StandardCharsets.UTF_8);
                try {
                    Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING);
                }
                log.info("Recorded daily activity for {} in {}", targetDate, filePath);
                return new ActivityLogResult(true, activity, filePath, "Successfully added daily activity entry");
            } finally {
                // Clean up temp file if still present
                if (Files.exists(tempFile)) {
                    try {
                        Files.delete(tempFile);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    public record ActivityLogResult(boolean created, DailyActivity activity, Path filePath, String message) {
    }
}
