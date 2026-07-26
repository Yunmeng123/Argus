package com.argus.profile;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** AI 生成的开发者画像总评缓存: 生成一次入库, 避免每次查看都调模型 */
@Entity
@Table(name = "author_profile")
public class AuthorProfileEntity {

    @Id
    @Column(length = 128)
    private String author;

    @Lob
    @Column(name = "ai_summary")
    private String aiSummary;

    @Column(name = "generated_at")
    private Instant generatedAt;

    /** 生成时的审查次数, 用于前端提示"画像基于 N 次审查, 已有新数据可刷新" */
    @Column(name = "review_count")
    private Integer reviewCount;

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public void setAiSummary(String aiSummary) {
        this.aiSummary = aiSummary;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }
}
