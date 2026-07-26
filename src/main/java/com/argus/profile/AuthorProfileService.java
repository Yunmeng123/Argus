package com.argus.profile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import com.argus.llm.LlmChat;
import com.argus.report.ReviewFindingEntity;
import com.argus.report.ReviewRecordEntity;
import com.argus.report.ReviewRecordRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 开发者画像: 基于审查记录聚合出个人质量档案。
 * 产品定位是"个人成长助手"而非绩效考核——标签措辞中性, AI 总评强调建设性建议。
 */
@Service
public class AuthorProfileService {

    private static final Logger log = LoggerFactory.getLogger(AuthorProfileService.class);
    private static final int RECENT_LIMIT = 10;
    private static final int RECENT_FINDINGS_LIMIT = 20;

    private final ReviewRecordRepository recordRepository;
    private final AuthorProfileRepository profileRepository;
    private final LlmChat llmChat;

    public AuthorProfileService(ReviewRecordRepository recordRepository,
                                AuthorProfileRepository profileRepository,
                                LlmChat llmChat) {
        this.recordRepository = recordRepository;
        this.profileRepository = profileRepository;
        this.llmChat = llmChat;
    }

    public record AuthorCard(String author, long reviews, Double avgScore, long findings,
                             long tokens, Instant lastActive) {
    }

    public record TrendPoint(String reviewId, Instant createdAt, Integer score, int findingCount) {
    }

    public record RecentFinding(String reviewId, String file, int line, String severity,
                                String category, String title) {
    }

    public record AiSummaryView(String content, Instant generatedAt, int reviewCountAtGeneration, boolean stale) {
    }

    public record AuthorProfile(AuthorCard card, List<TrendPoint> scoreTrend,
                                Map<String, Long> severityDistribution,
                                Map<String, Long> categoryDistribution,
                                List<String> tags,
                                List<RecentFinding> recentFindings,
                                AiSummaryView aiSummary) {
    }

