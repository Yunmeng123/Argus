package com.argus.config;

/**
 * 运行时可修改的配置快照。约定: 对外只读, 修改必须走 {@link RuntimeConfigService#update},
 * 整体替换引用保证读取方看到一致的快照。
 */
public class RuntimeConfig {

    private Llm llm = new Llm();
    private Review review = new Review();
    private Gitlab gitlab = new Gitlab();
    private Github github = new Github();
    private Gitee gitee = new Gitee();
    private Notify notify = new Notify();

    public RuntimeConfig copy() {
        RuntimeConfig copied = new RuntimeConfig();
        copied.llm.setBaseUrl(llm.getBaseUrl());
        copied.llm.setApiKey(llm.getApiKey());
        copied.llm.setModel(llm.getModel());
        copied.llm.setVerifierModel(llm.getVerifierModel());
        copied.llm.setTemperature(llm.getTemperature());
        copied.llm.setMock(llm.isMock());
        copied.review.setMaxFileChangedLines(review.getMaxFileChangedLines());
        copied.review.setMaxFindingsPerFile(review.getMaxFindingsPerFile());
        copied.review.setVerifierEnabled(review.isVerifierEnabled());
        copied.review.setDailyTokenBudget(review.getDailyTokenBudget());
        copied.gitlab.setBaseUrl(gitlab.getBaseUrl());
        copied.gitlab.setToken(gitlab.getToken());
        copied.gitlab.setWebhookSecret(gitlab.getWebhookSecret());
        copied.github.setToken(github.getToken());
        copied.github.setWebhookSecret(github.getWebhookSecret());
        copied.gitee.setToken(gitee.getToken());
        copied.gitee.setWebhookSecret(gitee.getWebhookSecret());
        copied.notify.setWebhookUrl(notify.getWebhookUrl());
        return copied;
    }

    public Llm getLlm() {
        return llm;
    }

    public void setLlm(Llm llm) {
        this.llm = llm;
    }

    public Review getReview() {
        return review;
    }

    public void setReview(Review review) {
        this.review = review;
    }

    public Gitlab getGitlab() {
        return gitlab;
    }

    public void setGitlab(Gitlab gitlab) {
        this.gitlab = gitlab;
    }

    public Github getGithub() {
        return github;
    }

    public void setGithub(Github github) {
        this.github = github;
    }

    public Gitee getGitee() {
        return gitee;
    }

    public void setGitee(Gitee gitee) {
        this.gitee = gitee;
    }

    public Notify getNotify() {
        return notify;
    }

    public void setNotify(Notify notify) {
        this.notify = notify;
    }

    public static class Llm {
        private String baseUrl;
        private String apiKey;
        private String model;
        /** Verifier 复核用模型(级联): 空=与主模型相同 */
        private String verifierModel = "";
        private double temperature = 0.2;
        private boolean mock;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getVerifierModel() {
            return verifierModel;
        }

        public void setVerifierModel(String verifierModel) {
            this.verifierModel = verifierModel;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public boolean isMock() {
            return mock;
        }

        public void setMock(boolean mock) {
            this.mock = mock;
        }
    }

    public static class Review {
        private int maxFileChangedLines = 800;
        private int maxFindingsPerFile = 8;
        /** Finder-Verifier 两段式: 开启后每个文件的发现会再经一次"反驳式"复核, 降误报 */
        private boolean verifierEnabled;
        /** 每日 Token 预算, 0=不限制; 超预算拒绝新审查 */
        private int dailyTokenBudget;

        public int getMaxFileChangedLines() {
            return maxFileChangedLines;
        }

        public void setMaxFileChangedLines(int maxFileChangedLines) {
            this.maxFileChangedLines = maxFileChangedLines;
        }

        public int getMaxFindingsPerFile() {
            return maxFindingsPerFile;
        }

        public void setMaxFindingsPerFile(int maxFindingsPerFile) {
            this.maxFindingsPerFile = maxFindingsPerFile;
        }

        public boolean isVerifierEnabled() {
            return verifierEnabled;
        }

        public void setVerifierEnabled(boolean verifierEnabled) {
            this.verifierEnabled = verifierEnabled;
        }

        public int getDailyTokenBudget() {
            return dailyTokenBudget;
        }

        public void setDailyTokenBudget(int dailyTokenBudget) {
            this.dailyTokenBudget = dailyTokenBudget;
        }
    }

    public static class Gitlab {
        /** 例: https://gitlab.example.com (不带 /api/v4) */
        private String baseUrl = "";
        private String token = "";
        private String webhookSecret = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }
    }

    public static class Github {
        /** Personal Access Token(repo 权限), API 地址固定 api.github.com */
        private String token = "";
        private String webhookSecret = "";

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }
    }

    public static class Gitee {
        /** 私人令牌, API 地址固定 gitee.com/api/v5 */
        private String token = "";
        private String webhookSecret = "";

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }
    }

    public static class Notify {
        /** 钉钉/企微自定义机器人 webhook, 空=不通知 */
        private String webhookUrl = "";

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }
    }
}
