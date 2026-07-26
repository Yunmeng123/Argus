package com.argus.report;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRecordRepository extends JpaRepository<ReviewRecordEntity, String> {

    List<ReviewRecordEntity> findTop200ByOrderByCreatedAtDesc();

    /** PR 幂等(平台无关): 同一 PR 的同一 commit 只审一次 */
    boolean existsBySourceAndRepoKeyAndMrIidAndCommitSha(String source, String repoKey, Long mrIid, String commitSha);

    /** 开发者画像: 按作者聚合(排除评测记录) */
    @Query("""
            select r.author as author, count(r) as reviews, avg(r.score) as avgScore,
                   sum(r.findingCount) as findings, sum(r.totalTokens) as tokens, max(r.createdAt) as lastActive
            from ReviewRecordEntity r
            where r.author is not null and r.source <> 'EVAL'
            group by r.author
            order by count(r) desc
            """)
    List<AuthorAggregate> aggregateAuthors();

    List<ReviewRecordEntity> findTop10ByAuthorAndSourceNotOrderByCreatedAtDesc(String author, String source);

    @Query("select f.severity, count(f) from ReviewRecordEntity r join r.findings f "
            + "where r.author = :author and r.source <> 'EVAL' group by f.severity")
    List<Object[]> severityDistributionByAuthor(@Param("author") String author);

    @Query("select f.category, count(f) from ReviewRecordEntity r join r.findings f "
            + "where r.author = :author and r.source <> 'EVAL' group by f.category order by count(f) desc")
    List<Object[]> categoryDistributionByAuthor(@Param("author") String author);

    interface AuthorAggregate {
        String getAuthor();

        long getReviews();

        Double getAvgScore();

        Long getFindings();

        Long getTokens();

        Instant getLastActive();
    }

    /** 日预算控制用: 统计某时刻以来的 token 消耗 */
    @Query("select coalesce(sum(r.totalTokens), 0) from ReviewRecordEntity r where r.createdAt >= :since")
    long sumTokensSince(@Param("since") Instant since);
}
