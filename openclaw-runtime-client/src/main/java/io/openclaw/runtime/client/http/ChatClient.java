package io.openclaw.runtime.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openclaw.runtime.api.dto.ChatRequest;
import io.openclaw.runtime.api.dto.ChatResponse;
import io.openclaw.runtime.api.exception.ClientException;
import io.openclaw.runtime.api.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.*;

/**
 * 聊天客户端，通过 HTTP {@code POST /v1/chat/completions} 与 OpenClaw Gateway 进行聊天操作。
 * <p>
 * Gateway 的 HTTP 端点在使用 shared-secret auth 时自动获得完整 operator 权限
 * （包括 {@code operator.write}），无需设备配对。
 * <p>
 * 该端点默认关闭，需在 Gateway 配置中启用：
 * <pre>{@code
 * {
 *   gateway: {
 *     http: {
 *       endpoints: {
 *         chatCompletions: { enabled: true }
 *       }
 *     }
 *   }
 * }
 * }</pre>
 * <p>
 * 请求格式遵循 OpenAI Chat Completions 规范，使用 {@code model: "openclaw/default"}
 * 路由到 Gateway 的默认 agent。
 */
public class ChatClient {

    private static final Logger log = LoggerFactory.getLogger(ChatClient.class);
    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String DEFAULT_MODEL = "openclaw/default";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ChatClient(WebClient webClient) {
        this(webClient, new ObjectMapper());
    }

    public ChatClient(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 向后兼容的构造函数。
     * @deprecated WebSocket 参数不再需要，请使用 {@link #ChatClient(WebClient, ObjectMapper)}
     */
    @Deprecated
    public ChatClient(WebClient webClient,
                      io.openclaw.runtime.client.websocket.OpenClawWebSocketClient wsClient,
                      ObjectMapper objectMapper) {
        this(webClient, objectMapper);
    }

    private void requireWebClient() {
        if (webClient == null) {
            throw new ClientException(ErrorCode.HTTP_ERROR,
                    "WebClient not configured for ChatClient");
        }
    }

    /**
     * 向 OpenClaw Gateway 发送聊天消息并等待完整响应。
     * <p>
     * 通过 HTTP {@code POST /v1/chat/completions} 发送请求，使用 OpenAI 兼容格式。
     * Session 路由通过 {@code x-openclaw-session-key} 请求头或 {@code user} 字段控制。
     *
     * @param request 包含消息内容的聊天请求
     * @return 网关返回的聊天响应
     * @throws ClientException 当请求失败或端点未启用时
     */
    public ChatResponse sendMessage(ChatRequest request) {
        requireWebClient();
        log.debug("Sending chat message via HTTP /v1/chat/completions, sessionId={}",
                request.getSessionId());

        Map<String, Object> body = buildRequestBody(request, false);

        try {
            JsonNode response = webClient.post()
                    .uri(CHAT_COMPLETIONS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> addSessionHeaders(headers, request))
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(errorBody -> new ClientException(ErrorCode.HTTP_ERROR,
                                            "Chat completions HTTP error: " + clientResponse.statusCode()
                                                    + " — " + errorBody
                                                    + " (hint: ensure gateway.http.endpoints.chatCompletions.enabled=true)")))
                    .bodyToMono(JsonNode.class)
                    .block();

            return parseChatResponse(response, request);
        } catch (ClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ClientException(ErrorCode.HTTP_ERROR,
                    "Chat completions request failed", e);
        }
    }

    /**
     * 向 OpenClaw Gateway 流式发送聊天消息，返回文本增量的响应式流。
     * <p>
     * 通过 HTTP {@code POST /v1/chat/completions} 发送 {@code stream: true} 请求，
     * 解析 Server-Sent Events (SSE) 流，提取 {@code choices[0].delta.content} 作为文本增量。
     *
     * @param request 包含消息内容的聊天请求
     * @return 表示流式响应的文本增量 {@link Flux}
     */
    public Flux<String> streamMessage(ChatRequest request) {
        if (webClient == null) {
            return Flux.error(new ClientException(ErrorCode.HTTP_ERROR,
                    "WebClient not configured for ChatClient"));
        }

        log.debug("Opening chat stream via HTTP /v1/chat/completions, sessionId={}",
                request.getSessionId());

        Map<String, Object> body = buildRequestBody(request, true);

        return webClient.post()
                .uri(CHAT_COMPLETIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .headers(headers -> addSessionHeaders(headers, request))
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> !line.isBlank() && !"[DONE]".equals(line.trim()))
                .mapNotNull(this::extractDeltaContent)
                .filter(delta -> !delta.isEmpty())
                .onErrorMap(e -> {
                    if (e instanceof ClientException) return e;
                    return new ClientException(ErrorCode.HTTP_ERROR,
                            "Chat stream failed (hint: ensure gateway.http.endpoints.chatCompletions.enabled=true)",
                            e);
                });
    }

    // ─── Internal Helpers ──────────────────────────────────────────

    private Map<String, Object> buildRequestBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", DEFAULT_MODEL);
        body.put("stream", stream);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", request.getMessage());
        messages.add(userMessage);
        body.put("messages", messages);

        // Use OpenAI 'user' field for stable session routing
        String sessionKey = resolveSessionKey(request);
        if (sessionKey != null) {
            body.put("user", sessionKey);
        }

        return body;
    }

