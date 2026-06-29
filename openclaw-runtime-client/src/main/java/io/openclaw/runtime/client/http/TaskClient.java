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
 * 任务 HTTP 客户端，通过 WebSocket RPC 与 OpenClaw Gateway 进行任务操作。
 * <p>
 * 使用 WebSocket RPC 方法：{@code tasks.get}、{@code tasks.list}、{@code tasks.cancel}。
 */
public class TaskClient {

    private static final Logger log = LoggerFactory.getLogger(TaskClient.class);

    private final WebClient webClient;
    private final OpenClawWebSocketClient wsClient;
    private final ObjectMapper objectMapper;

    public TaskClient(WebClient webClient) {
        this(webClient, null, new ObjectMapper());
    }

    public TaskClient(WebClient webClient, OpenClawWebSocketClient wsClient) {
        this(webClient, wsClient, new ObjectMapper());
    }

    public TaskClient(WebClient webClient, OpenClawWebSocketClient wsClient,
                      ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.wsClient = wsClient;
        this.objectMapper = objectMapper;
    }

    private void requireWsClient() {
        if (wsClient == null) {
            throw new ClientException(ErrorCode.WEBSOCKET_ERROR,
                    "WebSocket client not configured for TaskClient");
        }
    }

    /**
     * 根据标识符获取任务信息。
     *
     * @param taskId 任务的唯一标识符
     * @return 任务的 JSON 表示
     * @throws ClientException 当查询失败时
     */
    public JsonNode getTask(String taskId) {
        requireWsClient();
        log.debug("Getting task: {}", taskId);
        return wsClient.invoke("tasks.get", Map.of("taskId", taskId))
                .onErrorMap(e -> {
                    if (e instanceof ClientException) return e;
                    return new ClientException(ErrorCode.WEBSOCKET_ERROR,
                            "Failed to get task: " + taskId, e);
                })
                .block();
    }

    /**
     * 列出指定会话关联的所有任务。
     *
     * @param sessionId 要查询的会话标识符
     * @return 任务 JSON 表示的列表
     * @throws ClientException 当查询失败时
     */
    public List<JsonNode> listTasks(String sessionId) {
        requireWsClient();
        log.debug("Listing tasks for session: {}", sessionId);

        Map<String, Object> params = new LinkedHashMap<>();
        if (sessionId != null) {
            params.put("sessionKey", sessionId);
        }
        params.put("limit", 100);

        JsonNode result = wsClient.invoke("tasks.list", params)
                .onErrorMap(e -> {
                    if (e instanceof ClientException) return e;
                    return new ClientException(ErrorCode.WEBSOCKET_ERROR,
                            "Failed to list tasks for session: " + sessionId, e);
                })
                .block();

        if (result == null) {
            return Collections.emptyList();
        }

        // Response payload contains { tasks: [...] }
        JsonNode tasksArray = result.path("tasks");
        if (tasksArray.isMissingNode() || !tasksArray.isArray()) {
            // Fallback: try treating the result itself as the array
            if (result.isArray()) {
                tasksArray = result;
            } else {
                return Collections.emptyList();
            }
        }

        List<JsonNode> tasks = new ArrayList<>();
        tasksArray.forEach(tasks::add);
        return tasks;
    }

    /**
     * 根据标识符取消正在运行的任务。
     *
     * @param taskId 要取消的任务的唯一标识符
     * @throws ClientException 当取消操作失败时
     */
    public void cancelTask(String taskId) {
        requireWsClient();
        log.debug("Cancelling task: {}", taskId);
        wsClient.invoke("tasks.cancel", Map.of("taskId", taskId))
                .onErrorMap(e -> {
                    if (e instanceof ClientException) return e;
                    return new ClientException(ErrorCode.WEBSOCKET_ERROR,
                            "Failed to cancel task: " + taskId, e);
                })
                .block();
    }
}
