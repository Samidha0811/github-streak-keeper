# Deployment Guide: GitHub Streak Keeper

This guide describes how to deploy and operate **GitHub Streak Keeper** on an always-on Linux server (e.g. AWS EC2, DigitalOcean Droplet, Hetzner Cloud, or Oracle Cloud Always Free VM) so that daily automated activity commits and pushes run independently of your local development machine.

---

## 1. Hosting Architecture & Prerequisites

### Architecture Overview
The application runs as a persistent Spring Boot background service on an always-on host. It manages a local Git checkout of your repository, appends a daily log to `activity/daily-log.md`, creates a commit, and pushes to GitHub over SSH.

```
+-----------------------------------------------------------+
| Server (Linux VM / VPS)                                   |
|                                                           |
|  +-----------------------+     +-----------------------+  |
|  | systemd Service       | --> | Local Git Checkout    |  |
|  | (Java 21 Spring Boot) |     | (/opt/streak-keeper)  |  |
|  +-----------------------+     +-----------------------+  |
|             |                              |              |
|   GET /api/health (Monitoring)             | git push     |
|   POST /api/jobs/daily-commit (Manual)     v (SSH Auth)   |
+--------------------------------------------|--------------+
                                             v
                                  GitHub Remote Repository
                                  (git@github.com:Samidha0811/...)
```

### Hosting Comparison

| Hosting Type | Suitability | Notes |
| :--- | :--- | :--- |
| **Linux VPS (Recommended)** (Hetzner, DigitalOcean, AWS EC2, Oracle Free Tier) | **Optimal** | Persistent disk for Git repo, full SSH key isolation, uninterrupted 24/7 scheduler execution, systemd process management. |
| **Raspberry Pi / Home Server** | **Good** | Inexpensive, full control, but requires continuous power and stable home internet. |
| **Serverless / Sleep-enabled PaaS** (Heroku free tier, Render free tier) | **Not recommended** | Free tiers spin down when idle, dropping scheduled jobs; ephemeral filesystems wipe the local Git repository on restart. |

---

## 2. Server Provisioning & Installation

### Step 2.1: Install Java 21 and Git

On Ubuntu / Debian:
```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless git curl
```

On Fedora / RHEL / Rocky Linux:
```bash
sudo dnf install -y java-21-openjdk-headless git curl
```

Verify installation:
```bash
java -version
git --version
```

### Step 2.2: Create Dedicated Service User
Create a non-root system user for security:
```bash
sudo useradd -r -s /bin/bash -m -d /opt/streak-keeper streakkeeper
sudo -u streakkeeper mkdir -p /opt/streak-keeper/app
sudo -u streakkeeper mkdir -p /opt/streak-keeper/repo
```

---

## 3. GitHub Authentication (SSH Setup)

For unattended Git pushes on a server, **SSH key authentication** is the industry standard.

### Step 3.1: Generate Dedicated SSH Key Pair
Log in as the service user:
```bash
sudo -u streakkeeper -i
ssh-keygen -t ed25519 -C "samidha-streak-keeper-server" -f ~/.ssh/id_ed25519 -N ""
```
> [!IMPORTANT]
> Leave the passphrase empty (`-N ""`) so the automated background process can authenticate without interactive prompts.

### Step 3.2: Add Public Key to GitHub
1. Print the **public** key:
   ```bash
   cat ~/.ssh/id_ed25519.pub
   ```
2. Navigate to **GitHub.com** -> **Settings** -> **SSH and GPG keys** -> **New SSH key**.
3. Set Title to `Streak Keeper Server` and paste the public key.
4. If configuring as a **Deploy Key** on a single repository:
   - Go to your repository `https://github.com/Samidha0811/github-streak-keeper` -> **Settings** -> **Deploy keys** -> **Add deploy key**.
   - Paste the public key and check **"Allow write access"**.

### Step 3.3: Verify SSH Authentication
On the server, test the connection to GitHub:
```bash
ssh -T -o StrictHostKeyChecking=accept-new git@github.com
```
Expected output:
```
Hi Samidha0811! You've successfully authenticated, but GitHub does not provide shell access.
```

### Step 3.4: Clone the Repository
Clone the repository using the SSH URL:
```bash
cd /opt/streak-keeper/repo
git clone git@github.com:Samidha0811/github-streak-keeper.git .
```

