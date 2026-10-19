package com.streakkeeper.service;

/**
 * Result of a Git CLI process execution.
 */
public record GitProcessResult(int exitCode, String stdout, String stderr, boolean timedOut) {

    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }

    public static GitProcessResult success(String stdout) {
        return new GitProcessResult(0, stdout, "", false);
    }

    public static GitProcessResult failure(int exitCode, String stdout, String stderr) {
        return new GitProcessResult(exitCode, stdout, stderr, false);
    }

    public static GitProcessResult timeout(String stdout, String stderr) {
        return new GitProcessResult(-1, stdout, stderr, true);
    }
}
