package com.argus.web;

import java.util.List;

import com.argus.model.ReviewResult;
import com.argus.model.ReviewSummary;
import com.argus.report.ReviewStore;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class ReviewQueryController {

    private final ReviewStore reviewStore;

    public ReviewQueryController(ReviewStore reviewStore) {
        this.reviewStore = reviewStore;
    }

    /** 审查记录列表(按时间倒序) */
    @GetMapping
    public List<ReviewSummary> list() {
        return reviewStore.list();
    }

    /** 单条审查记录全量结果 */
    @GetMapping("/{reviewId}")
    public ReviewResult get(@PathVariable String reviewId) {
        return reviewStore.get(reviewId);
    }
}
