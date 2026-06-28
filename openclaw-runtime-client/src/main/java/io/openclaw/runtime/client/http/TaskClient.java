package io.openclaw.runtime.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;

/** 任务 HTTP 客户端，用于与 OpenClaw Gateway 进行任务相关操作。 */
public class TaskClient {

    private final WebClient webClient;

    public TaskClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 根据标识符获取任务信息。
     *
     * @param taskId 任务的唯一标识符
     * @return 任务的 JSON 表示
     */
    public JsonNode getTask(String taskId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * 列出指定会话关联的所有任务。
     *
     * @param sessionId 要查询的会话标识符
     * @return 任务 JSON 表示的列表
     */
    public List<JsonNode> listTasks(String sessionId) {
        return Collections.emptyList();
    }

    /**
     * 根据标识符取消正在运行的任务。
     *
     * @param taskId 要取消的任务的唯一标识符
     */
    public void cancelTask(String taskId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
