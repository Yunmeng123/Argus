package com.argus.report;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.argus.llm.TokenUsage;
import com.argus.model.Finding;
import com.argus.model.Severity;
import com.argus.model.SkippedFile;

import org.springframework.stereotype.Component;

/**
 * 把审查结果渲染成 Markdown 报告(按文件分组), 后续接 GitLab 时同一份数据改渲染为 MR 评论。
 */
@Component
public class MarkdownReportRenderer {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public String render(String reviewId, Instant createdAt, String model, List<Finding> findings,
                         List<SkippedFile> skipped, Map<String, Integer> severityCounts, TokenUsage usage,
                         Integer score, String summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 代码审查报告 ").append(reviewId).append("\n\n");
        if (score != null) {
            sb.append("## 质量评分: ").append(score).append(" / 100\n\n");
        }
        if (summary != null && !summary.isBlank()) {
            sb.append("> ").append(summary.replace("\n", "\n> ")).append("\n\n");
        }
        sb.append("- 时间: ").append(TIME_FORMAT.format(createdAt)).append('\n');
        sb.append("- 模型: ").append(model).append('\n');
        sb.append("- Token: prompt=").append(usage.promptTokens())
                .append(", completion=").append(usage.completionTokens())
                .append(", total=").append(usage.totalTokens()).append("\n\n");

        sb.append("## 问题统计\n\n");
        if (findings.isEmpty()) {
            sb.append("未发现问题。\n\n");
        } else {
            sb.append("| 严重程度 | 数量 |\n|---|---|\n");
            severityCounts.forEach((severity, count) ->
                    sb.append("| ").append(icon(severity)).append(' ').append(severity)
                            .append(" | ").append(count).append(" |\n"));
            sb.append('\n');
        }

        List<Finding> byFile = findings.stream()
                .sorted(Comparator.comparing(Finding::file)
                        .thenComparing(Finding::severity)
                        .thenComparingInt(Finding::line))
                .toList();
        String currentFile = null;
        for (Finding finding : byFile) {
            if (!finding.file().equals(currentFile)) {
                currentFile = finding.file();
                sb.append("## ").append(currentFile).append("\n\n");
            }
            sb.append("### ").append(icon(finding.severity().name()))
                    .append(" [").append(finding.severity()).append("] ")
                    .append(finding.title()).append("\n\n");
            sb.append("- 位置: 第 ").append(finding.line()).append(" 行");
            if (!finding.lineVerified()) {
                sb.append(" (行号未能与 diff 新增行对齐, 仅供参考)");
            }
            sb.append('\n');
            sb.append("- 分类: ").append(finding.category())
                    .append(" | 置信度: ").append(String.format("%.2f", finding.confidence())).append('\n');
            if (!finding.detail().isEmpty()) {
                sb.append("- 说明: ").append(finding.detail()).append('\n');
            }
            if (!finding.suggestion().isEmpty()) {
                sb.append("- 建议: ").append(finding.suggestion()).append('\n');
            }
            sb.append('\n');
        }

        if (!skipped.isEmpty()) {
            sb.append("## 跳过的文件\n\n| 文件 | 原因 |\n|---|---|\n");
            for (SkippedFile skippedFile : skipped) {
                sb.append("| ").append(skippedFile.path()).append(" | ")
                        .append(skippedFile.reason()).append(" |\n");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String icon(String severity) {
        return switch (Severity.from(severity)) {
            case BLOCKER -> "🔴";
            case MAJOR -> "🟠";
            case MINOR -> "🟡";
            case INFO -> "🟢";
        };
    }
}
