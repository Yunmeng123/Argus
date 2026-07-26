package com.argus.llm;

import java.util.ArrayList;
import java.util.List;

import com.argus.config.RuntimeConfig;
import com.argus.config.RuntimeConfigService;
import com.argus.model.Finding;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Finder-Verifier 两段式的第二段: 对 Finder 的发现做"反驳式"复核, 剔除站不住脚的误报。
 * 可配置独立的复核模型(级联: 便宜模型初筛 + 强模型复核, 或反过来)。
 * 一个文件的所有发现合并为一次调用, 控制成本。
 */
@Component
public class FindingVerifier {

    private static final Logger log = LoggerFactory.getLogger(FindingVerifier.class);

    private final LlmChat llmChat;
    private final RuntimeConfigService configService;
    private final ObjectMapper objectMapper;

    public FindingVerifier(LlmChat llmChat, RuntimeConfigService configService, ObjectMapper objectMapper) {
        this.llmChat = llmChat;
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    public record VerifyOutcome(List<Finding> kept, int dropped, TokenUsage usage) {
    }

    /**
     * @param fileDiffPrompt Finder 阶段使用的同一份 diff 上下文(带行号标注)
     */
    public VerifyOutcome verify(String fileDiffPrompt, List<Finding> findings) {
        if (findings.isEmpty()) {
            return new VerifyOutcome(findings, 0, TokenUsage.empty());
        }
        RuntimeConfig.Llm llm = configService.current().getLlm();
        try {
            LlmChat.ChatOutcome outcome = llmChat.complete(systemPrompt(),
                    userPrompt(fileDiffPrompt, findings), llm.getVerifierModel());
            List<RawVerdict> verdicts = parseVerdicts(outcome.text());
            List<Finding> kept = new ArrayList<>();
            int dropped = 0;
            for (int i = 0; i < findings.size(); i++) {
                RawVerdict verdict = verdictFor(verdicts, i);
                if (verdict != null && "REJECTED".equalsIgnoreCase(verdict.verdict())) {
                    dropped++;
                    log.info("Verifier 剔除误报: {} (理由: {})", findings.get(i).title(), verdict.reason());
                } else {
                    kept.add(findings.get(i));
                }
            }
            return new VerifyOutcome(kept, dropped, outcome.usage());
        } catch (Exception e) {
            // 复核失败不应吞掉 Finder 结果, 降级为不过滤
            log.warn("Verifier 复核失败, 保留原始发现: {}", e.getMessage());
            return new VerifyOutcome(findings, 0, TokenUsage.empty());
        }
    }

    private String systemPrompt() {
        return """
                你是代码审查结果的质检员。给你一份 diff 和另一位审查者报告的问题清单,
                你的任务是逐条【尝试反驳】: 只有当问题明显不成立(行号指向的代码不存在该问题、
                纯属臆测 diff 之外的代码、重复报告、与语言/框架的实际语义不符)时才判 REJECTED,
                有合理怀疑但无法确定时判 UNCERTAIN, 确认存在判 CONFIRMED。
                不要过度否定: 真实的 BUG/安全问题被误删的代价远大于保留一条存疑问题。

                输出纯 JSON, 不要 markdown 围栏:
                {"verdicts":[{"index":0,"verdict":"CONFIRMED|REJECTED|UNCERTAIN","reason":"一句话理由"}]}
                verdicts 数量必须与问题清单条数一致, index 从 0 开始。
                """;
    }

    private String userPrompt(String fileDiffPrompt, List<Finding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append(fileDiffPrompt).append("\n待复核的问题清单:\n");
        for (int i = 0; i < findings.size(); i++) {
            Finding finding = findings.get(i);
            sb.append(i).append(". [").append(finding.severity()).append('/').append(finding.category())
                    .append("] 第 ").append(finding.line()).append(" 行: ").append(finding.title())
                    .append(" — ").append(finding.detail()).append('\n');
        }
        return sb.toString();
    }

    private List<RawVerdict> parseVerdicts(String text) throws Exception {
        String json = SpringAiReviewer.extractJson(text);
        VerdictsPayload payload = objectMapper.readValue(json, VerdictsPayload.class);
        return payload.verdicts();
    }

    private RawVerdict verdictFor(List<RawVerdict> verdicts, int index) {
        for (RawVerdict verdict : verdicts) {
            if (verdict != null && verdict.index() != null && verdict.index() == index) {
                return verdict;
            }
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VerdictsPayload(List<RawVerdict> verdicts) {
        VerdictsPayload {
            verdicts = verdicts == null ? List.of() : verdicts;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawVerdict(Integer index, String verdict, String reason) {
    }
}
