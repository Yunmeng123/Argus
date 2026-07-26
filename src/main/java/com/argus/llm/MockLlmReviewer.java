package com.argus.llm;

import java.util.List;

import com.argus.diff.FileDiff;

import org.springframework.stereotype.Component;

/**
 * mock 模式: 不调用真实 LLM, 返回固定示例结果, 用于无 API Key 时验证整条链路。
 * 是否启用由 RuntimeConfigService 的 mock 开关决定(见 RoutingLlmReviewer)。
 */
@Component
public class MockLlmReviewer implements LlmReviewer {

    @Override
    public LlmReviewOutcome review(FileDiff fileDiff, String systemPrompt, String userPrompt) {
        int line = fileDiff.addedLineNumbers().stream().findFirst().orElse(1);
        RawFinding finding = new RawFinding(line, "INFO", "STYLE",
                "[Mock] 示例审查结果",
                "当前为 mock 模式, 未调用真实模型, 此条目仅用于验证链路。",
                "在「系统配置」页关闭 mock 模式并配置 API Key 后可获得真实审查结果。",
                1.0);
        return new LlmReviewOutcome(List.of(finding), TokenUsage.empty(), 92,
                "[Mock] 链路验证通过, 未调用真实模型");
    }
}
