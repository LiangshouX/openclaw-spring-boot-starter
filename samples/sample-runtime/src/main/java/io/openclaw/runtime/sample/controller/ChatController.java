package io.openclaw.runtime.sample.controller;

import io.openclaw.runtime.api.dto.ChatResponse;
import io.openclaw.runtime.api.dto.RuntimeSession;
import io.openclaw.runtime.api.event.RuntimeEvent;
import io.openclaw.runtime.sample.service.ChatDemoService;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 演示 OpenClaw Runtime 端点的 REST 控制器。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatDemoService chatDemoService;

    public ChatController(ChatDemoService chatDemoService) {
        this.chatDemoService = chatDemoService;
    }

    /**
     * 发送同步聊天消息。
     *
     * @param sessionId 发送消息的会话标识符
     * @param message   消息文本内容
     * @return 来自 OpenClaw 的聊天响应
     */
    @PostMapping
    public ChatResponse chat(@RequestParam String sessionId,
                              @RequestParam String message) {
        return chatDemoService.sendChat(sessionId, message);
    }

    /**
     * 打开聊天消息的 SSE 事件流。
     *
     * @param sessionId 流式传输的会话标识符
     * @param message   消息文本内容
     * @return 表示流式响应的运行时事件 {@link Flux}
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<RuntimeEvent> stream(@RequestParam String sessionId,
                                      @RequestParam String message) {
        return chatDemoService.streamChat(sessionId, message);
    }

    /**
     * 创建新的运行时会话。
     *
     * @return 新创建的 {@link RuntimeSession}
     */
    @PostMapping("/session")
    public RuntimeSession createSession() {
        return chatDemoService.startSession();
    }

    /**
     * 关闭已有会话。
     *
     * @param sessionId 要关闭的会话标识符
     */
    @PostMapping("/session/{sessionId}/close")
    public void closeSession(@PathVariable String sessionId) {
        chatDemoService.closeSession(sessionId);
    }
}
