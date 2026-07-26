package com.argus.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * 审查记录主表。severityCounts/skippedFiles 为冗余 JSON 列:
 * 列表页只查主表即可渲染, 不必联查 finding 明细。
 */
@Entity
@Table(name = "review_record")
public class ReviewRecordEntity {

    @Id
    @Column(name = "review_id", length = 64)
    private String reviewId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(length = 128)
    private String model;

    @Column(name = "total_files")
    private int totalFiles;

    @Column(name = "reviewed_files")
    private int reviewedFiles;

    @Column(name = "finding_count")
    private int findingCount;

    @Column(name = "severity_counts_json", length = 512)
    private String severityCountsJson;

    @Lob
    @Column(name = "skipped_files_json")
    private String skippedFilesJson;

    @Column(name = "prompt_tokens")
    private int promptTokens;

    @Column(name = "completion_tokens")
    private int completionTokens;

    @Column(name = "total_tokens")
    private int totalTokens;

    @Column(name = "report_path", length = 512)
    private String reportPath;

    /** MANUAL / GITLAB / GITHUB / GITEE / EVAL */
    @Column(length = 16)
    private String source;

    /** 提交人(开发者画像数据源): PR 作者 / 本地仓库提交人; 手动粘贴无作者 */
    @Column(length = 128)
    private String author;

    /** 平台仓库标识: GitLab=项目数字ID, GitHub/Gitee=owner/repo */
    @Column(name = "repo_key", length = 256)
    private String repoKey;

    /** 旧列, 已由 repoKey 取代, 保留兼容历史数据 */
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "mr_iid")
    private Long mrIid;

    @Column(name = "commit_sha", length = 64)
    private String commitSha;

    /** Integer 而非 int: ddl-auto=update 加列后历史行为 NULL, primitive 反序列化会炸 */
    @Column(name = "verifier_dropped")
    private Integer verifierDropped;

    /** AI 质量评分 0~100, 历史行可能为 NULL */
    @Column(name = "score")
    private Integer score;

    /** AI 总评 */
    @Lob
    @Column(name = "summary_text")
    private String summaryText;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    @OrderColumn(name = "seq")
    private List<ReviewFindingEntity> findings = new ArrayList<>();

    public String getReviewId() {
        return reviewId;
    }

    public void setReviewId(String reviewId) {
        this.reviewId = reviewId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    public int getReviewedFiles() {
        return reviewedFiles;
    }

    public void setReviewedFiles(int reviewedFiles) {
        this.reviewedFiles = reviewedFiles;
    }

    public int getFindingCount() {
        return findingCount;
    }

    public void setFindingCount(int findingCount) {
        this.findingCount = findingCount;
    }

    public String getSeverityCountsJson() {
        return severityCountsJson;
    }

    public void setSeverityCountsJson(String severityCountsJson) {
        this.severityCountsJson = severityCountsJson;
    }

    public String getSkippedFilesJson() {
        return skippedFilesJson;
    }

    public void setSkippedFilesJson(String skippedFilesJson) {
        this.skippedFilesJson = skippedFilesJson;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public String getReportPath() {
        return reportPath;
    }

    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
    }

    public List<ReviewFindingEntity> getFindings() {
        return findings;
    }

    public void setFindings(List<ReviewFindingEntity> findings) {
        this.findings = findings;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getRepoKey() {
        return repoKey;
    }

    public void setRepoKey(String repoKey) {
        this.repoKey = repoKey;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getMrIid() {
        return mrIid;
    }

    public void setMrIid(Long mrIid) {
        this.mrIid = mrIid;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    public int getVerifierDropped() {
        return verifierDropped == null ? 0 : verifierDropped;
    }

    public void setVerifierDropped(Integer verifierDropped) {
        this.verifierDropped = verifierDropped;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }
}
