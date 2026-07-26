package com.argus.vcs;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一 Webhook 入口: /api/webhook/{platform}, 按平台路由到对应 Provider。
 * 秒回 202(平台侧超时会重推), 真正的审查在异步队列执行。
 */
@RestController
public class VcsWebhookController {

    private static final Logger log = LoggerFactory.getLogger(VcsWebhookController.class);

    private final Map<String, VcsProvider> providers;
    private final ReviewTaskQueue taskQueue;

    public VcsWebhookController(List<VcsProvider> providerList, ReviewTaskQueue taskQueue) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(VcsProvider::platform, Function.identity()));
        this.taskQueue = taskQueue;
    }

    @PostMapping("/api/webhook/{platform}")
    public ResponseEntity<Map<String, String>> handle(@PathVariable String platform,
                                                      HttpServletRequest request,
                                                      @RequestBody String rawBody) {
        VcsProvider provider = providers.get(platform.toLowerCase());
        if (provider == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "不支持的平台: " + platform + ", 可用: " + providers.keySet()));
        }
        Optional<PrTask> task;
        try {
            task = provider.parseWebhook(request, rawBody);
        } catch (WebhookAuthException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
        if (task.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }
        log.info("收到 {} PR 事件: {} sha={}", platform, task.get().key(), task.get().commitSha());
        taskQueue.submit(task.get());
        return ResponseEntity.accepted().body(Map.of("status", "queued"));
    }
}
