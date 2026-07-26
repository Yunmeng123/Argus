package com.argus.vcs;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 代码平台适配接口。新增平台只需实现本接口并注册为 Spring Bean,
 * Webhook 路由/异步队列/审查编排/幂等全部复用。
 */
public interface VcsProvider {

    /** 平台标识(小写), 同时是 webhook 路径段: /api/webhook/{platform} */
    String platform();

    /**
     * 验签 + 事件过滤 + 任务提取。
     * 返回 empty = 事件被忽略(非 PR 事件/非代码变更等); 验签失败抛 {@link WebhookAuthException}。
     * rawBody 单独传入是因为 GitHub 的 HMAC 签名必须基于原始报文计算。
     */
    Optional<PrTask> parseWebhook(HttpServletRequest request, String rawBody);

    PrChanges fetchChanges(PrTask task);

    void postSummary(PrTask task, String markdownBody);

    /** 行级评论; 失败返回 false 由调用方降级进总结评论 */
    boolean postLineComment(PrTask task, PrChanges refs, String newPath, int newLine, String markdownBody);
}
