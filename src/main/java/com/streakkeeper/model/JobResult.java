package com.streakkeeper.model;

import java.time.LocalDate;

/**
 * Result of a daily commit workflow execution.
 */
public class JobResult {

    private final LocalDate date;
    private final JobStatus status;
    private final String message;
    private final String commitHash;
    private final long durationMillis;
    private final String details;

    public JobResult(LocalDate date, JobStatus status, String message, String commitHash, long durationMillis, String details) {
        this.date = date;
        this.status = status;
        this.message = message;
        this.commitHash = commitHash;
        this.durationMillis = durationMillis;
        this.details = details;
    }

    public static JobResult success(LocalDate date, String commitHash, long durationMillis, String message) {
        return new JobResult(date, JobStatus.COMMITTED_AND_PUSHED, message, commitHash, durationMillis, null);
    }

    public static JobResult noChange(LocalDate date, long durationMillis, String message) {
        return new JobResult(date, JobStatus.NO_CHANGE, message, null, durationMillis, null);
    }

    public static JobResult duplicate(LocalDate date, long durationMillis, String message) {
        return new JobResult(date, JobStatus.DUPLICATE_EXECUTION, message, null, durationMillis, null);
    }

    public static JobResult alreadyRunning(LocalDate date) {
        return new JobResult(date, JobStatus.ALREADY_RUNNING, "A streak keeper job is currently executing.", null, 0, null);
    }

    public static JobResult failure(LocalDate date, JobStatus status, String message, String details, long durationMillis) {
        return new JobResult(date, status, message, null, durationMillis, details);
    }

    public static JobResult commitSucceededPushFailed(LocalDate date, JobStatus status, String commitHash, String message, String details, long durationMillis) {
        return new JobResult(date, status, message, commitHash, durationMillis, details);
    }

    public LocalDate getDate() {
        return date;
    }

    public JobStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public String getDetails() {
        return details;
    }

    public boolean isSuccessful() {
        return status == JobStatus.COMMITTED_AND_PUSHED || status == JobStatus.NO_CHANGE;
    }

    @Override
    public String toString() {
        return "JobResult{" +
                "date=" + date +
                ", status=" + status +
                ", message='" + message + '\'' +
                ", commitHash='" + commitHash + '\'' +
                ", durationMillis=" + durationMillis +
                ", details='" + details + '\'' +
                '}';
    }
}
