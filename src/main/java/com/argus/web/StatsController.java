package com.argus.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.argus.model.ReviewSummary;
import com.argus.report.ReviewStore;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计看板数据源: 基于近 200 条审查记录聚合(与列表页同一查询, 足够看板用)。
 */
@RestController
public class StatsController {

    private final ReviewStore reviewStore;

    public StatsController(ReviewStore reviewStore) {
        this.reviewStore = reviewStore;
    }

    public record StatsResponse(int totalReviews, int totalFindings, long totalTokens,
                                Map<String, Integer> severityTotals,
                                Map<String, Integer> sourceTotals,
                                List<ReviewSummary> recent) {
    }

    @GetMapping("/api/stats")
    public StatsResponse stats() {
        List<ReviewSummary> summaries = reviewStore.list();
        int totalFindings = 0;
        long totalTokens = 0;
        Map<String, Integer> severityTotals = new LinkedHashMap<>();
        Map<String, Integer> sourceTotals = new LinkedHashMap<>();
        for (ReviewSummary summary : summaries) {
            totalFindings += summary.findingCount();
            totalTokens += summary.totalTokens();
            summary.severityCounts().forEach((severity, count) -> severityTotals.merge(severity, count, Integer::sum));
            sourceTotals.merge(summary.source(), 1, Integer::sum);
        }
        List<ReviewSummary> recent = summaries.size() > 10 ? summaries.subList(0, 10) : summaries;
        return new StatsResponse(summaries.size(), totalFindings, totalTokens, severityTotals, sourceTotals, recent);
    }
}
