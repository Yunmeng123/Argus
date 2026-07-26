package com.argus.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.argus.llm.TokenUsage;

public record ReviewResult(
        String reviewId,
        Instant createdAt,
        String model,
        int totalFiles,
        int reviewedFiles,
        List<SkippedFile> skippedFiles,
        Map<String, Integer> severityCounts,
        List<Finding> findings,
        TokenUsage tokenUsage,
        Integer score,
        String summary,
        int verifierDropped,
        String reportPath) {
}