    private void addSessionHeaders(org.springframework.http.HttpHeaders headers,
                                    ChatRequest request) {
        String sessionKey = resolveSessionKey(request);
        if (sessionKey != null) {
            headers.set("x-openclaw-session-key", sessionKey);
        }
    }

    private String resolveSessionKey(ChatRequest request) {
        if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
            return request.getSessionId();
        }
        if (request.getConversationId() != null && !request.getConversationId().isBlank()) {
            return request.getConversationId();
        }
        return null;
    }

    private ChatResponse parseChatResponse(JsonNode response, ChatRequest request) {
        if (response == null) {
            throw new ClientException(ErrorCode.HTTP_ERROR,
                    "Empty response from chat completions endpoint");
        }

        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new ClientException(ErrorCode.HTTP_ERROR,
                    "No choices in chat completions response");
        }

        JsonNode firstChoice = choices.get(0);
        JsonNode message = firstChoice.path("message");
        String content = message.path("content").asText("");
        String finishReason = firstChoice.path("finish_reason").asText("");

        // Parse tool calls if present
        List<ChatResponse.ToolCall> toolCalls = new ArrayList<>();
        JsonNode toolCallsNode = message.path("tool_calls");
        if (toolCallsNode.isArray()) {
            for (JsonNode tc : toolCallsNode) {
                JsonNode fn = tc.path("function");
                String argStr = fn.path("arguments").asText("{}");
                JsonNode argNode;
                try {
                    argNode = objectMapper.readTree(argStr);
                } catch (Exception e) {
                    argNode = objectMapper.createObjectNode();
                }
                toolCalls.add(ChatResponse.ToolCall.builder()
                        .id(tc.path("id").asText(""))
                        .skillName(fn.path("name").asText(""))
                        .arguments(argNode)
                        .build());
            }
        }

        return ChatResponse.builder()
                .requestId(response.path("id").asText(UUID.randomUUID().toString()))
                .sessionId(request.getSessionId())
                .content(content)
                .toolCalls(toolCalls)
                .metadata(Map.of("finish_reason", finishReason,
                        "model", response.path("model").asText("")))
                .timestamp(Instant.now())
                .build();
    }

    /**
     * 从 SSE JSON chunk 中提取 delta content。
     * SSE 格式: data: {"choices":[{"delta":{"content":"..."}}]}
     */
    private String extractDeltaContent(String sseLine) {
        try {
            // Strip "data: " prefix if present
            String json = sseLine.startsWith("data:") ? sseLine.substring(5).trim() : sseLine.trim();
            if (json.isEmpty() || "[DONE]".equals(json)) {
                return null;
            }
            JsonNode node = objectMapper.readTree(json);
            JsonNode delta = node.path("choices").path(0).path("delta");
            JsonNode content = delta.path("content");
            if (content.isMissingNode() || content.isNull()) {
                return null;
            }
            return content.asText("");
        } catch (Exception e) {
            log.trace("Failed to parse SSE chunk: {}", sseLine, e);
            return null;
        }
    }
}
