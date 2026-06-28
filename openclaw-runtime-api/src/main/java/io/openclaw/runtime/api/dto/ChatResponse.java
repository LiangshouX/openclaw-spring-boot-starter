package io.openclaw.runtime.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 表示从 OpenClaw Runtime 接收的聊天响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** 此响应的唯一标识符。 */
    private String requestId;
    /** 此响应所属的会话。 */
    private String sessionId;
    /** 响应的文本内容。 */
    private String content;
    /** 处理过程中 OpenClaw 请求的工具调用。 */
    private List<ToolCall> toolCalls;
    /** 与响应关联的附加元数据。 */
    private Map<String, Object> metadata;
    /** 此响应的生成时间戳。 */
    private Instant timestamp;

    /** 表示聊天处理过程中 OpenClaw 请求的工具调用。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {

        /** 此工具调用的唯一标识符。 */
        private String id;
        /** 要调用的技能名称。 */
        private String skillName;
        /** 传递给技能的参数。 */
        private JsonNode arguments;
    }
}
