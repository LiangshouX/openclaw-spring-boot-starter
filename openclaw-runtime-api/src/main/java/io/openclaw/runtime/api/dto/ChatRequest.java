package io.openclaw.runtime.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** 表示发送到 OpenClaw Runtime 的聊天请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /** 与此请求关联的会话标识符。 */
    private String sessionId;
    /** 多轮对话的会话标识符。 */
    private String conversationId;
    /** 用户消息内容。 */
    private String message;
    /** 可选的变量，用于替换请求上下文中的占位符。 */
    private Map<String, Object> variables;
    /** 此请求的流式模式。 */
    private StreamMode mode;
}
