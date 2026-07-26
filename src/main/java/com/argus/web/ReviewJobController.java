package com.argus.web;

import com.argus.review.ReviewJobManager;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 异步审查任务接口(前端用): 提交秒回 jobId, 轮询查进度。
 * 同步接口 /api/review/* 仍保留, 供 MCP 工具与脚本使用。
 */
@RestController
@RequestMapping("/api/review/jobs")
public class ReviewJobController {

    private final ReviewJobManager jobManager;

    public ReviewJobController(ReviewJobManager jobManager) {
        this.jobManager = jobManager;
    }

    public record SubmitJobRequest(String diff, String repoPath, String baseRef, String headRef) {
    }

    @PostMapping
    public ResponseEntity<ReviewJobManager.JobView> submit(@RequestBody SubmitJobRequest request) {
        boolean hasDiff = request.diff() != null && !request.diff().isBlank();
        boolean hasRepo = request.repoPath() != null && !request.repoPath().isBlank();
        if (!hasDiff && !hasRepo) {
            throw new IllegalArgumentException("diff 与 repoPath 至少提供一个");
        }
        return ResponseEntity.accepted()
                .body(jobManager.submit(request.diff(), request.repoPath(), request.baseRef(), request.headRef()));
    }

    @GetMapping("/{jobId}")
    public ReviewJobManager.JobView get(@PathVariable String jobId) {
        return jobManager.get(jobId);
    }
}
