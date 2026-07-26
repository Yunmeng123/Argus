package com.argus.model;

/**
 * 一条审查发现。
 *
 * @param lineVerified 该行号是否确实是 diff 中的新增行(LLM 报告的行号可能漂移,
 *                     未对齐的行号后续无法用于行级评论, 只能降级为文件级)
 */
public record Finding(
        String file,
        int line,
        boolean lineVerified,
        Severity severity,
        String category,
        String title,
        String detail,
        String suggestion,
        double confidence) {
}
