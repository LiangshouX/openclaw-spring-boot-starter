package io.openclaw.runtime.tool.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openclaw.runtime.api.dto.ToolDefinition;
import io.openclaw.runtime.tool.registry.ToolManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 聊天请求级工具注册策略。
 * <p>
 * 将工具定义转换为 OpenAI 兼容的 {@code tools} 数组格式，
 * 供 {@link io.openclaw.runtime.client.http.ChatClient} 在每次聊天请求中携带。
 * <p>
 * 格式示例：
 * <pre>{@code
 * [
 *   {
 *     "type": "function",
 *     "function": {
 *       "name": "translate_text",
 *       "description": "Translate text between languages",
 *       "parameters": { "type": "object", "properties": { ... } }
 *     }
 *   }
 * ]
 * }</pre>
 */
public class ChatRequestToolStrategy implements ToolRegistrationStrategy {

    private static final Logger log = LoggerFactory.getLogger(ChatRequestToolStrategy.class);
    private static final String NAME = "chat-request";

    private final ObjectMapper objectMapper;
    private final List<ToolDefinition> registeredTools = new CopyOnWriteArrayList<>();

    public ChatRequestToolStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void register(ToolManifest manifest) {
        registeredTools.clear();
        registeredTools.addAll(manifest.getTools());
        log.info("Cached {} tool definitions for chat request injection", manifest.getTools().size());
    }

    @Override
    public void unregister(List<String> toolNames) {
        registeredTools.removeIf(def -> toolNames.contains(def.getName()));
        log.info("Removed {} tool definitions from chat request cache", toolNames.size());
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * 获取当前已注册工具的 OpenAI 兼容 {@code tools} 数组。
     * <p>
     * 此方法由 ChatClient 在构建请求体时调用，
     * 将工具定义注入到 {@code /v1/chat/completions} 请求中。
     *
     * @return OpenAI tools 数组格式的 JsonNode，无工具时返回空数组
     */
    public JsonNode getOpenAIToolsArray() {
        if (registeredTools.isEmpty()) {
            return objectMapper.createArrayNode();
        }

        ArrayNode toolsArray = objectMapper.createArrayNode();
        for (ToolDefinition def : registeredTools) {
            ObjectNode toolEntry = objectMapper.createObjectNode();
            toolEntry.put("type", "function");

            ObjectNode functionNode = objectMapper.createObjectNode();
            functionNode.put("name", def.getName());
            functionNode.put("description", def.getDescription() != null ? def.getDescription() : "");

            // Include JSON Schema for parameters if available
            if (def.getJsonSchema() != null) {
                functionNode.set("parameters", def.getJsonSchema());
            } else {
                ObjectNode emptySchema = objectMapper.createObjectNode();
                emptySchema.put("type", "object");
                emptySchema.set("properties", objectMapper.createObjectNode());
                functionNode.set("parameters", emptySchema);
            }

            toolEntry.set("function", functionNode);
            toolsArray.add(toolEntry);
        }

        return toolsArray;
    }

    /**
     * 返回当前已缓存的工具定义列表（只读视图）。
     */
    public List<ToolDefinition> getRegisteredTools() {
        return Collections.unmodifiableList(registeredTools);
    }
}
