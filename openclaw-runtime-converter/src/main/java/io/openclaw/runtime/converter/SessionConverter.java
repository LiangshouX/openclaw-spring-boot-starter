package io.openclaw.runtime.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openclaw.runtime.api.dto.RuntimeSession;

/** 会话相关 DTO 转换器，负责 JSON 和运行时表示之间的转换。 */
public class SessionConverter {

    private final ObjectMapper objectMapper;

    public SessionConverter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 将 JSON 节点转换为运行时 {@link RuntimeSession}。
     *
     * @param node 会话的 JSON 表示
     * @return 转换后的 {@link RuntimeSession}
     */
    public RuntimeSession toRuntimeSession(JsonNode node) {
        return objectMapper.convertValue(node, RuntimeSession.class);
    }

    /**
     * 将运行时 {@link RuntimeSession} 转换为 OpenClaw 兼容的 JSON 节点。
     *
     * @param session 运行时会话
     * @return 用于 OpenClaw Gateway 的 JSON 表示
     */
    public JsonNode toOpenClawSession(RuntimeSession session) {
        return objectMapper.valueToTree(session);
    }
}
