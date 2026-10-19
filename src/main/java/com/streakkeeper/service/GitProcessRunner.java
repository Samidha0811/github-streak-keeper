package com.streakkeeper.service;

import java.io.File;

/**
 * Strategy interface for executing Git CLI commands.
 * Decouples Git execution logic for thorough unit testing without external side effects.
 */
public interface GitProcessRunner {

    /**
     * Executes a Git CLI command in the given working directory.
     *
     * @param workingDir     the repository directory
     * @param timeoutSeconds maximum seconds to wait
     * @param command        the command and arguments (e.g. "git", "status")
     * @return GitProcessResult containing exit code, stdout, stderr, and timeout status
     */
    GitProcessResult run(File workingDir, long timeoutSeconds, String... command);
}
