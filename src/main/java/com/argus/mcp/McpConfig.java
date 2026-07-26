package com.argus.mcp;

import com.argus.review.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 装配: 把审查能力注册为 MCP 工具,
 * Claude Code/Cursor 等客户端通过 SSE(http://host:18080/sse) 即可调用。
 */
@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider argusToolCallbacks(ReviewService reviewService, ObjectMapper objectMapper) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(new ArgusMcpTools(reviewService, objectMapper))
                .build();
    }
}
