package com.argus.llm;

import java.util.List;

/**
 * 单文件审查结果。score/summary 由模型给出, 可能为 null(由上层做兜底推导)。
 */
public record LlmReviewOutcome(List<RawFinding> findings, TokenUsage tokenUsage, Integer score, String summary) {
}
