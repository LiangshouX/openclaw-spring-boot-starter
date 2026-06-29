package io.openclaw.runtime.sample.service;

import io.openclaw.runtime.api.OpenClawRuntime;
import io.openclaw.runtime.api.dto.ChatRequest;
import io.openclaw.runtime.api.dto.ChatResponse;
import io.openclaw.runtime.api.dto.RuntimeSession;
import io.openclaw.runtime.api.dto.StreamMode;
import io.openclaw.runtime.api.event.RuntimeEvent;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 演示服务，展示如何使用 OpenClawRuntime 进行聊天、流式传输和会话管理。
 */
@Service
public class ChatDemoService {

    private final OpenClawRuntime runtime;

    public ChatDemoService(OpenClawRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * 发送同步聊天消息。
     */
    public ChatResponse sendChat(String sessionId, String message) {
        ChatRequest request = ChatRequest.builder()
                .sessionId(sessionId)
                .message(message)
                .mode(StreamMode.SYNC)
                .build();
        ChatResponse res = runtime.chat(request);

        return res;
    }

    /**
     * 发送流式聊天消息。
     */
    public Flux<RuntimeEvent> streamChat(String sessionId, String message) {
        ChatRequest request = ChatRequest.builder()
                .sessionId(sessionId)
                .message(message)
                .mode(StreamMode.STREAM)
                .build();
        return runtime.stream(request);
    }

    /**
     * 创建新会话。
     */
    public RuntimeSession startSession() {
        return runtime.createSession();
    }

    /**
     * 恢复已有会话。
     */
    public RuntimeSession resumeSession(String sessionId) {
        return runtime.resumeSession(sessionId);
    }

    /**
     * 关闭会话。
     */
    public void closeSession(String sessionId) {
        runtime.closeSession(sessionId);
    }
}
