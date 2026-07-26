package com.argus.vcs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.argus.model.Finding;
import com.argus.model.ReviewResult;
import com.argus.model.Severity;
import com.argus.report.ReviewStore;
import com.argus.review.ReviewOrigin;
import com.argus.review.ReviewService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * PR 审查编排(平台无关): 幂等检查 -> 拉取变更 -> 审查 -> 行级评论 + 总结评论回写。
 * 行号未对齐(lineVerified=false)或平台不支持行级评论的发现降级进总结评论。
 */
@Service
public class PrReviewService {

    private static final Logger log = LoggerFactory.getLogger(PrReviewService.class);
    /** 行级评论上限, 防止问题过多时刷屏 */
    private static final int MAX_LINE_DISCUSSIONS = 20;

    private final Map<String, VcsProvider> providers;
    private final ReviewService reviewService;
    private final ReviewStore reviewStore;

    public PrReviewService(List<VcsProvider> providerList, ReviewService reviewService, ReviewStore reviewStore) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(VcsProvider::platform, Function.identity()));
        this.reviewService = reviewService;
        this.reviewStore = reviewStore;
    }

    public void process(PrTask task) {
        VcsProvider provider = providers.get(task.platform());
        if (provider == null) {
            throw new IllegalStateException("未找到平台适配器: " + task.platform());
        }
        if (reviewStore.existsVcsReview(task.platform().toUpperCase(), task.repoKey(),
                task.prNumber(), task.commitSha())) {
            log.info("该 commit 已审查过, 幂等跳过: {} sha={}", task.key(), task.commitSha());
            return;
        }
        PrChanges changes = provider.fetchChanges(task);
        if (changes.diffText().isBlank()) {
            log.info("PR 无可审查的文本变更: {}", task.key());
            return;
        }
        ReviewOrigin origin = ReviewOrigin.vcs(task.platform(), task.repoKey(), task.prNumber(),
                task.commitSha(), changes.webUrl(), changes.title(), task.author());
        ReviewResult result = reviewService.review(changes.diffText(), origin);

        List<Finding> unanchored = new ArrayList<>();
        int posted = 0;
        for (Finding finding : result.findings()) {
            boolean anchored = false;
            if (finding.lineVerified() && posted < MAX_LINE_DISCUSSIONS) {
                anchored = provider.postLineComment(task, changes,
                        finding.file(), finding.line(), lineCommentBody(finding));
                if (anchored) {
                    posted++;
                }
            }
            if (!anchored) {
                unanchored.add(finding);
            }
        }
        provider.postSummary(task, summaryBody(result, unanchored));
        log.info("PR 审查完成并回写: {} findings={} 行级={} 总结兜底={}",
                task.key(), result.findings().size(), posted, unanchored.size());
    }

    private String lineCommentBody(Finding finding) {
        return "**" + icon(finding.severity()) + " [" + finding.severity() + "/" + finding.category() + "] "
                + finding.title() + "**\n\n"
                + (finding.detail().isEmpty() ? "" : finding.detail() + "\n\n")
                + (finding.suggestion().isEmpty() ? "" : "建议: " + finding.suggestion() + "\n\n")
                + "<sub>置信度 " + String.format("%.2f", finding.confidence()) + " · by Argus</sub>";
    }

    private String summaryBody(ReviewResult result, List<Finding> unanchored) {
        StringBuilder sb = new StringBuilder("## 👁 Argus 代码审查\n\n");
        if (result.score() != null) {
            sb.append("**质量评分: ").append(result.score()).append(" / 100**\n\n");
        }
        sb.append("| 审查文件 | 问题 | Token |\n|---|---|---|\n");
        sb.append("| ").append(result.reviewedFiles()).append('/').append(result.totalFiles())
                .append(" | ").append(result.findings().size())
                .append(" | ").append(result.tokenUsage().totalTokens()).append(" |\n\n");
        if (result.summary() != null && !result.summary().isBlank()) {
            sb.append("> ").append(result.summary().replace("\n", "\n> ")).append("\n\n");
        }
        if (result.findings().isEmpty()) {
            sb.append("✅ 未发现问题。\n");
            return sb.toString();
        }
        result.severityCounts().forEach((severity, count) ->
                sb.append(icon(Severity.from(severity))).append(' ').append(severity)
                        .append(" ×").append(count).append("  "));
        sb.append('\n');
        if (result.verifierDropped() > 0) {
            sb.append("\n<sub>Verifier 已过滤 ").append(result.verifierDropped()).append(" 条疑似误报</sub>\n");
        }
        if (!unanchored.isEmpty()) {
            sb.append("\n### 未能行级锚定的问题\n\n");
            for (Finding finding : unanchored) {
                sb.append("- ").append(icon(finding.severity())).append(" **").append(finding.file())
                        .append(":").append(finding.line()).append("** ").append(finding.title()).append('\n');
            }
        }
        return sb.toString();
    }

    private String icon(Severity severity) {
        return switch (severity) {
            case BLOCKER -> "🔴";
            case MAJOR -> "🟠";
            case MINOR -> "🟡";
            case INFO -> "🟢";
        };
    }
}
