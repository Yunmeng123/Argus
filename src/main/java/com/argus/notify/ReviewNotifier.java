package com.argus.notify;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.argus.config.RuntimeConfigService;
import com.argus.model.ReviewResult;
import com.argus.review.ReviewOrigin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 审查完成 IM 通知: 支持钉钉/企微自定义机器人 webhook(按 URL 自动识别报文格式)。
 * 通知失败只记日志, 绝不影响审查主流程。
 */
@Component
public class ReviewNotifier {

    private static final Logger log = LoggerFactory.getLogger(ReviewNotifier.class);

    private final RuntimeConfigService configService;

    public ReviewNotifier(RuntimeConfigService configService) {
        this.configService = configService;
    }

    public void notifyResult(ReviewResult result, ReviewOrigin origin) {
        String webhookUrl = configService.current().getNotify().getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank() || "EVAL".equals(origin.source())) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                String markdown = buildMarkdown(result, origin);
                Object body = webhookUrl.contains("qyapi.weixin.qq.com")
                        ? Map.of("msgtype", "markdown", "markdown", Map.of("content", markdown))
                        : Map.of("msgtype", "markdown", "markdown",
                                Map.of("title", "Argus 审查完成", "text", markdown));
                RestClient.create().post().uri(webhookUrl)
                        .header("Content-Type", "application/json")
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                log.warn("IM 通知发送失败: {}", e.getMessage());
            }
        });
    }

    private String buildMarkdown(ReviewResult result, ReviewOrigin origin) {
        StringBuilder sb = new StringBuilder("### Argus 审查完成\n");
        if (origin.mrTitle() != null) {
            sb.append("> MR: ").append(origin.mrTitle()).append('\n');
        }
        if (origin.mrUrl() != null) {
            sb.append("> ").append(origin.mrUrl()).append('\n');
        }
        if (result.score() != null) {
            sb.append("- 质量评分: **").append(result.score()).append("** / 100\n");
        }
        sb.append("- 审查 ID: ").append(result.reviewId()).append('\n');
        sb.append("- 文件: ").append(result.reviewedFiles()).append('/').append(result.totalFiles()).append('\n');
        sb.append("- 发现问题: ").append(result.findings().size());
        if (!result.severityCounts().isEmpty()) {
            sb.append(" (");
            result.severityCounts().forEach((severity, count) ->
                    sb.append(severity).append('x').append(count).append(' '));
            sb.append(')');
        }
        sb.append('\n');
        return sb.toString();
    }
}
