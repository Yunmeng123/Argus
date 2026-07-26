package com.argus.review;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

import com.argus.git.LocalGitDiffService;
import com.argus.model.ReviewResult;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 手动审查的异步任务管理: 提交立即返回 jobId, 前端轮询进度,
 * 解决大 MR 同步等待超时与刷新丢进度的问题(与 GitLab webhook 路径的异步模型对齐)。
 * 任务表在内存中(容量上限+过期清理), 重启丢任务可接受——结果本身已入库。
 */
@Service
public class ReviewJobManager {

    private static final Logger log = LoggerFactory.getLogger(ReviewJobManager.class);
    private static final int MAX_JOBS = 200;

    private final ReviewService reviewService;
    private final LocalGitDiffService gitDiffService;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public ReviewJobManager(ReviewService reviewService, LocalGitDiffService gitDiffService) {
        this.reviewService = reviewService;
        this.gitDiffService = gitDiffService;
    }

    /** diffText 非空走 diff 模式; 否则按本地仓库模式 */
    public JobView submit(String diffText, String repoPath, String baseRef, String headRef) {
        String jobId = "job-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
                + "-" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
        Job job = new Job(jobId);
        cleanup();
        jobs.put(jobId, job);
        executor.submit(() -> run(job, diffText, repoPath, baseRef, headRef));
        return job.view();
    }

    public JobView get(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            throw new NoSuchElementException("任务不存在或已过期: " + jobId);
        }
        return job.view();
    }

    private void run(Job job, String diffText, String repoPath, String baseRef, String headRef) {
        job.status = "RUNNING";
        try {
            String diff;
            ReviewOrigin origin;
            if (diffText != null && !diffText.isBlank()) {
                diff = diffText;
                origin = ReviewOrigin.MANUAL;
            } else {
                diff = gitDiffService.diff(repoPath, baseRef, headRef);
                if (diff.isBlank()) {
                    throw new IllegalArgumentException("指定范围内没有差异");
                }
                origin = ReviewOrigin.local(repoPath, headRef, gitDiffService.commitAuthor(repoPath, headRef));
            }
            ReviewResult result = reviewService.review(diff, origin, new ReviewProgressListener() {
                @Override
                public void onStart(int totalFiles) {
                    job.totalFiles = totalFiles;
                }

                @Override
                public void onFileDone(String path, int done, int total) {
                    job.doneFiles = done;
                    job.currentFile = path;
                }
            });
            job.reviewId = result.reviewId();
            job.score = result.score();
            job.findingCount = result.findings().size();
            job.status = "DONE";
        } catch (Exception e) {
            log.warn("异步审查任务失败: {}", job.jobId, e);
            job.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            job.status = "FAILED";
        }
    }

    /** 超量时清掉最早完成的任务 */
    private void cleanup() {
        if (jobs.size() < MAX_JOBS) {
            return;
        }
        jobs.values().stream()
                .filter(j -> "DONE".equals(j.status) || "FAILED".equals(j.status))
                .sorted((a, b) -> a.submittedAt.compareTo(b.submittedAt))
                .limit(50)
                .forEach(j -> jobs.remove(j.jobId));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public record JobView(String jobId, String status, int totalFiles, int doneFiles, String currentFile,
                          String reviewId, Integer score, Integer findingCount, String error) {
    }

    private static class Job {
        final String jobId;
        final Instant submittedAt = Instant.now();
        volatile String status = "QUEUED";
        volatile int totalFiles;
        volatile int doneFiles;
        volatile String currentFile;
        volatile String reviewId;
        volatile Integer score;
        volatile Integer findingCount;
        volatile String error;

        Job(String jobId) {
            this.jobId = jobId;
        }

        JobView view() {
            return new JobView(jobId, status, totalFiles, doneFiles, currentFile,
                    reviewId, score, findingCount, error);
        }
    }
}
