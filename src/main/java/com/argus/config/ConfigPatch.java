package com.argus.config;

/**
 * 配置合并式更新的入参。语义:
 * - null = 保持原值
 * - 机密字段(各平台 token/webhookSecret/llmApiKey): 空白也视为保持原值(避免前端掩码回显误清空)
 * - 可清空字段(verifierModel/gitlabBaseUrl/notifyWebhookUrl): 非 null 即覆盖, 传 "" 表示清空
 */
public record ConfigPatch(
        String llmBaseUrl,
        String llmApiKey,
        String llmModel,
        String llmVerifierModel,
        Double temperature,
        Boolean mock,
        Integer maxFileChangedLines,
        Integer maxFindingsPerFile,
        Boolean verifierEnabled,
        Integer dailyTokenBudget,
        String gitlabBaseUrl,
        String gitlabToken,
        String gitlabWebhookSecret,
        String githubToken,
        String githubWebhookSecret,
        String giteeToken,
        String giteeWebhookSecret,
        String notifyWebhookUrl) {
}
