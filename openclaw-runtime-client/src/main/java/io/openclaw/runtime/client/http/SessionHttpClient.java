package io.openclaw.runtime.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openclaw.runtime.api.exception.ClientException;
import io.openclaw.runtime.api.exception.ErrorCode;
import io.openclaw.runtime.client.websocket.OpenClawWebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会话 HTTP 客户端，通过 WebSocket RPC 与 OpenClaw Gateway 进行会话操作。
 * <p>
 * 使用 WebSocket RPC 方法：{@code sessions.create}、{@code sessions.get}、
 * {@code sessions.delete}、{@code sessions.describe}（心跳保活）。
 */
public class SessionHttpClient {

    private static final Logger log = LoggerFactory.getLogger(SessionHttpClient.class);

    private final WebClient webClient;
    private final OpenClawWebSocketClient wsClient;
    private final ObjectMapper objectMapper;

    public SessionHttpClient(WebClient webClient) {
        this(webClient, null, new ObjectMapper());
    }

    public SessionHttpClient(WebClient webClient, OpenClawWebSocketClient wsClient) {
        this(webClient, wsClient, new ObjectMapper());
    }

    public SessionHttpClient(WebClient webClient, OpenClawWebSocketClient wsClient,
                             ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.wsClient = wsClient;
        this.objectMapper = objectMapper;
    }

    private void requireWsClient() {
        if (wsClient == null) {
            throw new ClientException(ErrorCode.WEBSOCKET_ERROR,
                    "WebSocket client not configured for SessionHttpClient");
        }
    }

    /**
     * 为指定工作空间创建新会话。
     *
     * @param workspaceId 要创建会话的工作空间标识符
     * @return 新创建会话的 JSON 表示
     * @throws ClientException 当创建失败时
     */
    public JsonNode createSession(String workspaceId) {
        requireWsClient();
        log.debug("Creating session for workspace: {}", workspaceId);
        Map<String, Object> params = new LinkedHashMap<>();
        if (workspaceId != null) {
            params.put("workspaceId", workspaceId);
        }
        return wsClient.invoke("sessions.create", params)
                .onErrorMap(e -> {
                    if (e instanceof ClientException) return e;
                    return new ClientException(ErrorCode.SESSION_CREATE_FAILED,
                            "Failed to create session for workspace: " + workspaceId, e);
                })
                .block();
    }

    /**
     * 根据标识符获取已有会话。
     *
     * @param sessionId 会话的唯一标识符
     * @return 会话的 JSON 表示
     * @throws ClientException 当会话不存在或查询失败时
     */
    public JsonNode getSession(String sessionId) {
        requireWsClient();
        log.debug("Getting session: {}", sessionId);
        return wsClient.invoke("sessions.get", Map.of("key", sessionId))
                .onErrorMap(e -> {
                    if (e instanceof ClientException) return e;
                    return new ClientException(ErrorCode.SESSION_NOT_FOUND,
                            "Session not found: " + sessionId, e);
                })
                .block();
    }

    /**
     * 关闭活跃会话。
     *
     * @param sessionId 要关闭的会话的唯一标识符
     * @throws ClientException 当关闭失败时
     */
    public void closeSession(String sessionId) {
        requireWsClient();
        log.debug("Closing session: {}", sessionId);
        wsClient.invoke("sessions.delete", Map.of("key", sessionId))
                .onErrorMap(e -> {
                    if (e instanceof ClientException) return e;
                    return new ClientException(ErrorCode.SESSION_CLOSED,
                            "Failed to close session: " + sessionId, e);
                })
                .block();
    }

    /**
     * 为指定会话发送心跳以保持存活。
     * 使用轻量级的 {@code sessions.describe} RPC 作为心跳机制。
     *
     * @param sessionId 会话的唯一标识符
     */
    public void heartbeat(String sessionId) {
        requireWsClient();
        log.trace("Heartbeat for session: {}", sessionId);
        wsClient.invoke("sessions.describe", Map.of("key", sessionId))
                .onErrorMap(e -> {
                    if (e instanceof ClientException) return e;
                    return new ClientException(ErrorCode.WEBSOCKET_ERROR,
                            "Heartbeat failed for session: " + sessionId, e);
                })
                .block();
    }
}
