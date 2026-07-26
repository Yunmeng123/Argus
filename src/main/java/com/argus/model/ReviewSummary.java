package com.argus.model;

import java.time.Instant;
import java.util.Map;

/** 审查记录列表项 */
public record ReviewSummary(
        String reviewId,
        Instant createdAt,
        String model,
        String source,
        String author,
        int totalFiles,
        int reviewedFiles,
        int findingCount,
        int totalTokens,
        Integer score,
        Map<String, Integer> severityCounts) {
}
