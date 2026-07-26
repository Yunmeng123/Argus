package com.argus.review;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import com.argus.config.RuntimeConfig;
import com.argus.config.RuntimeConfigService;
import com.argus.context.ContextEnhancer;
import com.argus.diff.FileDiff;
import com.argus.diff.UnifiedDiffParser;
import com.argus.llm.FindingVerifier;
import com.argus.llm.LlmReviewOutcome;
import com.argus.llm.LlmReviewer;
import com.argus.llm.RawFinding;
import com.argus.llm.TokenUsage;
import com.argus.model.Finding;
import com.argus.model.ReviewResult;
import com.argus.model.Severity;
import com.argus.model.SkippedFile;
import com.argus.notify.ReviewNotifier;
import com.argus.report.MarkdownReportRenderer;
import com.argus.report.ReviewStore;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 审查主流程: 解析 diff -> 过滤 -> [并行] 上下文增强 + Finder 审查 + Verifier 复核
 * -> 行号校验 -> 汇总 + 报告 + 入库 + 通知。
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);
    /** 文件级并行度: 受 LLM 供应商限流约束, 不宜过大 */
    private static final int PARALLELISM = 4;

    private final UnifiedDiffParser parser = new UnifiedDiffParser();
    private final ReviewFileFilter fileFilter;
    private final PromptBuilder promptBuilder;
    private final LlmReviewer llmReviewer;
    private final FindingVerifier findingVerifier;
    private final ContextEnhancer contextEnhancer;
    private final MarkdownReportRenderer reportRenderer;
    private final ReviewStore reviewStore;
    private final RuntimeConfigService configService;
    private final ReviewNotifier notifier;
    private final ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM);

    public ReviewService(ReviewFileFilter fileFilter,
                         PromptBuilder promptBuilder,
                         LlmReviewer llmReviewer,
                         FindingVerifier findingVerifier,
                         ContextEnhancer contextEnhancer,
                         MarkdownReportRenderer reportRenderer,
                         ReviewStore reviewStore,
                         RuntimeConfigService configService,
                         ReviewNotifier notifier) {
        this.fileFilter = fileFilter;
        this.promptBuilder = promptBuilder;
        this.llmReviewer = llmReviewer;
        this.findingVerifier = findingVerifier;
        this.contextEnhancer = contextEnhancer;
        this.reportRenderer = reportRenderer;
        this.reviewStore = reviewStore;
        this.configService = configService;
        this.notifier = notifier;
    }

    public ReviewResult review(String diffText) {
        return review(diffText, ReviewOrigin.MANUAL);
    }

    public ReviewResult review(String diffText, ReviewOrigin origin) {
        return review(diffText, origin, ReviewProgressListener.NOOP);
    }

    public ReviewResult review(String diffText, ReviewOrigin origin, ReviewProgressListener listener) {
        List<FileDiff> files = parser.parse(diffText);
        if (files.isEmpty()) {
            throw new IllegalArgumentException("未能从输入中解析出文件变更, 请确认是合法的 unified diff 文本");
        }

        RuntimeConfig config = configService.current();
        checkDailyBudget(config);
        String modelName = config.getLlm().isMock()
                ? config.getLlm().getModel() + " (mock)"
                : config.getLlm().getModel();
        String reviewId = "rev-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));

        List<SkippedFile> skipped = new ArrayList<>();
        List<FileDiff> toReview = new ArrayList<>();
        for (FileDiff fileDiff : files) {
            Optional<String> skipReason = fileFilter.skipReason(fileDiff, config.getReview().getMaxFileChangedLines());
            if (skipReason.isPresent()) {
                skipped.add(new SkippedFile(fileDiff.displayPath(), skipReason.get()));
            } else {
                toReview.add(fileDiff);
            }
        }

        String systemPrompt = promptBuilder.systemPrompt(config.getReview().getMaxFindingsPerFile());
        List<Finding> findings = new ArrayList<>();
        TokenUsage usage = TokenUsage.empty();
        int reviewed = 0;
        int verifierDropped = 0;
        long weightedScore = 0;
        long scoreWeight = 0;
        List<String> fileSummaries = new ArrayList<>();

        listener.onStart(toReview.size());
        java.util.concurrent.atomic.AtomicInteger doneCounter = new java.util.concurrent.atomic.AtomicInteger();
        List<Future<PerFileOutcome>> futures = new ArrayList<>();
        for (FileDiff fileDiff : toReview) {
            futures.add(executor.submit(reviewOneFile(fileDiff, systemPrompt, config, origin,
                    listener, doneCounter, toReview.size())));
        }
        for (int i = 0; i < futures.size(); i++) {
            FileDiff fileDiff = toReview.get(i);
            try {
                PerFileOutcome outcome = futures.get(i).get();
                findings.addAll(outcome.findings());
                usage = usage.plus(outcome.usage());
                verifierDropped += outcome.verifierDropped();
                reviewed++;
                int weight = Math.max(1, fileDiff.changedLineCount());
                weightedScore += (long) outcome.score() * weight;
                scoreWeight += weight;
                if (outcome.summary() != null && !outcome.summary().isBlank()) {
                    fileSummaries.add(toReview.size() > 1
                            ? fileDiff.displayPath() + ": " + outcome.summary().trim()
                            : outcome.summary().trim());
                }
            } catch (Exception e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                log.warn("文件审查失败: {}", fileDiff.displayPath(), cause);
                skipped.add(new SkippedFile(fileDiff.displayPath(), "审查失败: " + cause.getMessage()));
            }
        }

        findings.sort(Comparator.comparing(Finding::severity)
                .thenComparing(Finding::file)
                .thenComparingInt(Finding::line));
        Map<String, Integer> severityCounts = countBySeverity(findings);
        // 总分 = 各文件评分按变更行数加权平均
        Integer score = scoreWeight == 0 ? null
                : (int) Math.max(0, Math.min(100, Math.round((double) weightedScore / scoreWeight)));
        String summary = fileSummaries.isEmpty() ? null : String.join("\n", fileSummaries);
        Instant createdAt = Instant.now();
        String reportPath = writeReport(reviewId, createdAt, modelName, findings, skipped, severityCounts,
                usage, score, summary);
        ReviewResult result = new ReviewResult(reviewId, createdAt, modelName, files.size(), reviewed,
                skipped, severityCounts, findings, usage, score, summary, verifierDropped, reportPath);
        reviewStore.save(result, origin);
        notifier.notifyResult(result, origin);
        return result;
    }

    private Callable<PerFileOutcome> reviewOneFile(FileDiff fileDiff, String systemPrompt,
                                                  RuntimeConfig config, ReviewOrigin origin,
                                                  ReviewProgressListener listener,
                                                  java.util.concurrent.atomic.AtomicInteger doneCounter,
                                                  int totalFiles) {
        return () -> {
            log.info("审查文件: {} (+{}/-{})", fileDiff.displayPath(),
                    fileDiff.addedLineCount(), fileDiff.removedLineCount());
            try {
                String extraContext = contextEnhancer.enhance(origin, fileDiff);
                String userPrompt = promptBuilder.userPrompt(fileDiff, extraContext);
                LlmReviewOutcome outcome = llmReviewer.review(fileDiff, systemPrompt, userPrompt);
                List<Finding> fileFindings = toFindings(fileDiff, outcome.findings(),
                        config.getReview().getMaxFindingsPerFile());
                TokenUsage fileUsage = outcome.tokenUsage();
                int dropped = 0;
                if (config.getReview().isVerifierEnabled() && !config.getLlm().isMock() && !fileFindings.isEmpty()) {
                    FindingVerifier.VerifyOutcome verified = findingVerifier.verify(userPrompt, fileFindings);
                    fileFindings = verified.kept();
                    dropped = verified.dropped();
                    fileUsage = fileUsage.plus(verified.usage());
                }
                int score = outcome.score() != null ? clampScore(outcome.score()) : deriveScore(fileFindings);
                return new PerFileOutcome(fileFindings, fileUsage, dropped, score, outcome.summary());
            } finally {
                listener.onFileDone(fileDiff.displayPath(), doneCounter.incrementAndGet(), totalFiles);
            }
        };
    }

    /** 每日 Token 预算闸门: 超预算直接拒绝新审查(mock 不计) */
    private void checkDailyBudget(RuntimeConfig config) {
        int budget = config.getReview().getDailyTokenBudget();
        if (budget <= 0 || config.getLlm().isMock()) {
            return;
        }
        long usedToday = reviewStore.tokensUsedToday();
        if (usedToday >= budget) {
            throw new IllegalArgumentException(
                    "今日 Token 预算已用完(" + usedToday + "/" + budget + "), 可在「系统配置」调整预算或明天再试");
        }
    }

    private record PerFileOutcome(List<Finding> findings, TokenUsage usage, int verifierDropped,
                                  int score, String summary) {
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    /** 模型没给分时按发现严重程度兜底推导 */
    private int deriveScore(List<Finding> findings) {
        int score = 100;
        for (Finding finding : findings) {
            score -= switch (finding.severity()) {
                case BLOCKER -> 30;
                case MAJOR -> 12;
                case MINOR -> 4;
                case INFO -> 1;
            };
        }
        return Math.max(10, score);
    }

    /** 将 LLM 原始结果转为 Finding, 并校验行号确实落在本文件的新增行上 */
    private List<Finding> toFindings(FileDiff fileDiff, List<RawFinding> rawFindings, int max) {
        Set<Integer> addedLines = fileDiff.addedLineNumbers();
        List<Finding> result = new ArrayList<>();
        for (RawFinding raw : rawFindings) {
            if (result.size() >= max) {
                break;
            }
            if (raw == null || raw.title() == null || raw.title().isBlank()) {
                continue;
            }
            int line = raw.line() == null ? -1 : raw.line();
            result.add(new Finding(
                    fileDiff.displayPath(),
                    line,
                    addedLines.contains(line),
                    Severity.from(raw.severity()),
                    raw.category() == null ? "OTHER" : raw.category().trim().toUpperCase(),
                    raw.title().trim(),
                    nullToEmpty(raw.detail()),
                    nullToEmpty(raw.suggestion()),
                    raw.confidence() == null ? 0.5 : Math.max(0, Math.min(1, raw.confidence()))));
        }
        return result;
    }

    private Map<String, Integer> countBySeverity(List<Finding> findings) {
        Map<Severity, Integer> counts = new EnumMap<>(Severity.class);
        findings.forEach(f -> counts.merge(f.severity(), 1, Integer::sum));
        Map<String, Integer> result = new LinkedHashMap<>();
        counts.forEach((severity, count) -> result.put(severity.name(), count));
        return result;
    }

    private String writeReport(String reviewId, Instant createdAt, String modelName, List<Finding> findings,
                               List<SkippedFile> skipped, Map<String, Integer> severityCounts, TokenUsage usage,
                               Integer score, String summary) {
        try {
            Path dir = reviewStore.reportDirectory();
            Files.createDirectories(dir);
            Path file = dir.resolve(reviewId + ".md");
            String content = reportRenderer.render(reviewId, createdAt, modelName,
                    findings, skipped, severityCounts, usage, score, summary);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file.toAbsolutePath().toString();
        } catch (IOException e) {
            log.warn("写入审查报告失败", e);
            return null;
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
