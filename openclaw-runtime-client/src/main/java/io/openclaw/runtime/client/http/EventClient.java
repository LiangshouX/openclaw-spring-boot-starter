package io.openclaw.runtime.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openclaw.runtime.api.exception.ClientException;
import io.openclaw.runtime.api.exception.ErrorCode;
import io.openclaw.runtime.client.websocket.OpenClawWebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 事件 HTTP 客户端，通过 WebSocket RPC 与 OpenClaw Gateway 进行事件操作。
 * <p>
 * 使用 WebSocket RPC 方法：{@code system-event}（发布）、{@code chat.history}（获取）。
 */
public class EventClient {

    private static final Logger log = LoggerFactory.getLogger(EventClient.class);

    private final WebClient webClient;
    private final OpenClawWebSocketClient wsClient;
    private final ObjectMapper objectMapper;

    public EventClient(WebClient webClient) {
        this(webClient, null, new ObjectMapper());
    }

    public EventClient(WebClient webClient, OpenClawWebSocketClient wsClient) {
        this(webClient, wsClient, new ObjectMapper());
    }

    public EventClient(WebClient webClient, OpenClawWebSocketClient wsClient,
                       ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.wsClient = wsClient;
        this.objectMapper = objectMapper;
    }

    private void requireWsClient() {
        if (wsClient == null) {
            throw new ClientException(ErrorCode.WEBSOCKET_ERROR,
                    "WebSocket client not configured for EventClient");
        }
    }

    /**
     * 向指定会话发布事件。
     * <p>
     * 使用 {@code system-event} RPC 方法将事件注入到会话中。
     *
     * @param sessionId 目标会话标识符
     * @param event     事件负载（JSON 节点）
     * @throws ClientException 当发布失败时
     */
    public void publishEvent(String sessionId, JsonNode event) {
        requireWsClient();
        log.debug("Publishing event to session: {}", sessionId);
        Map<String, Object> params = new LinkedHashMap<>();
        if (sessionId != null) {
            params.put("sessionKey", sessionId);
        }
        params.put("event", event);
        wsClient.invoke("system-event", params)
                .onErrorMap(e -> {
                    if (e instanceof ClientException) return e;
                    return new ClientException(ErrorCode.WEBSOCKET_ERROR,
                            "Failed to publish event to session: " + sessionId, e);
                })
                .block();
    }

    /**
     * 获取指定会话关联的所有事件。
     * <p>
     * 使用 {@code chat.history} RPC 方法获取会话的事件/消息历史。
     *
     * @param sessionId 要查询的会话标识符
     * @return 事件 JSON 表示的列表
     * @throws ClientException 当查询失败时
     */
    public List<JsonNode> getEvents(String sessionId) {
        requireWsClient();
        log.debug("Getting events for session: {}", sessionId);

        Map<String, Object> params = new LinkedHashMap<>();
        if (sessionId != null) {
            params.put("sessionKey", sessionId);
        }

        JsonNode result = wsClient.invoke("chat.history", params)
                .onErrorMap(e -> {
                    if (e instanceof ClientException) return e;
                    return new ClientException(ErrorCode.WEBSOCKET_ERROR,
                            "Failed to get events for session: " + sessionId, e);
                })
                .block();

        if (result == null) {
            return Collections.emptyList();
        }

        // Response may contain { messages: [...] } or be a direct array
        JsonNode eventsArray = result.path("messages");
        if (eventsArray.isMissingNode() || !eventsArray.isArray()) {
            eventsArray = result.path("history");
        }
        if (eventsArray.isMissingNode() || !eventsArray.isArray()) {
            if (result.isArray()) {
                eventsArray = result;
            } else {
                return Collections.emptyList();
            }
        }

        List<JsonNode> events = new ArrayList<>();
        eventsArray.forEach(events::add);
        return events;
    }
}
