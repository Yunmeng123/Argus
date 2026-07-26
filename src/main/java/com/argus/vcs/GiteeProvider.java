package com.argus.vcs;

import java.util.Map;
import java.util.Optional;

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
 * Gitee 适配: X-Gitee-Token 密码模式验签; v5 API 取 PR files 拼 diff。
 * Gitee 行级评论的 position 语义与 diff 偏移耦合较深, 第一版降级为只发总结评论
 * (postLineComment 恒返回 false, 所有发现汇入总结)。
 */
@Component
public class GiteeProvider implements VcsProvider {

    private static final Logger log = LoggerFactory.getLogger(GiteeProvider.class);
    private static final String API_BASE = "https://gitee.com";

    private final RuntimeConfigService configService;
    private final ObjectMapper objectMapper;

    public GiteeProvider(RuntimeConfigService configService, ObjectMapper objectMapper) {
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String platform() {
        return "gitee";
    }

    @Override
    public Optional<PrTask> parseWebhook(HttpServletRequest request, String rawBody) {
        String secret = configService.current().getGitee().getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new WebhookAuthException("未配置 Gitee Webhook 密码, 拒绝接收");
        }
        if (!secret.equals(request.getHeader("X-Gitee-Token"))) {
            throw new WebhookAuthException("Gitee Webhook 密码校验失败");
        }
        String event = request.getHeader("X-Gitee-Event");
        if (event == null || !event.toLowerCase().contains("merge request")) {
            return Optional.empty();
        }
        try {
            return decide(objectMapper.readTree(rawBody));
        } catch (Exception e) {
            log.warn("Gitee webhook 报文解析失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Gitee MR Hook: action open/update, 从 pull_request 节点取编号/头 sha/作者 */
    static Optional<PrTask> decide(JsonNode payload) {
        String action = payload.path("action").asText();
        if (!"open".equals(action) && !"update".equals(action) && !"reopen".equals(action)) {
            return Optional.empty();
        }
        JsonNode pr = payload.path("pull_request");
        String repo = payload.path("repository").path("full_name").asText("");
        if (repo.isBlank()) {
            repo = payload.path("project").path("path_with_namespace").asText("");
        }
        long number = pr.path("number").asLong(payload.path("iid").asLong(0));
        String sha = pr.path("head").path("sha").asText("");
        if (repo.isBlank() || number <= 0 || sha.isBlank()) {
            return Optional.empty();
        }
        String author = pr.path("user").path("login").asText(null);
        if (author == null || author.isBlank()) {
            author = pr.path("user").path("username").asText(null);
        }
        return Optional.of(new PrTask("gitee", repo, number, sha, author));
    }

    @Override
    public PrChanges fetchChanges(PrTask task) {
        String token = requireToken();
        JsonNode pr = client().get()
                .uri("/api/v5/repos/{repo}/pulls/{n}?access_token={t}", task.repoKey(), task.prNumber(), token)
                .retrieve()
                .body(JsonNode.class);
        JsonNode files = client().get()
                .uri("/api/v5/repos/{repo}/pulls/{n}/files?access_token={t}", task.repoKey(), task.prNumber(), token)
                .retrieve()
                .body(JsonNode.class);
        if (pr == null || files == null) {
            throw new IllegalStateException("Gitee 返回为空");
        }
        return new PrChanges(assembleDiffFromFiles(files),
                pr.path("base").path("sha").asText(null),
                pr.path("base").path("sha").asText(null),
                pr.path("head").path("sha").asText(null),
                pr.path("title").asText(""),
                pr.path("html_url").asText(null));
    }

    /** Gitee files API 的 patch 可能是字符串或 {diff: "..."} 对象, 两种都兼容 */
    static String assembleDiffFromFiles(JsonNode files) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode file : files) {
            String filename = file.path("filename").asText("");
            JsonNode patch = file.path("patch");
            String diff = patch.isTextual() ? patch.asText("") : patch.path("diff").asText("");
            if (filename.isBlank() || diff.isBlank()) {
                continue;
            }
            String status = file.path("status").asText("");
            sb.append("diff --git a/").append(filename).append(" b/").append(filename).append('\n');
            if ("added".equals(status)) {
                sb.append("new file mode 100644\n--- /dev/null\n");
            } else {
                sb.append("--- a/").append(filename).append('\n');
            }
            if ("removed".equals(status) || "deleted".equals(status)) {
                sb.append("+++ /dev/null\n");
            } else {
                sb.append("+++ b/").append(filename).append('\n');
            }
            sb.append(diff);
            if (!diff.endsWith("\n")) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    @Override
    public void postSummary(PrTask task, String markdownBody) {
        client().post()
                .uri("/api/v5/repos/{repo}/pulls/{n}/comments", task.repoKey(), task.prNumber())
                .header("Content-Type", "application/json")
                .body(Map.of("access_token", requireToken(), "body", markdownBody))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public boolean postLineComment(PrTask task, PrChanges refs, String newPath, int newLine, String markdownBody) {
        // Gitee 行级评论依赖 diff position 偏移, 第一版统一降级进总结评论
        return false;
    }

    private String requireToken() {
        RuntimeConfig.Gitee gitee = configService.current().getGitee();
        if (gitee.getToken() == null || gitee.getToken().isBlank()) {
            throw new IllegalStateException("未配置 Gitee Token, 请在「系统配置」页填写");
        }
        return gitee.getToken();
    }

    private RestClient client() {
        return RestClient.builder().baseUrl(API_BASE).build();
    }
}
