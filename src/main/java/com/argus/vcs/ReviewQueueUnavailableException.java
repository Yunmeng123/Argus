package com.argus.vcs;

/** RabbitMQ 无法接受或确认 Webhook 审查任务；入口返回 503，让代码平台稍后重试。 */
public class ReviewQueueUnavailableException extends RuntimeException {

    public ReviewQueueUnavailableException(String message) {
        super(message);
    }

    public ReviewQueueUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
