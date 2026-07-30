package com.argus.vcs;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 进程内异步任务队列: webhook 必须秒回, 审查在后台执行。
 * 同一 PR 高频 push 时只保留最新 commit 的任务(任务合并), 过期任务直接丢弃。
 * 接口有意保持简单, 后续要平移到 RabbitMQ 时只需替换本类实现。
 */
@Component
public class ReviewTaskQueue {

    private static final Logger log = LoggerFactory.getLogger(ReviewTaskQueue.class);

    private final PrReviewService prReviewService;
    private final ExecutorService worker;
    private final AtomicBoolean closed = new AtomicBoolean();
    /** key=platform:repo:pr -> 该 PR 最新待处理任务 */
    private final ConcurrentHashMap<String, PrTask> latest = new ConcurrentHashMap<>();

    public ReviewTaskQueue(PrReviewService prReviewService) {
        this(prReviewService, Executors.newFixedThreadPool(2));
    }

    /** Package-visible injection point used by deterministic queue tests. */
    ReviewTaskQueue(PrReviewService prReviewService, ExecutorService worker) {
        this.prReviewService = prReviewService;
        this.worker = worker;
    }

    public void submit(PrTask task) {
        if (closed.get()) {
            throw new RejectedExecutionException("review task queue is closed");
        }
        latest.put(task.key(), task);
        worker.submit(() -> {
            PrTask current = latest.get(task.key());
            if (current == null || !current.commitSha().equals(task.commitSha())) {
                log.info("任务已被更新的 commit 取代, 跳过: {} sha={}", task.key(), task.commitSha());
                return;
            }
            latest.remove(task.key(), current);
            try {
                prReviewService.process(current);
            } catch (Exception e) {
                log.error("PR 审查任务失败: {} sha={}", task.key(), task.commitSha(), e);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        if (closed.compareAndSet(false, true)) {
            latest.clear();
            worker.shutdownNow();
        }
    }
}
