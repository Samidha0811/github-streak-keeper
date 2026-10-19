package com.streakkeeper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@ConfigurationProperties(prefix = "streak-keeper")
public class StreakKeeperProperties {

    private Scheduler scheduler = new Scheduler();
    private Git git = new Git();
    private Activity activity = new Activity();
    private Api api = new Api();

    public Scheduler getScheduler() {
        return scheduler;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public Git getGit() {
        return git;
    }

    public void setGit(Git git) {
        this.git = git;
    }

    public Activity getActivity() {
        return activity;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    public Api getApi() {
        return api;
    }

    public void setApi(Api api) {
        this.api = api;
    }

    public static class Scheduler {
        private boolean enabled = true;
        private String cron = "0 0 9 * * *";
        private String zone = "Asia/Kolkata";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public String getZone() {
            return zone;
        }

        public void setZone(String zone) {
            this.zone = zone;
        }

        public ZoneId getZoneId() {
            try {
                return ZoneId.of(zone);
            } catch (Exception e) {
                return ZoneId.of("Asia/Kolkata");
            }
        }
    }

    public static class Git {
        private String repositoryPath = ".";
        private String remote = "origin";
        private String branch = "main";
        private String activityFile = "activity/daily-log.md";
        private long timeoutSeconds = 30;
        private String authorName = "";
        private String authorEmail = "";

        public String getRepositoryPath() {
            return repositoryPath;
        }

        public void setRepositoryPath(String repositoryPath) {
            this.repositoryPath = repositoryPath;
        }

        public String getRemote() {
            return remote;
        }

        public void setRemote(String remote) {
            this.remote = remote;
        }

        public String getBranch() {
            return branch;
        }

        public void setBranch(String branch) {
            this.branch = branch;
        }

        public String getActivityFile() {
            return activityFile;
        }

        public void setActivityFile(String activityFile) {
            this.activityFile = activityFile;
        }

        public long getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public String getAuthorName() {
            return authorName;
        }

        public void setAuthorName(String authorName) {
            this.authorName = authorName;
        }

        public String getAuthorEmail() {
            return authorEmail;
        }

        public void setAuthorEmail(String authorEmail) {
            this.authorEmail = authorEmail;
        }
    }

    public static class Activity {
        private String messageTemplate = "GitHub Streak Keeper automation recorded its daily execution.";

        public String getMessageTemplate() {
            return messageTemplate;
        }

        public void setMessageTemplate(String messageTemplate) {
            this.messageTemplate = messageTemplate;
        }
    }

    public static class Api {
        private String key = "";

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }
}