    @Transactional(readOnly = true)
    public List<AuthorCard> listAuthors() {
        return recordRepository.aggregateAuthors().stream()
                .map(agg -> new AuthorCard(agg.getAuthor(), agg.getReviews(), round(agg.getAvgScore()),
                        nvl(agg.getFindings()), nvl(agg.getTokens()), agg.getLastActive()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AuthorProfile profile(String author) {
        AuthorCard card = listAuthors().stream()
                .filter(c -> c.author().equals(author))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("没有该开发者的审查数据: " + author));

        List<ReviewRecordEntity> recent =
                recordRepository.findTop10ByAuthorAndSourceNotOrderByCreatedAtDesc(author, "EVAL");
        List<TrendPoint> trend = new ArrayList<>();
        List<RecentFinding> recentFindings = new ArrayList<>();
        int blockerCount = 0;
        for (ReviewRecordEntity record : recent) {
            trend.add(new TrendPoint(record.getReviewId(), record.getCreatedAt(),
                    record.getScore(), record.getFindingCount()));
            for (ReviewFindingEntity finding : record.getFindings()) {
                if ("BLOCKER".equals(finding.getSeverity())) {
                    blockerCount++;
                }
                if (recentFindings.size() < RECENT_FINDINGS_LIMIT) {
                    recentFindings.add(new RecentFinding(record.getReviewId(), finding.getFile(),
                            finding.getLine(), finding.getSeverity(), finding.getCategory(), finding.getTitle()));
                }
            }
        }

        Map<String, Long> severityDist = toCountMap(recordRepository.severityDistributionByAuthor(author));
        Map<String, Long> categoryDist = toCountMap(recordRepository.categoryDistributionByAuthor(author));
        List<String> tags = buildTags(card, severityDist, categoryDist, blockerCount);
        AiSummaryView aiSummary = profileRepository.findById(author)
                .map(entity -> new AiSummaryView(entity.getAiSummary(), entity.getGeneratedAt(),
                        entity.getReviewCount() == null ? 0 : entity.getReviewCount(),
                        entity.getReviewCount() != null && entity.getReviewCount() < card.reviews()))
                .orElse(null);
        return new AuthorProfile(card, trend, severityDist, categoryDist, tags, recentFindings, aiSummary);
    }

    /** 生成/刷新 AI 成长画像并缓存 */
    @Transactional
    public AiSummaryView generateAiSummary(String author) {
        AuthorProfile profile = profile(author);
        String content = llmChat.complete(aiSystemPrompt(), aiUserPrompt(profile), null).text().trim();
        AuthorProfileEntity entity = profileRepository.findById(author).orElseGet(() -> {
            AuthorProfileEntity created = new AuthorProfileEntity();
            created.setAuthor(author);
            return created;
        });
        entity.setAiSummary(content);
        entity.setGeneratedAt(Instant.now());
        entity.setReviewCount((int) profile.card().reviews());
        profileRepository.save(entity);
        log.info("已生成开发者 AI 画像: {} (基于 {} 次审查)", author, profile.card().reviews());
        return new AiSummaryView(content, entity.getGeneratedAt(), entity.getReviewCount(), false);
    }

    /** 规则打标: 措辞保持中性/建设性 */
    private List<String> buildTags(AuthorCard card, Map<String, Long> severityDist,
                                   Map<String, Long> categoryDist, int recentBlockers) {
        List<String> tags = new ArrayList<>();
        if (card.reviews() >= 3 && card.avgScore() != null && card.avgScore() >= 85) {
            tags.add("高质量提交者");
        }
        if (card.reviews() >= 3 && card.findings() == 0) {
            tags.add("零缺陷记录");
        }
        if (recentBlockers >= 2 || nvl(severityDist.get("BLOCKER")) >= 3) {
            tags.add("严重问题需重点关注");
        }
        Map<String, String> categoryTags = Map.of(
                "SECURITY", "安全编码待加强",
                "BUG", "逻辑健壮性待提升",
                "PERFORMANCE", "性能意识可优化",
                "MAINTAINABILITY", "可维护性习惯培养中",
                "STYLE", "编码风格可打磨");
        categoryDist.forEach((category, count) -> {
            if (count >= 3 && categoryTags.containsKey(category)) {
                tags.add(categoryTags.get(category));
            }
        });
        return tags;
    }

    private String aiSystemPrompt() {
        return """
                你是一位研发效能教练。根据某开发者近期代码审查的统计数据与问题清单,
                写一段 150~250 字的开发者成长画像, 包含: ①代码质量的整体印象 ②最值得注意的 1~2 类问题模式
                ③两三条具体可执行的改进建议。
                语气必须建设性、对事不对人, 不贴负面标签, 不做人身评价。输出纯文本, 不要 markdown 标题。
                """;
    }

    private String aiUserPrompt(AuthorProfile profile) {
        StringBuilder sb = new StringBuilder();
        AuthorCard card = profile.card();
        sb.append("开发者: ").append(card.author()).append('\n');
        sb.append("累计审查 ").append(card.reviews()).append(" 次, 平均质量分 ")
                .append(card.avgScore() == null ? "-" : card.avgScore())
                .append(", 累计发现问题 ").append(card.findings()).append(" 个\n");
        sb.append("问题分类分布: ").append(profile.categoryDistribution()).append('\n');
        sb.append("严重程度分布: ").append(profile.severityDistribution()).append('\n');
        sb.append("近期问题清单:\n");
        for (RecentFinding finding : profile.recentFindings()) {
            sb.append("- [").append(finding.severity()).append('/').append(finding.category()).append("] ")
                    .append(finding.title()).append('\n');
        }
        return sb.toString();
    }

    private Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return result;
    }

    private Double round(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }

    private long nvl(Long value) {
        return value == null ? 0 : value;
    }
}
