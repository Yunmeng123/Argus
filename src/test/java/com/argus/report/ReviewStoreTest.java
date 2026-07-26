package com.argus.report;

import java.time.Instant;
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
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ReviewStore 数据库存取回读测试(内存 H2) */
@DataJpaTest
@Import(ReviewStoreTest.StoreTestConfig.class)
class ReviewStoreTest {

    @TestConfiguration
    static class StoreTestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        ArgusProperties argusProperties() {
            return new ArgusProperties();
        }

        @Bean
        ReviewStore reviewStore(ReviewRecordRepository repository, ObjectMapper objectMapper,
                                ArgusProperties properties) {
            return new ReviewStore(repository, objectMapper, properties);
        }
    }

    @Autowired
    private ReviewStore store;

    @Test
    void saveThenGetRoundTrip() {
        ReviewResult result = sampleResult("rev-20260101-000000-a1b2");
        store.save(result);

        ReviewResult loaded = store.get("rev-20260101-000000-a1b2");
        assertEquals(result.reviewId(), loaded.reviewId());
        assertEquals(result.model(), loaded.model());
        assertEquals(2, loaded.findings().size());
        assertEquals(Severity.BLOCKER, loaded.findings().get(0).severity());
        assertEquals("src/Foo.java", loaded.findings().get(0).file());
        assertEquals(20, loaded.findings().get(0).line());
        assertTrue(loaded.findings().get(0).lineVerified());
        assertEquals(1, loaded.skippedFiles().size());
        assertEquals("package-lock.json", loaded.skippedFiles().get(0).path());
        assertEquals(Map.of("BLOCKER", 1, "MAJOR", 1), loaded.severityCounts());
        assertEquals(1500, loaded.tokenUsage().totalTokens());
        assertEquals(58, loaded.score());
        assertEquals("存在SQL注入与空指针风险, 建议修复后合入", loaded.summary());
    }

    @Test
    void listReturnsSummariesNewestFirst() {
        store.save(sampleResult("rev-20260101-000000-aaaa"));
        store.save(sampleResult("rev-20260102-000000-bbbb", Instant.parse("2026-01-02T00:00:00Z")));

        List<ReviewSummary> summaries = store.list();
        assertEquals(2, summaries.size());
        assertEquals("rev-20260102-000000-bbbb", summaries.get(0).reviewId());
        assertEquals(2, summaries.get(0).findingCount());
        assertEquals("MANUAL", summaries.get(0).source());
        assertEquals(1500, summaries.get(0).totalTokens());
        assertEquals(Map.of("BLOCKER", 1, "MAJOR", 1), summaries.get(0).severityCounts());
    }

    @Test
    void getMissingIdThrowsNoSuchElement() {
        assertThrows(NoSuchElementException.class, () -> store.get("rev-not-exist"));
    }

    @Test
    void vcsOriginPersistsAuthorAndIdempotencyKey() {
        store.save(sampleResult("rev-20260103-000000-cccc", Instant.parse("2026-01-03T00:00:00Z")),
                com.argus.review.ReviewOrigin.vcs("github", "alice/demo", 15, "fedcba9",
                        "https://github.com/alice/demo/pull/15", "fix: npe", "alice"));

        assertTrue(store.existsVcsReview("GITHUB", "alice/demo", 15, "fedcba9"));
        assertTrue(!store.existsVcsReview("GITHUB", "alice/demo", 15, "other-sha"));
        ReviewSummary summary = store.list().get(0);
        assertEquals("GITHUB", summary.source());
        assertEquals("alice", summary.author());
    }

    private ReviewResult sampleResult(String id) {
        return sampleResult(id, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private ReviewResult sampleResult(String id, Instant createdAt) {
        return new ReviewResult(id, createdAt, "test-model", 3, 2,
                List.of(new SkippedFile("package-lock.json", "锁文件")),
                Map.of("BLOCKER", 1, "MAJOR", 1),
                List.of(
                        new Finding("src/Foo.java", 20, true, Severity.BLOCKER, "SECURITY",
                                "SQL 注入", "拼接 SQL", "用 PreparedStatement", 0.95),
                        new Finding("src/Bar.java", 8, false, Severity.MAJOR, "BUG",
                                "空指针", "未判空", "加判空", 0.8)),
                new TokenUsage(1200, 300, 1500), 58, "存在SQL注入与空指针风险, 建议修复后合入",
                1, "reports/" + id + ".md");
    }
}
