package com.streakkeeper.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class DefaultGitProcessRunner implements GitProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultGitProcessRunner.class);
    private static final Pattern SECRET_PATTERN = Pattern.compile("https://([^:]+):([^@]+)@");

    @Override
    public GitProcessResult run(File workingDir, long timeoutSeconds, String... command) {
        String sanitizedCmd = sanitize(String.join(" ", command));
        log.debug("Executing Git command in [{}]: {}", workingDir.getAbsolutePath(), sanitizedCmd);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDir);

        Process process = null;
        try {
            process = processBuilder.start();

            // Asynchronously consume stdout and stderr to prevent OS buffer deadlocks
            CompletableFuture<String> stdoutFuture = readStreamAsync(process.getInputStream());
            CompletableFuture<String> stderrFuture = readStreamAsync(process.getErrorStream());

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.warn("Git command timed out after {} seconds: {}", timeoutSeconds, sanitizedCmd);
                process.destroyForcibly();
                String partialStdout = getFutureResult(stdoutFuture);
                String partialStderr = getFutureResult(stderrFuture);
                return GitProcessResult.timeout(sanitize(partialStdout), sanitize(partialStderr));
            }

            int exitCode = process.exitValue();
            String stdout = sanitize(getFutureResult(stdoutFuture));
            String stderr = sanitize(getFutureResult(stderrFuture));

            if (exitCode != 0) {
                log.warn("Git command failed (exit code {}): {}\nStderr: {}", exitCode, sanitizedCmd, stderr);
            } else {
                log.debug("Git command succeeded: {}", sanitizedCmd);
            }

            return new GitProcessResult(exitCode, stdout, stderr, false);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            log.error("Git command interrupted: {}", sanitizedCmd, e);
            return GitProcessResult.failure(-1, "", "Command interrupted: " + e.getMessage());
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            log.error("Failed to execute Git command: {}", sanitizedCmd, e);
            return GitProcessResult.failure(-1, "", "Failed to launch process: " + e.getMessage());
        }
    }

    private CompletableFuture<String> readStreamAsync(InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            } catch (Exception e) {
                return "";
            }
        });
    }

    private String getFutureResult(CompletableFuture<String> future) {
        try {
            return future.get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Sanitizes credentials (e.g. Basic auth in URLs) to prevent leaking in logs.
     */
    public static String sanitize(String input) {
        if (input == null) return "";
        return SECRET_PATTERN.matcher(input).replaceAll("https://$1:******@");
    }
}