Configure Git user identity inside the repository:
```bash
git config user.name "Samidha0811"
# Use your verified GitHub email address or GitHub noreply email:
git config user.email "your-github-email@example.com"
```

---

## 4. Application Packaging & Deployment

### Step 4.1: Build JAR Locally or in CI
On your local machine:
```bash
./mvnw clean package
```
This produces `target/github-streak-keeper-0.0.1-SNAPSHOT.jar`.

### Step 4.2: Transfer JAR to Server
Using `scp` or `rsync`:
```bash
scp target/github-streak-keeper-0.0.1-SNAPSHOT.jar user@your-server-ip:/tmp/
sudo cp /tmp/github-streak-keeper-0.0.1-SNAPSHOT.jar /opt/streak-keeper/app/github-streak-keeper.jar
sudo chown -R streakkeeper:streakkeeper /opt/streak-keeper
```

---

## 5. Systemd Service Configuration

Run the application as a monitored system service that restarts on failures and boots on server startup.

Create `/etc/systemd/system/streak-keeper.service`:
```ini
[Unit]
Description=GitHub Streak Keeper Service
After=network.target

[Service]
Type=simple
User=streakkeeper
Group=streakkeeper
WorkingDirectory=/opt/streak-keeper/repo

# Environment configurations
Environment="STREAK_KEEPER_GIT_REPOSITORY_PATH=/opt/streak-keeper/repo"
Environment="STREAK_KEEPER_GIT_REMOTE=origin"
Environment="STREAK_KEEPER_GIT_BRANCH=main"
Environment="STREAK_KEEPER_SCHEDULER_CRON=0 0 9 * * *"
Environment="STREAK_KEEPER_SCHEDULER_ZONE=Asia/Kolkata"
Environment="STREAK_KEEPER_SCHEDULER_ENABLED=true"
Environment="STREAK_KEEPER_API_KEY=change-to-a-secure-random-secret"
Environment="MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info"
Environment="SERVER_PORT=8080"

ExecStart=/usr/bin/java -Xms64m -Xmx256m -jar /opt/streak-keeper/app/github-streak-keeper.jar

Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=streak-keeper

# Security sandboxing
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full

[Install]
WantedBy=multi-user.target
```

Enable and start the service:
```bash
sudo systemctl daemon-reload
sudo systemctl enable streak-keeper
sudo systemctl start streak-keeper
```

Check status:
```bash
sudo systemctl status streak-keeper
```

---

## 6. Monitoring, Logs & Health Checks

### Check Real-Time Logs
```bash
sudo journalctl -u streak-keeper -f
```

### Query Health Endpoint
```bash
curl http://localhost:8080/api/health
```
Response:
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

### Manually Trigger Workflow via API
```bash
curl -X POST http://localhost:8080/api/jobs/daily-commit \
  -H "X-API-KEY: change-to-a-secure-random-secret"
```

---

## 7. Troubleshooting & Recovery

### Push Failure: `AUTHENTICATION_ERROR`
- **Cause**: The SSH key is missing, unauthorized, or not associated with write access on the GitHub repo.
- **Fix**: Run `sudo -u streakkeeper ssh -T git@github.com`. If using a repository Deploy Key, verify **"Allow write access"** was checked in GitHub repository settings.

### Push Failure: `REMOTE_DIVERGED`
- **Cause**: Commits were pushed to GitHub from another source (e.g. web UI or local machine) that are not in the server's local clone.
- **Fix**: The application intentionally rejects destructive resets. Pull the latest commits on the server:
  ```bash
  cd /opt/streak-keeper/repo
  sudo -u streakkeeper git pull --rebase origin main
  ```

### Push Failure: `NETWORK_ERROR`
- **Cause**: Temporary DNS or ISP connectivity disruption.
- **Fix**: Verify network connectivity (`ping github.com`). The scheduler will retry on the next execution cycle, or you can trigger manually via the API once network is restored.

### File Lock or Duplicate Executions
- The service uses thread-safe memory guards (`AtomicBoolean`) and file locks to prevent concurrent runs. If an execution is in progress, any subsequent trigger receives `409 CONFLICT` or `ALREADY_RUNNING` without corrupting files.
