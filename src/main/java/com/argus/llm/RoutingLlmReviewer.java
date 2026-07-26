package com.argus.llm;

import com.argus.config.RuntimeConfigService;
import com.argus.diff.FileDiff;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 按运行时配置的 mock 开关路由到真实/模拟实现, 业务层只依赖 LlmReviewer 接口。
 * 后续做级联模型(小模型初筛+大模型复核)时也在这一层扩展。
 */
@Primary
@Component
public class RoutingLlmReviewer implements LlmReviewer {

    private final RuntimeConfigService configService;
    private final SpringAiReviewer realReviewer;
    private final MockLlmReviewer mockReviewer;

    public RoutingLlmReviewer(RuntimeConfigService configService,
                              SpringAiReviewer realReviewer,
                              MockLlmReviewer mockReviewer) {
        this.configService = configService;
        this.realReviewer = realReviewer;
        this.mockReviewer = mockReviewer;
    }

    @Override
    public LlmReviewOutcome review(FileDiff fileDiff, String systemPrompt, String userPrompt) {
        LlmReviewer delegate = configService.current().getLlm().isMock() ? mockReviewer : realReviewer;
        return delegate.review(fileDiff, systemPrompt, userPrompt);
    }
}
