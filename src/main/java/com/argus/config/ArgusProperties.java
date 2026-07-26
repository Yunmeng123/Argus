package com.argus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * argus.* 业务配置。
 */
@ConfigurationProperties(prefix = "argus")
public class ArgusProperties {

    private final Llm llm = new Llm();
    private final Review review = new Review();
    private final Report report = new Report();
    private final Gitlab gitlab = new Gitlab();
    private final Github github = new Github();
    private final Gitee gitee = new Gitee();
    private final Notify notify = new Notify();
    private final Security security = new Security();

    public Llm getLlm() {
        return llm;
    }

    public Review getReview() {
        return review;
    }

    public Report getReport() {
        return report;
    }

    public Gitlab getGitlab() {
        return gitlab;
    }

    public Github getGithub() {
        return github;
    }

    public Gitee getGitee() {
        return gitee;
    }

    public Notify getNotify() {
        return notify;
    }

    public Security getSecurity() {
        return security;
    }

    public static class Security {
        /** 访问令牌: 为空=开放模式(本机自用); 设置后 /api/** 与 /sse 必须携带 X-Argus-Token */
        private String accessToken = "";

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }
    }

    public static class Gitlab {
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
        private String webhookUrl = "";

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }
    }

    public static class Llm {
        /** true 时不调用真实 LLM, 返回固定示例结果, 用于无 API Key 时跑通链路 */
        private boolean mock = false;
        /** OpenAI 兼容接口地址(以下均为种子值, 运行期以 RuntimeConfigService 为准) */
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey = "";
        private String model = "deepseek-chat";
        /** Verifier 复核用模型(级联), 空=与主模型相同 */
        private String verifierModel = "";
        private double temperature = 0.2;

        public boolean isMock() {
            return mock;
        }

        public void setMock(boolean mock) {
            this.mock = mock;
        }

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

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public String getVerifierModel() {
            return verifierModel;
        }

        public void setVerifierModel(String verifierModel) {
            this.verifierModel = verifierModel;
        }
    }

    public static class Review {
        /** 单文件变更行数上限, 超过则跳过审查(大文件 diff 塞满上下文反而降低审查质量) */
        private int maxFileChangedLines = 800;
        /** 单文件最多报告的问题数 */
        private int maxFindingsPerFile = 8;
        /** Finder-Verifier 两段式复核开关 */
        private boolean verifierEnabled = false;
        /** 每日 Token 预算, 0=不限制 */
        private int dailyTokenBudget = 0;

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

    public static class Report {
        /** Markdown 审查报告输出目录 */
        private String dir = "reports";

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }
    }
}
