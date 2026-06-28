package io.openclaw.runtime.converter;

import com.fasterxml.jackson.databind.JsonNode;
import io.openclaw.runtime.api.dto.ChatRequest;
import io.openclaw.runtime.api.dto.ChatResponse;
import io.openclaw.runtime.api.dto.RuntimeSession;
import io.openclaw.runtime.api.dto.SkillResult;
import io.openclaw.runtime.api.event.RuntimeEvent;

/** 运行时转换器门面，委托各领域专用转换器实现层间 DTO 转换。 */
public class RuntimeConverter {

    private final ChatConverter chatConverter;
    private final SessionConverter sessionConverter;
    private final SkillConverter skillConverter;
    private final EventConverter eventConverter;

    public RuntimeConverter(ChatConverter chatConverter,
                            SessionConverter sessionConverter,
                            SkillConverter skillConverter,
                            EventConverter eventConverter) {
        this.chatConverter = chatConverter;
        this.sessionConverter = sessionConverter;
        this.skillConverter = skillConverter;
        this.eventConverter = eventConverter;
    }

    /**
     * 将 OpenClaw 聊天请求的原始 JSON 转换为运行时 {@link ChatRequest}。
     *
     * @param openClawRequest 来自 OpenClaw Gateway 的原始 JSON
     * @return 转换后的 {@link ChatRequest}
     */
    public ChatRequest toRuntimeChatRequest(JsonNode openClawRequest) {
        return chatConverter.toRuntimeChatRequest(openClawRequest);
    }

    /**
     * 将运行时 {@link ChatRequest} 转换为 OpenClaw 兼容的 JSON 节点。
     *
     * @param request 运行时聊天请求
     * @return 用于 OpenClaw Gateway 的 JSON 表示
     */
    public JsonNode toOpenClawChatRequest(ChatRequest request) {
        return chatConverter.toOpenClawChatRequest(request);
    }

    /**
     * 将 OpenClaw 聊天响应的原始 JSON 转换为业务 {@link ChatResponse}。
     *
     * @param openClawResponse 来自 OpenClaw Gateway 的原始 JSON 响应
     * @return 转换后的 {@link ChatResponse}
     */
    public ChatResponse toBusinessChatResponse(JsonNode openClawResponse) {
        return chatConverter.toBusinessChatResponse(openClawResponse);
    }

    /**
     * 将 OpenClaw 会话的原始 JSON 转换为运行时 {@link RuntimeSession}。
     *
     * @param openClawSession 来自 OpenClaw Gateway 的原始会话 JSON
     * @return 转换后的 {@link RuntimeSession}
     */
    public RuntimeSession toRuntimeSession(JsonNode openClawSession) {
        return sessionConverter.toRuntimeSession(openClawSession);
    }

    /**
     * 将 OpenClaw 技能结果的原始 JSON 转换为业务 {@link SkillResult}。
     *
     * @param openClawResult 来自 OpenClaw Gateway 的原始技能结果 JSON
     * @return 转换后的 {@link SkillResult}
     */
    public SkillResult toBusinessSkillResult(JsonNode openClawResult) {
        return skillConverter.toBusinessSkillResult(openClawResult);
    }

    /**
     * 将 OpenClaw 事件的原始 JSON 转换为运行时 {@link RuntimeEvent}。
     *
     * @param openClawEvent 来自 OpenClaw Gateway 的原始事件 JSON
     * @return 转换后的 {@link RuntimeEvent}
     */
    public RuntimeEvent toRuntimeEvent(JsonNode openClawEvent) {
        return eventConverter.toRuntimeEvent(openClawEvent);
    }
}
