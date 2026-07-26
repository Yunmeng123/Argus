package com.argus.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * LLM 返回的原始问题条目, 未经行号校验与规整。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RawFinding(
        Integer line,
        String severity,
        String category,
        String title,
        String detail,
        String suggestion,
        Double confidence) {
}
