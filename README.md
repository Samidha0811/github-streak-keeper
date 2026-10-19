# GitHub Streak Keeper 🚀

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.8-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![License](https://img.shields.io/badge/license-MIT-blue.svg)]()

**GitHub Streak Keeper** is a production-conscious, automated Spring Boot service that generates a daily dated activity log entry, stages and commits only the activity file, and safely pushes to your GitHub repository to maintain an honest, consistent activity record.

---

## Table of Contents

- [Key Features](#key-features)
- [Architecture & Workflow](#architecture--workflow)
- [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Configuration Reference](#configuration-reference)
- [GitHub Authentication & Contribution Attribution](#github-authentication--contribution-attribution)
- [Local Setup & Running](#local-setup--running)
- [REST Endpoints & Manual Trigger](#rest-endpoints--manual-trigger)
- [Testing](#testing)
- [Troubleshooting & Recovery](#troubleshooting--recovery)
- [Deployment Guide](#deployment-guide)

---

## Key Features

1. **Automated Scheduling**: Scheduled daily execution (default **9:00 AM Indian Standard Time** / `Asia/Kolkata`) with configurable cron expression and timezone.
2. **Strict Idempotency**: Inspects `activity/daily-log.md` before writing. If an entry for the date already exists, duplicate entries are skipped.
3. **Safe Git Integration**:
   - Built using Java `ProcessBuilder` with separate argument arrays (no fragile shell string concatenation).
   - Asynchronous stdout/stderr stream consumption to eliminate OS pipe deadlocks.
   - Strict command timeouts.
   - Stages **only** `activity/daily-log.md` (never runs `git add .` or `git add -A`).
   - Detects whether real changes exist via `git diff --cached --quiet` before creating a commit (never creates empty commits).
   - Strictly forbids force pushes (`git push --force`).
4. **Execution Locking**: Process-level concurrency guard (`AtomicBoolean`) prevents scheduled jobs and manual API triggers from overlapping.
5. **Secure Manual Execution**: Protected REST endpoint (`POST /api/jobs/daily-commit`) with constant-time API key validation (`X-API-KEY`).
6. **Detailed Health & Diagnostics**: Sanitized health endpoint (`GET /api/health`) and Spring Boot Actuator (`/actuator/health`). No secrets, private keys, or passwords are ever exposed in logs or endpoints.
7. **Production Ready**: Fully documented for deployment on always-on Linux VMs with `systemd` restart management.

---

## Architecture & Workflow

```
                        +----------------------------+
                        |   DailyCommitScheduler     |
                        |   (Cron: 0 0 9 * * *)      |
                        +--------------+-------------+
                                       |
                                       v
+-------------------------+     +-------------------------------+
| HTTP Client / curl      | --> |  StreakKeeperWorkflowService  |
| POST /api/jobs/...      |     |  (Single-execution Lock)      |
+-------------------------+     +---------------+---------------+
                                                |
                       +------------------------+------------------------+
                       |                                                 |
                       v                                                 v
         +---------------------------+                     +---------------------------+
         |    ActivityLogService     |                     |     GitCommitService      |
         +---------------------------+                     +---------------------------+
         | 1. Determine local date   |                     | 1. Verify Git repository  |
         |    in configured timezone |                     | 2. Stage activity file    |
         | 2. Check for duplicates   |                     | 3. Check for real diff    |
         | 3. Atomic file write to   |                     | 4. Create Git commit      |
         |    activity/daily-log.md  |                     | 5. Push to remote branch  |
         +---------------------------+                     | 6. Categorize failures    |
                                                           +---------------------------+
                                                                         |
                                                                         v
                                                            GitHub Remote (via SSH)
```

### Log File Format (`activity/daily-log.md`)
```markdown
# Daily Activity

- 2026-10-19: GitHub Streak Keeper automation recorded its daily execution.
- 2026-10-20: GitHub Streak Keeper automation recorded its daily execution.
```

---

## Technology Stack

- **Java**: OpenJDK 21 LTS
- **Framework**: Spring Boot 4.0.8 (Managed dependencies)
- **Scheduling**: Spring Scheduling (`@EnableScheduling`, `@Scheduled`)
- **Process Management**: Java `ProcessBuilder` with async stream handling
- **Monitoring**: Spring Boot Actuator (`/actuator/health`, `/actuator/info`)
- **Web API**: Spring Web MVC REST Controller
- **Testing**: JUnit 5, Mockito, Spring Boot Test, isolated local Git integration tests

---

## Prerequisites

- **Java 21** or later installed:
  ```bash
  java -version
  ```
- **Git 2.28+** installed:
  ```bash
  git --version
  ```
- Active GitHub account with repository permissions.

---

## Configuration Reference

All settings can be configured via `src/main/resources/application.properties` or overridden via environment variables.

| Property | Default | Environment Variable | Description |
| :--- | :--- | :--- | :--- |
| `streak-keeper.scheduler.enabled` | `true` | `STREAK_KEEPER_SCHEDULER_ENABLED` | Toggle automated scheduling on/off |
| `streak-keeper.scheduler.cron` | `0 0 9 * * *` | `STREAK_KEEPER_SCHEDULER_CRON` | Six-field Spring cron expression (Sec Min Hour Day Month DayOfWeek) |
| `streak-keeper.scheduler.zone` | `Asia/Kolkata` | `STREAK_KEEPER_SCHEDULER_ZONE` | Time zone used for scheduling and log date |
| `streak-keeper.git.repository-path` | `.` | `STREAK_KEEPER_GIT_REPOSITORY_PATH` | Path to the local Git repository root |
| `streak-keeper.git.remote` | `origin` | `STREAK_KEEPER_GIT_REMOTE` | Git remote name |
| `streak-keeper.git.branch` | `main` | `STREAK_KEEPER_GIT_BRANCH` | Target Git branch |
| `streak-keeper.git.activity-file` | `activity/daily-log.md` | `STREAK_KEEPER_GIT_ACTIVITY_FILE` | Relative path to the activity log markdown file |
| `streak-keeper.git.timeout-seconds` | `30` | `STREAK_KEEPER_GIT_TIMEOUT_SECONDS` | Maximum seconds allowed per Git CLI operation |
| `streak-keeper.git.author-name` | `Samidha0811` | `STREAK_KEEPER_GIT_AUTHOR_NAME` | Git author name fallback if Git config is unset |
| `streak-keeper.git.author-email` | *(empty)* | `STREAK_KEEPER_GIT_AUTHOR_EMAIL` | Git author email (must match your GitHub email) |
| `streak-keeper.activity.message-template` | *Daily log string* | `STREAK_KEEPER_ACTIVITY_MESSAGE_TEMPLATE` | Truthful message template for daily log entries |
| `streak-keeper.api.key` | *(empty)* | `STREAK_KEEPER_API_KEY` | Optional secret key for securing manual trigger API |

---

## GitHub Authentication & Contribution Attribution

### 1. Secure SSH Setup (Recommended)

To allow unattended background pushes without storing plain-text passwords:

1. **Check if an SSH key already exists**:
   ```bash
   ls -la ~/.ssh
   ```
2. **Generate an SSH key** (if none exists):
   ```bash
   ssh-keygen -t ed25519 -C "samidha-streak-keeper"
   ```
3. **Add your public key to GitHub**:
   - Copy key content: `cat ~/.ssh/id_ed25519.pub`
   - In GitHub: **Settings** -> **SSH and GPG keys** -> **New SSH key**.
4. **Test SSH connection**:
   ```bash
   ssh -T git@github.com
   ```
   Expected response:
   ```
   Hi Samidha0811! You've successfully authenticated, but GitHub does not provide shell access.
   ```
5. **Set Remote to SSH URL**:
   ```bash
   git remote set-url origin git@github.com:Samidha0811/github-streak-keeper.git
   ```

### 2. Contribution Attribution Requirements (Why a Push Isn't Always a Green Square)

A successful `git push` does not automatically register as a contribution square on your GitHub profile. GitHub enforces strict contribution rules:

> [!IMPORTANT]
> To receive contribution graph credit and maintain your streak:
>
> 1. **Matching Author Email**: The Git commit author email **MUST** match an email address registered and verified on your GitHub account (or your GitHub-provided noreply address `ID+username@users.noreply.github.com`).
>    - Configure locally:
>      ```bash
>      git config user.name "Samidha0811"
>      git config user.email "your-verified-email@example.com"
>      ```
> 2. **Default Branch**: Commits must be made to the repository's **default branch** (usually `main`), or in a branch with an open pull request merged into the default branch.
> 3. **Non-Fork Repository**: The repository must be a standalone repository, not an unmerged fork.
> 4. **Private Repository Visibility**: If your repository is private, you must enable **"Include private contributions on my profile"** under:
>    - **GitHub Profile** -> **Settings** -> **Public profile** -> **Contributions & Activity** -> check **"Include private contributions on my profile"**.
> 5. **Processing Time**: GitHub recalculates contribution stats asynchronously; graphs typically update within 5 to 15 minutes.

---

## Local Setup & Running

### Step 1: Clone or Navigate to the Workspace
```bash
cd /Users/samidha/Development/Java/GitHubStreak
```

### Step 2: Build the Application
```bash
./mvnw clean package
```

### Step 3: Run the Application
Run via the Maven wrapper:
```bash
./mvnw spring-boot:run
```

Or run the standalone executable JAR:
```bash
java -jar target/github-streak-keeper-0.0.1-SNAPSHOT.jar
```

---

## REST Endpoints & Manual Trigger

The application exposes lightweight, secure REST endpoints:

### 1. Health Status: `GET /api/health`
Returns configuration and execution status without revealing credentials:
```bash
curl http://localhost:8080/api/health
```
Example Response:
```json
{
  "status": "UP",
  "application": "github-streak-keeper",
  "schedulerEnabled": true,
  "cron": "0 0 9 * * *",
  "zone": "Asia/Kolkata",
  "remote": "origin",
  "branch": "main",
  "isJobRunning": false,
  "repositoryValid": true
}
```

### 2. Manual Trigger: `POST /api/jobs/daily-commit`
Triggers the exact same workflow as the scheduled job:
```bash
curl -X POST http://localhost:8080/api/jobs/daily-commit \
  -H "X-API-KEY: your-configured-api-key"
```

- Returns **200 OK** with `JobResult` upon success.
- Returns **401 Unauthorized** if the API key is invalid.
- Returns **409 Conflict** if another job execution is already running.

---

## Testing

The project includes unit and integration tests:

- **ActivityLogServiceTest**: Validates entry creation, idempotency, date formatting across time zones, and log preservation.
- **GitCommitServiceTest**: Mocks Git CLI operations to test empty commit prevention, selective staging, commit creation, push success, timeouts, and categorized push errors (auth, network, divergence).
- **DailyCommitSchedulerTest**: Verifies scheduler triggers the workflow when enabled and skips when disabled.
- **StreakKeeperWorkflowServiceTest**: Verifies coordination and execution locks.
- **JobControllerTest**: Tests health endpoint, API authentication, and conflict handling.
- **GitCommitServiceIntegrationTest**: Exercises real Git CLI commands (`git init --bare`, `git add`, `git commit`, `git push`) against an isolated, temporary local Git repository.

Run the test suite:
```bash
./mvnw clean test
```

---

## Troubleshooting & Recovery

| Issue | Diagnosed Status | Resolution |
| :--- | :--- | :--- |
| **Missing/Invalid SSH Key** | `AUTHENTICATION_ERROR` | Run `ssh -T git@github.com` to test SSH. Check that your public key is added under GitHub Settings. |
| **No Internet Connection** | `NETWORK_ERROR` | Check network connection. The scheduled job will safely re-attempt on the next scheduled run. |
| **Remote Has Diverged** | `REMOTE_DIVERGED` | Remote repository has commits that are not present locally. Run `git pull --rebase origin main` manually to synchronize history. The service never overwrites remote history. |
| **Git Command Timed Out** | `TIMEOUT` | Git operation took longer than `streak-keeper.git.timeout-seconds` (default 30s). Increase timeout in properties if network is slow. |
| **Directory Not a Git Repo** | `INVALID_REPOSITORY` | Verify `streak-keeper.git.repository-path` points to a directory containing a `.git` folder. |
| **Author Identity Unknown** | `FAILED` (git commit) | Configure Git user identity: `git config user.name "Your Name"` and `git config user.email "your@email.com"`. |

---

## Deployment Guide

For full instructions on deploying GitHub Streak Keeper to an always-on cloud server (Linux VM / systemd service), refer to [DEPLOYMENT.md](DEPLOYMENT.md).
