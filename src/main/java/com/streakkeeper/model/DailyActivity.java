package com.streakkeeper.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a dated daily activity entry.
 */
public class DailyActivity {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Pattern ENTRY_PATTERN = Pattern.compile("^-\\s*(\\d{4}-\\d{2}-\\d{2})\\s*:\\s*(.*)$");

    private final LocalDate date;
    private final String message;
    private final Instant timestamp;

    public DailyActivity(LocalDate date, String message) {
        this(date, message, Instant.now());
    }

    public DailyActivity(LocalDate date, String message, Instant timestamp) {
        this.date = Objects.requireNonNull(date, "date cannot be null");
        this.message = Objects.requireNonNull(message, "message cannot be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

    public LocalDate getDate() {
        return date;
    }

    public String getMessage() {
        return message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Formats the activity as a markdown log entry line.
     * Example: "- 2026-10-19: GitHub Streak Keeper automation recorded its daily execution."
     */
    public String toLogLine() {
        return "- " + date.format(DATE_FORMATTER) + ": " + message;
    }

    /**
     * Checks if a line in the log file corresponds to the specified date.
     */
    public static boolean matchesDate(String line, LocalDate targetDate) {
        if (line == null || line.isBlank()) {
            return false;
        }
        Matcher matcher = ENTRY_PATTERN.matcher(line.trim());
        if (matcher.matches()) {
            String dateStr = matcher.group(1);
            try {
                LocalDate parsedDate = LocalDate.parse(dateStr, DATE_FORMATTER);
                return parsedDate.equals(targetDate);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Parses a log line into a DailyActivity, or returns null if not matching.
     */
    public static DailyActivity parseLogLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        Matcher matcher = ENTRY_PATTERN.matcher(line.trim());
        if (matcher.matches()) {
            try {
                LocalDate parsedDate = LocalDate.parse(matcher.group(1), DATE_FORMATTER);
                String msg = matcher.group(2);
                return new DailyActivity(parsedDate, msg);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DailyActivity that = (DailyActivity) o;
        return Objects.equals(date, that.date) && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, message);
    }

    @Override
    public String toString() {
        return "DailyActivity{" +
                "date=" + date +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
