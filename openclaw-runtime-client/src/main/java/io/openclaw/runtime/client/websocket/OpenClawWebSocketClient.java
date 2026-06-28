package io.openclaw.runtime.client.websocket;

import io.openclaw.runtime.api.dto.ChatRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;

/** WebSocket 客户端，用于与 OpenClaw Gateway 进行流式通信。 */
public class OpenClawWebSocketClient {

    /**
     * 建立 WebSocket 连接并以流式方式接收聊天响应块。
     *
     * @param sessionId 流式会话的标识符
     * @param request 通过 WebSocket 发送的聊天请求
     * @return 表示流式响应的 {@link ChatStreamChunk} {@link Flux}
     */
    public Flux<ChatStreamChunk> connect(String sessionId, ChatRequest request) {
        return Flux.empty();
    }

    /**
     * 关闭 WebSocket 连接。
     */
    public void close() {
        // no-op
    }

    /**
     * 检查 WebSocket 连接是否处于活跃状态。
     *
     * @return 连接活跃时返回 {@code true}，否则返回 {@code false}
     */
    public boolean isConnected() {
        return false;
    }

    /** WebSocket 流式传输中接收的单个数据块。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatStreamChunk {

        private String delta;
        private String type;
        private boolean done;
    }
}
