package com.argus.llm;

import com.argus.config.RuntimeConfig;
import com.argus.config.RuntimeConfigService;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

/**
 * LLM 通用调用入口: 依据运行时配置手动构建客户端并按配置版本缓存,
 * 支持按调用覆盖模型名(Finder 主模型 / Verifier 复核模型的级联就靠这个)。
 */
@Component
public class LlmChat {

    private final RuntimeConfigService configService;

    private volatile OpenAiChatModel cachedModel;
    private volatile long cachedVersion = -1;

    public LlmChat(RuntimeConfigService configService) {
        this.configService = configService;
    }

    public record ChatOutcome(String text, TokenUsage usage) {
    }

    /** modelOverride 为空时使用配置的主模型 */
    public ChatOutcome complete(String systemPrompt, String userPrompt, String modelOverride) {
        RuntimeConfig.Llm llm = configService.current().getLlm();
        String modelName = (modelOverride == null || modelOverride.isBlank()) ? llm.getModel() : modelOverride;
        ChatResponse response = ChatClient.create(model(llm)).prompt()
                .options(OpenAiChatOptions.builder()
                        .model(modelName)
                        .temperature(llm.getTemperature())
                        .build())
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .chatResponse();
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("LLM 返回为空");
        }
        return new ChatOutcome(response.getResult().getOutput().getText(), extractUsage(response));
    }

    private OpenAiChatModel model(RuntimeConfig.Llm llm) {
        long version = configService.version();
        OpenAiChatModel model = cachedModel;
        if (model == null || cachedVersion != version) {
            synchronized (this) {
                if (cachedModel == null || cachedVersion != configService.version()) {
                    cachedModel = build(llm);
                    cachedVersion = configService.version();
                }
                model = cachedModel;
            }
        }
        return model;
    }

    private OpenAiChatModel build(RuntimeConfig.Llm llm) {
        if (llm.getApiKey() == null || llm.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "未配置 LLM API Key: 请在「系统配置」页填写, 或设置环境变量 ARGUS_LLM_API_KEY, 或开启 mock 模式");
        }
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(llm.getBaseUrl())
                .apiKey(llm.getApiKey())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(llm.getModel())
                        .temperature(llm.getTemperature())
                        .build())
                // 显式重试策略: 供应商限流/抖动最多重试 3 次, 指数退避 1s→2s→4s(上限 8s)
                .retryTemplate(org.springframework.retry.support.RetryTemplate.builder()
                        .maxAttempts(3)
                        .exponentialBackoff(1000, 2.0, 8000)
                        .build())
                .build();
    }

    private TokenUsage extractUsage(ChatResponse response) {
        Usage usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
        if (usage == null) {
            return TokenUsage.empty();
        }
        return new TokenUsage(nvl(usage.getPromptTokens()), nvl(usage.getCompletionTokens()), nvl(usage.getTotalTokens()));
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
