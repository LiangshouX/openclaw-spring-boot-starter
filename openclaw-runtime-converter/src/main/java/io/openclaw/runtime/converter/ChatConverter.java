package io.openclaw.runtime.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openclaw.runtime.api.dto.ChatRequest;
import io.openclaw.runtime.api.dto.ChatResponse;

/** 聊天相关 DTO 转换器，负责 JSON、运行时和 OpenClaw 表示之间的转换。 */
public class ChatConverter {

    private final ObjectMapper objectMapper;

    public ChatConverter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 将 JSON 节点转换为运行时 {@link ChatRequest}。
     *
     * @param node 聊天请求的 JSON 表示
     * @return 转换后的 {@link ChatRequest}
     */
    public ChatRequest toRuntimeChatRequest(JsonNode node) {
        return objectMapper.convertValue(node, ChatRequest.class);
    }

    /**
     * 将运行时 {@link ChatRequest} 转换为 OpenClaw 兼容的 JSON 节点。
     *
     * @param request 运行时聊天请求
     * @return 用于 OpenClaw Gateway 的 JSON 表示
     */
    public JsonNode toOpenClawChatRequest(ChatRequest request) {
        return objectMapper.valueToTree(request);
    }

    /**
     * 将 JSON 节点转换为业务 {@link ChatResponse}。
     *
     * @param node 聊天响应的 JSON 表示
     * @return 转换后的 {@link ChatResponse}
     */
    public ChatResponse toBusinessChatResponse(JsonNode node) {
        return objectMapper.convertValue(node, ChatResponse.class);
    }
}
