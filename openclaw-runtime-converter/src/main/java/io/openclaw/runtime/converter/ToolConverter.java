package io.openclaw.runtime.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openclaw.runtime.api.dto.ToolDefinition;
import io.openclaw.runtime.api.dto.ToolResult;

/** 工具相关 DTO 转换器，负责 JSON 和运行时表示之间的转换。 */
public class ToolConverter {

    private final ObjectMapper objectMapper;

    public ToolConverter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 将 JSON 节点转换为运行时 {@link ToolDefinition}。
     *
     * @param node 工具定义的 JSON 表示
     * @return 转换后的 {@link ToolDefinition}
     */
    public ToolDefinition toToolDefinition(JsonNode node) {
        return objectMapper.convertValue(node, ToolDefinition.class);
    }

    /**
     * 将 JSON 节点转换为业务 {@link ToolResult}。
     *
     * @param node 工具结果的 JSON 表示
     * @return 转换后的 {@link ToolResult}
     */
    public ToolResult toBusinessToolResult(JsonNode node) {
        return objectMapper.convertValue(node, ToolResult.class);
    }

    /**
     * 将运行时 {@link ToolDefinition} 转换为 OpenClaw 兼容的 JSON 节点。
     *
     * @param definition 运行时工具定义
     * @return 用于 OpenClaw Gateway 的 JSON 表示
     */
    public JsonNode toOpenClawToolDefinition(ToolDefinition definition) {
        return objectMapper.valueToTree(definition);
    }
}
