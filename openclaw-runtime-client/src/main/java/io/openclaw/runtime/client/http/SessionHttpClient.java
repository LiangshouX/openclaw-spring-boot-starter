package io.openclaw.runtime.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.reactive.function.client.WebClient;

/** 会话 HTTP 客户端，用于与 OpenClaw Gateway 进行会话相关操作。 */
public class SessionHttpClient {

    private final WebClient webClient;

    public SessionHttpClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 为指定工作空间创建新会话。
     *
     * @param workspaceId 要创建会话的工作空间标识符
     * @return 新创建会话的 JSON 表示
     */
    public JsonNode createSession(String workspaceId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * 根据标识符获取已有会话。
     *
     * @param sessionId 会话的唯一标识符
     * @return 会话的 JSON 表示
     */
    public JsonNode getSession(String sessionId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * 关闭活跃会话。
     *
     * @param sessionId 要关闭的会话的唯一标识符
     */
    public void closeSession(String sessionId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * 为指定会话发送心跳以保持存活。
     *
     * @param sessionId 会话的唯一标识符
     */
    public void heartbeat(String sessionId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
