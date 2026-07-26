package com.argus.eval;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.argus.model.Finding;
import com.argus.model.ReviewResult;
import com.argus.review.ReviewOrigin;
import com.argus.review.ReviewService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 评测集运行器: samples/eval/ 下是埋了已知问题的 diff 用例(cases.json 声明期望命中),
 * 跑一遍当前配置的模型, 量化召回率(该抓的抓到多少)与精确率(报出来的有多少是真的)。
 * prompt/模型/上下文策略的每次调整都应跑一遍防退化。
 */
@Service
public class EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalService.class);
    private static final Path EVAL_DIR = Path.of("samples", "eval");
    /** 行号匹配容差: LLM 报告行号与埋点相差 2 行以内视为命中 */
    private static final int LINE_TOLERANCE = 2;

    private final ReviewService reviewService;
    private final ObjectMapper objectMapper;

    public EvalService(ReviewService reviewService, ObjectMapper objectMapper) {
        this.reviewService = reviewService;
        this.objectMapper = objectMapper;
    }

    public EvalReport run() {
        List<EvalCase> cases = loadCases();
        List<CaseResult> results = new ArrayList<>();
        int totalExpected = 0;
        int totalHits = 0;
        int totalFindings = 0;
        int matchedFindings = 0;
        int totalTokens = 0;

        for (EvalCase evalCase : cases) {
            try {
                String diff = Files.readString(EVAL_DIR.resolve(evalCase.diffFile()), StandardCharsets.UTF_8);
                ReviewResult result = reviewService.review(diff, ReviewOrigin.EVAL);
                totalTokens += result.tokenUsage().totalTokens();

                List<String> missed = new ArrayList<>();
                int hits = 0;
                for (Expected expected : evalCase.expected()) {
                    if (result.findings().stream().anyMatch(f -> matches(f, expected))) {
                        hits++;
                    } else {
                        missed.add(expected.file() + ":" + expected.line());
                    }
                }
                List<String> unmatched = result.findings().stream()
                        .filter(f -> evalCase.expected().stream().noneMatch(e -> matches(f, e)))
                        .map(f -> f.file() + ":" + f.line() + " " + f.title())
                        .toList();

                totalExpected += evalCase.expected().size();
                totalHits += hits;
                totalFindings += result.findings().size();
                matchedFindings += result.findings().size() - unmatched.size();
                results.add(new CaseResult(evalCase.name(), evalCase.expected().size(), hits,
                        result.findings().size(), missed, unmatched, result.reviewId()));
            } catch (Exception e) {
                log.warn("评测用例执行失败: {}", evalCase.name(), e);
                results.add(new CaseResult(evalCase.name(), evalCase.expected().size(), 0, 0,
                        List.of("用例执行失败: " + e.getMessage()), List.of(), null));
                totalExpected += evalCase.expected().size();
            }
        }

        double recall = totalExpected == 0 ? 0 : (double) totalHits / totalExpected;
        double precision = totalFindings == 0 ? 0 : (double) matchedFindings / totalFindings;
        return new EvalReport(results, totalExpected, totalHits, totalFindings, matchedFindings,
                round(recall), round(precision), totalTokens);
    }

    private boolean matches(Finding finding, Expected expected) {
        if (!finding.file().equals(expected.file())) {
            return false;
        }
        if (expected.line() != null && Math.abs(finding.line() - expected.line()) > LINE_TOLERANCE) {
            return false;
        }
        return expected.category() == null || expected.category().equalsIgnoreCase(finding.category());
    }

    private List<EvalCase> loadCases() {
        Path manifest = EVAL_DIR.resolve("cases.json");
        try {
            return objectMapper.readValue(Files.readString(manifest, StandardCharsets.UTF_8),
                    new TypeReference<List<EvalCase>>() {
                    });
        } catch (Exception e) {
            throw new IllegalStateException("读取评测用例失败(" + manifest.toAbsolutePath() + "): " + e.getMessage(), e);
        }
    }

    private double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvalCase(String name, String diffFile, List<Expected> expected) {
        public EvalCase {
            expected = expected == null ? List.of() : expected;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Expected(String file, Integer line, String category) {
    }

    public record CaseResult(String name, int expectedCount, int hitCount, int findingCount,
                             List<String> missed, List<String> unmatchedFindings, String reviewId) {
    }

    public record EvalReport(List<CaseResult> cases, int totalExpected, int totalHits,
                             int totalFindings, int matchedFindings,
                             double recall, double precision, int totalTokens) {
    }
}
