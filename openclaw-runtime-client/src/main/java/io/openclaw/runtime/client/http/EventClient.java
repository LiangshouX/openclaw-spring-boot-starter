package io.openclaw.runtime.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;

/** 事件 HTTP 客户端，用于与 OpenClaw Gateway 进行事件相关操作。 */
public class EventClient {

    private final WebClient webClient;

    public EventClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 向指定会话发布事件。
     *
     * @param sessionId 目标会话标识符
     * @param event 事件负载（JSON 节点）
     */
    public void publishEvent(String sessionId, JsonNode event) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * 获取指定会话关联的所有事件。
     *
     * @param sessionId 要查询的会话标识符
     * @return 事件 JSON 表示的列表
     */
    public List<JsonNode> getEvents(String sessionId) {
        return Collections.emptyList();
    }
}
