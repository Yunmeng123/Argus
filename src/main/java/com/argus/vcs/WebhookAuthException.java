package com.argus.vcs;

/** Webhook 验签失败(或未配置 Secret), 由入口统一转成 403 */
public class WebhookAuthException extends RuntimeException {

    public WebhookAuthException(String message) {
        super(message);
    }
}
