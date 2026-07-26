package com.argus.llm;

public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {

    public static TokenUsage empty() {
        return new TokenUsage(0, 0, 0);
    }

    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(
                promptTokens + other.promptTokens,
                completionTokens + other.completionTokens,
                totalTokens + other.totalTokens);
    }
}
