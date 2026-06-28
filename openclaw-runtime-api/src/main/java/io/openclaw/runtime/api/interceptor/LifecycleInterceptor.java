package io.openclaw.runtime.api.interceptor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openclaw.runtime.api.dto.ChatRequest;
import io.openclaw.runtime.api.dto.ChatResponse;
import io.openclaw.runtime.api.dto.SkillResult;

/**
 * 用于拦截 OpenClaw Runtime 生命周期事件的拦截器接口。
 * 所有方法均提供默认空实现。
 */
public interface LifecycleInterceptor {

    /** 聊天请求发送前调用。 */
    default void beforeRequest(ChatRequest request) {}

    /** 聊天响应接收后调用。 */
    default void afterResponse(ChatResponse response) {}

    /** 技能注册前调用。 */
    default void beforeRegisterSkill(String skillName) {}

    /** 技能注册后调用。 */
    default void afterRegisterSkill(String skillName) {}

    /** 工具调用执行前调用。 */
    default void beforeToolCall(String skillName, JsonNode arguments) {}

    /** 工具调用完成后调用。 */
    default void afterToolCall(String skillName, SkillResult result) {}

    /** 回调处理前调用。 */
    default void beforeCallback(String callbackType, JsonNode payload) {}

    /** 回调处理后调用。 */
    default void afterCallback(String callbackType, JsonNode payload) {}
}
