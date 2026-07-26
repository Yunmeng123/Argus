package com.argus.vcs;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.argus.config.RuntimeConfig;
import com.argus.config.RuntimeConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * GitHub 适配: X-Hub-Signature-256 对原始报文做 HMAC-SHA256 验签;
 * diff 直接用 Accept: application/vnd.github.v3.diff 拿标准 unified diff;
 * 行级评论走 pulls/{n}/comments (path + line + side=RIGHT, 比 GitLab 的 position 简单)。
 */
@Component
public class GitHubProvider implements VcsProvider {

    private static final Logger log = LoggerFactory.getLogger(GitHubProvider.class);
    private static final String API_BASE = "https://api.github.com";

    private final RuntimeConfigService configService;
    private final ObjectMapper objectMapper;

    public GitHubProvider(RuntimeConfigService configService, ObjectMapper objectMapper) {
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String platform() {
        return "github";
    }

    @Override
    public Optional<PrTask> parseWebhook(HttpServletRequest request, String rawBody) {
        String secret = configService.current().getGithub().getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new WebhookAuthException("未配置 GitHub Webhook Secret, 拒绝接收");
        }
        if (!verifySignature(secret, rawBody, request.getHeader("X-Hub-Signature-256"))) {
            throw new WebhookAuthException("GitHub Webhook 签名校验失败");
        }
        if (!"pull_request".equals(request.getHeader("X-GitHub-Event"))) {
            return Optional.empty();
        }
        try {
            return decide(objectMapper.readTree(rawBody));
        } catch (Exception e) {
            log.warn("GitHub webhook 报文解析失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** HMAC-SHA256 验签: 头格式 "sha256=<hex>" */
    static boolean verifySignature(String secret, String rawBody, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            // 常数时间比较, 防时序攻击
            return java.security.MessageDigest.isEqual(
                    hex.toString().getBytes(StandardCharsets.UTF_8),
                    signatureHeader.substring("sha256=".length()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    /** 触发规则: pull_request 事件, action = opened / synchronize / reopened, 且 PR 处于 open */
    static Optional<PrTask> decide(JsonNode payload) {
        String action = payload.path("action").asText();
        if (!"opened".equals(action) && !"synchronize".equals(action) && !"reopened".equals(action)) {
            return Optional.empty();
        }
        JsonNode pr = payload.path("pull_request");
        if (!"open".equals(pr.path("state").asText())) {
            return Optional.empty();
        }
        String repo = payload.path("repository").path("full_name").asText("");
        long number = payload.path("number").asLong(pr.path("number").asLong(0));
        String sha = pr.path("head").path("sha").asText("");
        if (repo.isBlank() || number <= 0 || sha.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new PrTask("github", repo, number,
                sha, pr.path("user").path("login").asText(null)));
    }

    @Override
    public PrChanges fetchChanges(PrTask task) {
        JsonNode pr = client().get()
                .uri("/repos/{repo}/pulls/{n}", task.repoKey(), task.prNumber())
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(JsonNode.class);
        String diff = client().get()
                .uri("/repos/{repo}/pulls/{n}", task.repoKey(), task.prNumber())
                .header("Accept", "application/vnd.github.v3.diff")
                .retrieve()
                .body(String.class);
        if (pr == null || diff == null) {
            throw new IllegalStateException("GitHub 返回为空");
        }
        String baseSha = pr.path("base").path("sha").asText(null);
        String headSha = pr.path("head").path("sha").asText(null);
        return new PrChanges(diff, baseSha, baseSha, headSha,
                pr.path("title").asText(""), pr.path("html_url").asText(null));
    }

    @Override
    public void postSummary(PrTask task, String markdownBody) {
        client().post()
                .uri("/repos/{repo}/issues/{n}/comments", task.repoKey(), task.prNumber())
                .header("Content-Type", "application/json")
                .body(Map.of("body", markdownBody))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public boolean postLineComment(PrTask task, PrChanges refs, String newPath, int newLine, String markdownBody) {
        try {
            client().post()
                    .uri("/repos/{repo}/pulls/{n}/comments", task.repoKey(), task.prNumber())
                    .header("Content-Type", "application/json")
                    .body(Map.of(
                            "body", markdownBody,
                            "commit_id", refs.headSha(),
                            "path", newPath,
                            "line", newLine,
                            "side", "RIGHT"))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("GitHub 行级评论失败({}:{}), 降级进总结: {}", newPath, newLine, e.getMessage());
            return false;
        }
    }

    private RestClient client() {
        RuntimeConfig.Github github = configService.current().getGithub();
        if (github.getToken() == null || github.getToken().isBlank()) {
            throw new IllegalStateException("未配置 GitHub Token, 请在「系统配置」页填写");
        }
        return RestClient.builder()
                .baseUrl(API_BASE)
                .defaultHeader("Authorization", "Bearer " + github.getToken())
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }
}
