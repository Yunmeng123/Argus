package com.argus.vcs;

import java.util.HashMap;
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
 * GitLab 适配: X-Gitlab-Token 明文验签; changes API 手工拼 diff 头;
 * 行级评论用 discussions position(base/start/head 三 sha + new_line)。
 */
@Component
public class GitLabProvider implements VcsProvider {

    private static final Logger log = LoggerFactory.getLogger(GitLabProvider.class);

    private final RuntimeConfigService configService;
    private final ObjectMapper objectMapper;

    public GitLabProvider(RuntimeConfigService configService, ObjectMapper objectMapper) {
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String platform() {
        return "gitlab";
    }

    @Override
    public Optional<PrTask> parseWebhook(HttpServletRequest request, String rawBody) {
        String secret = configService.current().getGitlab().getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new WebhookAuthException("未配置 GitLab Webhook Secret, 拒绝接收");
        }
        if (!secret.equals(request.getHeader("X-Gitlab-Token"))) {
            throw new WebhookAuthException("GitLab Webhook Token 校验失败");
        }
        try {
            return decide(objectMapper.readTree(rawBody));
        } catch (Exception e) {
            log.warn("GitLab webhook 报文解析失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 触发规则: merge_request 事件, 状态 opened, 动作 open/reopen/update;
     * update 必须带 oldrev(源分支有新代码), 排除改标题/打标签等噪音事件。
     */
    static Optional<PrTask> decide(JsonNode payload) {
        if (!"merge_request".equals(payload.path("object_kind").asText())) {
            return Optional.empty();
        }
        JsonNode attrs = payload.path("object_attributes");
        String action = attrs.path("action").asText();
        if (!"opened".equals(attrs.path("state").asText())) {
            return Optional.empty();
        }
        boolean triggering = switch (action) {
            case "open", "reopen" -> true;
            case "update" -> attrs.hasNonNull("oldrev");
            default -> false;
        };
        if (!triggering) {
            return Optional.empty();
        }
        long projectId = payload.path("project").path("id").asLong(0);
        long mrIid = attrs.path("iid").asLong(0);
        String sha = attrs.path("last_commit").path("id").asText("");
        if (projectId <= 0 || mrIid <= 0 || sha.isBlank()) {
            return Optional.empty();
        }
        String author = payload.path("user").path("username").asText(null);
        if (author == null || author.isBlank()) {
            author = payload.path("user").path("name").asText(null);
        }
        return Optional.of(new PrTask("gitlab", String.valueOf(projectId), mrIid, sha, author));
    }

    @Override
    public PrChanges fetchChanges(PrTask task) {
        JsonNode body = client().get()
                .uri("/api/v4/projects/{p}/merge_requests/{m}/changes", task.repoKey(), task.prNumber())
                .retrieve()
                .body(JsonNode.class);
        if (body == null) {
            throw new IllegalStateException("GitLab 返回为空");
        }
        JsonNode diffRefs = body.path("diff_refs");
        return new PrChanges(
                assembleUnifiedDiff(body.path("changes")),
                diffRefs.path("base_sha").asText(null),
                diffRefs.path("start_sha").asText(null),
                diffRefs.path("head_sha").asText(null),
                body.path("title").asText(""),
                body.path("web_url").asText(null));
    }

    /** GitLab changes[].diff 只含 hunk 内容, 补上标准 diff 头以复用 UnifiedDiffParser */
    static String assembleUnifiedDiff(JsonNode changes) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode change : changes) {
            String oldPath = change.path("old_path").asText("");
            String newPath = change.path("new_path").asText("");
            String diff = change.path("diff").asText("");
            if (diff.isBlank()) {
                continue;
            }
            if (diff.startsWith("diff --git") || diff.startsWith("--- ")) {
                sb.append(diff.startsWith("diff --git") ? "" : "diff --git a/" + oldPath + " b/" + newPath + "\n")
                        .append(diff);
            } else {
                sb.append("diff --git a/").append(oldPath).append(" b/").append(newPath).append('\n');
                if (change.path("new_file").asBoolean(false)) {
                    sb.append("new file mode 100644\n--- /dev/null\n");
                } else {
                    sb.append("--- a/").append(oldPath).append('\n');
                }
                if (change.path("deleted_file").asBoolean(false)) {
                    sb.append("+++ /dev/null\n");
                } else {
                    sb.append("+++ b/").append(newPath).append('\n');
                }
                sb.append(diff);
            }
            if (!diff.endsWith("\n")) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    @Override
    public void postSummary(PrTask task, String markdownBody) {
        client().post()
                .uri("/api/v4/projects/{p}/merge_requests/{m}/notes", task.repoKey(), task.prNumber())
                .header("Content-Type", "application/json")
                .body(Map.of("body", markdownBody))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public boolean postLineComment(PrTask task, PrChanges refs, String newPath, int newLine, String markdownBody) {
        try {
            Map<String, Object> position = new HashMap<>();
            position.put("position_type", "text");
            position.put("base_sha", refs.baseSha());
            position.put("start_sha", refs.startSha());
            position.put("head_sha", refs.headSha());
            position.put("new_path", newPath);
            position.put("new_line", newLine);
            client().post()
                    .uri("/api/v4/projects/{p}/merge_requests/{m}/discussions", task.repoKey(), task.prNumber())
                    .header("Content-Type", "application/json")
                    .body(Map.of("body", markdownBody, "position", position))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("GitLab 行级评论失败({}:{}), 降级进总结: {}", newPath, newLine, e.getMessage());
            return false;
        }
    }

    private RestClient client() {
        RuntimeConfig.Gitlab gitlab = configService.current().getGitlab();
        if (gitlab.getBaseUrl() == null || gitlab.getBaseUrl().isBlank()
                || gitlab.getToken() == null || gitlab.getToken().isBlank()) {
            throw new IllegalStateException("未配置 GitLab 地址或 Token, 请在「系统配置」页填写");
        }
        return RestClient.builder()
                .baseUrl(gitlab.getBaseUrl())
                .defaultHeader("PRIVATE-TOKEN", gitlab.getToken())
                .build();
    }
}
