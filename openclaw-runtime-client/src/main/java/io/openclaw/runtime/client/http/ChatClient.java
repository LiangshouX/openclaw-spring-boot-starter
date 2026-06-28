package io.openclaw.runtime.client.http;

import io.openclaw.runtime.api.dto.ChatRequest;
import io.openclaw.runtime.api.dto.ChatResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/** 聊天 HTTP 客户端，用于与 OpenClaw Gateway 进行聊天相关操作。 */
public class ChatClient {

    private final WebClient webClient;

    public ChatClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 向 OpenClaw Gateway 发送聊天消息并返回响应。
     *
     * @param request 包含消息内容的聊天请求
     * @return 网关返回的聊天响应
     */
    public ChatResponse sendMessage(ChatRequest request) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * 向 OpenClaw Gateway 流式发送聊天消息，返回文本块的响应式流。
     *
     * @param request 包含消息内容的聊天请求
     * @return 表示流式响应的字符串块 {@link Flux}
     */
    public Flux<String> streamMessage(ChatRequest request) {
        return Flux.empty();
    }
}
