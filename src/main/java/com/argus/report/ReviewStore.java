package com.argus.report;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.argus.config.ArgusProperties;
import com.argus.llm.TokenUsage;
import com.argus.model.Finding;
import com.argus.model.ReviewResult;
import com.argus.model.ReviewSummary;
import com.argus.model.Severity;
import com.argus.model.SkippedFile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审查记录持久化(H2 数据库, review_record + review_finding 两张表)。
 * severityCounts/skippedFiles 以冗余 JSON 列存主表, 列表页免联查;
 * Markdown 报告仍以文件形式留在报告目录, 不入库。
 */
@Component
public class ReviewStore {

    private static final Logger log = LoggerFactory.getLogger(ReviewStore.class);

    private final ReviewRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final ArgusProperties properties;

    public ReviewStore(ReviewRecordRepository repository, ObjectMapper objectMapper, ArgusProperties properties) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public void save(ReviewResult result) {
        save(result, com.argus.review.ReviewOrigin.MANUAL);
    }

    @Transactional
    public void save(ReviewResult result, com.argus.review.ReviewOrigin origin) {
        try {
            ReviewRecordEntity entity = toEntity(result);
            entity.setSource(origin == null ? "MANUAL" : origin.source());
            if (origin != null) {
                entity.setRepoKey(origin.repoKey());
                entity.setMrIid(origin.prNumber());
                entity.setCommitSha(origin.commitSha());
                entity.setAuthor(origin.author());
            }
            repository.save(entity);
        } catch (Exception e) {
            // 存档失败不影响本次审查结果返回
            log.warn("保存审查记录失败: {}", result.reviewId(), e);
        }
    }

    @Transactional(readOnly = true)
    public boolean existsVcsReview(String source, String repoKey, long prNumber, String commitSha) {
        return repository.existsBySourceAndRepoKeyAndMrIidAndCommitSha(source, repoKey, prNumber, commitSha);
    }

    @Transactional(readOnly = true)
    public long tokensUsedToday() {
        java.time.Instant startOfDay = java.time.LocalDate.now()
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
        return repository.sumTokensSince(startOfDay);
    }

    @Transactional(readOnly = true)
    public List<ReviewSummary> list() {
        List<ReviewSummary> summaries = new ArrayList<>();
        for (ReviewRecordEntity entity : repository.findTop200ByOrderByCreatedAtDesc()) {
            summaries.add(new ReviewSummary(entity.getReviewId(), entity.getCreatedAt(), entity.getModel(),
                    entity.getSource() == null ? "MANUAL" : entity.getSource(), entity.getAuthor(),
                    entity.getTotalFiles(), entity.getReviewedFiles(), entity.getFindingCount(),
                    entity.getTotalTokens(), entity.getScore(),
                    readJson(entity.getSeverityCountsJson(), new TypeReference<LinkedHashMap<String, Integer>>() {
                    }, new LinkedHashMap<>())));
        }
        return summaries;
    }

    @Transactional(readOnly = true)
    public ReviewResult get(String reviewId) {
        ReviewRecordEntity entity = repository.findById(reviewId == null ? "" : reviewId)
                .orElseThrow(() -> new NoSuchElementException("审查记录不存在: " + reviewId));
        return toResult(entity);
    }

    /** Markdown 报告的存放目录 */
    public Path reportDirectory() {
        return Path.of(properties.getReport().getDir());
    }

    private ReviewRecordEntity toEntity(ReviewResult result) {
        ReviewRecordEntity entity = new ReviewRecordEntity();
        entity.setReviewId(result.reviewId());
        entity.setCreatedAt(result.createdAt());
        entity.setModel(result.model());
        entity.setTotalFiles(result.totalFiles());
        entity.setReviewedFiles(result.reviewedFiles());
        entity.setFindingCount(result.findings() == null ? 0 : result.findings().size());
        entity.setSeverityCountsJson(writeJson(result.severityCounts()));
        entity.setSkippedFilesJson(writeJson(result.skippedFiles()));
        TokenUsage usage = result.tokenUsage() == null ? TokenUsage.empty() : result.tokenUsage();
        entity.setPromptTokens(usage.promptTokens());
        entity.setCompletionTokens(usage.completionTokens());
        entity.setTotalTokens(usage.totalTokens());
        entity.setReportPath(result.reportPath());
        entity.setVerifierDropped(result.verifierDropped());
        entity.setScore(result.score());
        entity.setSummaryText(result.summary());
        if (result.findings() != null) {
            for (Finding finding : result.findings()) {
                ReviewFindingEntity findingEntity = new ReviewFindingEntity();
                findingEntity.setFile(finding.file());
                findingEntity.setLine(finding.line());
                findingEntity.setLineVerified(finding.lineVerified());
                findingEntity.setSeverity(finding.severity() == null ? Severity.INFO.name() : finding.severity().name());
                findingEntity.setCategory(finding.category());
                findingEntity.setTitle(finding.title());
                findingEntity.setDetail(finding.detail());
                findingEntity.setSuggestion(finding.suggestion());
                findingEntity.setConfidence(finding.confidence());
                entity.getFindings().add(findingEntity);
            }
        }
        return entity;
    }

    private ReviewResult toResult(ReviewRecordEntity entity) {
        List<Finding> findings = new ArrayList<>();
        for (ReviewFindingEntity findingEntity : entity.getFindings()) {
            findings.add(new Finding(findingEntity.getFile(), findingEntity.getLine(),
                    findingEntity.isLineVerified(), Severity.from(findingEntity.getSeverity()),
                    findingEntity.getCategory(), findingEntity.getTitle(), findingEntity.getDetail(),
                    findingEntity.getSuggestion(), findingEntity.getConfidence()));
        }
        Map<String, Integer> severityCounts = readJson(entity.getSeverityCountsJson(),
                new TypeReference<LinkedHashMap<String, Integer>>() {
                }, new LinkedHashMap<>());
        List<SkippedFile> skipped = readJson(entity.getSkippedFilesJson(),
                new TypeReference<List<SkippedFile>>() {
                }, List.of());
        return new ReviewResult(entity.getReviewId(), entity.getCreatedAt(), entity.getModel(),
                entity.getTotalFiles(), entity.getReviewedFiles(), skipped, severityCounts, findings,
                new TokenUsage(entity.getPromptTokens(), entity.getCompletionTokens(), entity.getTotalTokens()),
                entity.getScore(), entity.getSummaryText(),
                entity.getVerifierDropped(), entity.getReportPath());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            log.warn("序列化冗余 JSON 列失败", e);
            return "[]";
        }
    }

    private <T> T readJson(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("解析冗余 JSON 列失败", e);
            return fallback;
        }
    }
}
