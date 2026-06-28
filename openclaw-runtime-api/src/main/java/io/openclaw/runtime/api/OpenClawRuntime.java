package io.openclaw.runtime.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.openclaw.runtime.api.dto.ChatRequest;
import io.openclaw.runtime.api.dto.ChatResponse;
import io.openclaw.runtime.api.dto.RuntimeSession;
import io.openclaw.runtime.api.event.RuntimeEvent;
import io.openclaw.runtime.api.listener.RuntimeListener;
import reactor.core.publisher.Flux;

/**
 * OpenClaw Runtime 的核心入口接口。
 * 提供同步和流式聊天、会话管理、技能注册和事件监听等方法。
 */
public interface OpenClawRuntime {

    /**
     * 向 OpenClaw 发送同步聊天请求。
     *
     * @param request 包含消息和会话详情的聊天请求
     * @return 包含响应内容和可选工具调用的聊天响应
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 打开与 OpenClaw 的流式聊天连接。
     *
     * @param request 包含消息和会话详情的聊天请求
     * @return 表示流式响应的运行时事件 Flux
     */
    Flux<RuntimeEvent> stream(ChatRequest request);

    /**
     * 创建新的运行时会话。
     *
     * @return 创建的会话
     */
    RuntimeSession createSession();

    /**
     * 通过 ID 恢复已有会话。
     *
     * @param sessionId 要恢复的会话标识符
     * @return 恢复的会话
     */
    RuntimeSession resumeSession(String sessionId);

    /**
     * 关闭活跃会话。
     *
     * @param sessionId 要关闭的会话标识符
     */
    void closeSession(String sessionId);

    /**
     * 手动触发向 OpenClaw 的技能注册。
     */
    void registerSkill();

    /**
     * 注册运行时事件监听器。
     *
     * @param listener 要注册的监听器
     */
    void addListener(RuntimeListener listener);
}
