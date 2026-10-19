package com.streakkeeper.model;

/**
 * Status enumeration for daily commit workflow executions.
 */
public enum JobStatus {
    /**
     * Activity entry for the date already exists and repository has no pending changes.
     */
    NO_CHANGE,

    /**
     * Activity entry was created, committed to Git, and successfully pushed to remote.
     */
    COMMITTED_AND_PUSHED,

    /**
     * Git commit was created, but pushing to the remote repository failed.
     */
    COMMIT_SUCCEEDED_PUSH_FAILED,

    /**
     * Authentication failed during remote Git operations (e.g. invalid SSH key or credentials).
     */
    AUTHENTICATION_ERROR,

    /**
     * Remote Git operation failed due to network connectivity issues or host unreachable.
     */
    NETWORK_ERROR,

    /**
     * Git command execution exceeded the configured timeout limit.
     */
    TIMEOUT,

    /**
     * Push rejected because remote repository contains commits not present locally.
     */
    REMOTE_DIVERGED,

    /**
     * The configured path is not a valid Git repository or remote/branch is missing.
     */
    INVALID_REPOSITORY,

    /**
     * Execution skipped because an entry for this date is already recorded.
     */
    DUPLICATE_EXECUTION,

    /**
     * Execution rejected because another job instance is currently in progress.
     */
    ALREADY_RUNNING,

    /**
     * General unexpected failure during job execution.
     */
    FAILED
}
