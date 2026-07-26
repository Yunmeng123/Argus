package com.argus.llm;

import java.util.List;

import com.argus.diff.FileDiff;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 基于 LlmChat 的真实审查实现(Finder 阶段), 使用配置的主模型。
 */
@Component
public class SpringAiReviewer implements LlmReviewer {

    private static final Logger log = LoggerFactory.getLogger(SpringAiReviewer.class);

    private final LlmChat llmChat;
    private final ObjectMapper objectMapper;

    public SpringAiReviewer(LlmChat llmChat, ObjectMapper objectMapper) {
        this.llmChat = llmChat;
        this.objectMapper = objectMapper;
    }

    @Override
    public LlmReviewOutcome review(FileDiff fileDiff, String systemPrompt, String userPrompt) {
        LlmChat.ChatOutcome outcome = llmChat.complete(systemPrompt, userPrompt, null);
        FindingsPayload payload = parsePayload(outcome.text(), fileDiff.displayPath());
        return new LlmReviewOutcome(payload.findings(), outcome.usage(), payload.score(), payload.summary());
    }

    private FindingsPayload parsePayload(String text, String path) {
        String json = extractJson(text);
        try {
            return objectMapper.readValue(json, FindingsPayload.class);
        } catch (Exception e) {
            log.warn("解析 LLM 审查结果失败, file={}, 原始输出:\n{}", path, text);
            throw new IllegalStateException("LLM 输出不是合法 JSON: " + e.getMessage(), e);
        }
    }

    /** 容错: 去掉可能出现的 markdown 围栏/前后缀文字, 截取首个 '{' 到最后一个 '}' */
    static String extractJson(String text) {
        String trimmed = text == null ? "" : text.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FindingsPayload(Integer score, String summary, List<RawFinding> findings) {
        FindingsPayload {
            findings = findings == null ? List.of() : findings;
        }
    }
}
