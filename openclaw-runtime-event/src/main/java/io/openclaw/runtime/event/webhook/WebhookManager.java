package io.openclaw.runtime.event.webhook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/** Webhook 管理器，负责在 OpenClaw Gateway 上注册 Webhook 以接收异步事件通知。 */
public class WebhookManager {

    /**
     * 在 OpenClaw Gateway 上注册新的 Webhook 端点。
     *
     * @param url        接收事件通知的回调 URL
     * @param eventTypes 要订阅的事件类型列表
     * @return 注册后的 {@link WebhookConfig}；若注册失败则返回 {@code null}
     */
    public WebhookConfig registerWebhook(String url, List<String> eventTypes) {
        return null;
    }

    /**
     * 注销已注册的 Webhook。
     *
     * @param webhookId 要移除的 Webhook 标识符
     */
    public void unregisterWebhook(String webhookId) {
        // 桩实现 — 无操作
    }

    /**
     * 列出所有当前已注册的 Webhook。
     *
     * @return 已注册 Webhook 配置的不可修改列表
     */
    public List<WebhookConfig> listWebhooks() {
        return Collections.emptyList();
    }

    /** 已注册 Webhook 端点的配置。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WebhookConfig {

        private String webhookId;
        private String url;
        private List<String> eventTypes;
        private boolean active;
    }
}
