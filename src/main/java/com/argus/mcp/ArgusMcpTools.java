package com.argus.mcp;

import com.argus.review.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 暴露给 MCP 客户端的工具集。返回值是给 AI 读的, 直接给结构化 JSON。
 */
public class ArgusMcpTools {

    private final ReviewService reviewService;
    private final ObjectMapper objectMapper;

    public ArgusMcpTools(ReviewService reviewService, ObjectMapper objectMapper) {
        this.reviewService = reviewService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "argus_review_diff",
            description = "对一段 git unified diff 做 AI 代码审查, 返回结构化的问题列表 JSON"
                    + "(含文件/行号/严重程度/说明/修复建议)")
    public String reviewDiff(@ToolParam(description = "unified diff 文本, 即 git diff 的输出") String diff) {
        try {
            return objectMapper.writeValueAsString(reviewService.review(diff));
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
