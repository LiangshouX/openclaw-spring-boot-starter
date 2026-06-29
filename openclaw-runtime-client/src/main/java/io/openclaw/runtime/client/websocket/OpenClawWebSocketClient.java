package io.openclaw.runtime.client.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openclaw.runtime.api.dto.ChatRequest;
import io.openclaw.runtime.api.exception.ClientException;
import io.openclaw.runtime.api.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket 客户端，实现与 OpenClaw Gateway 的完整 WebSocket 协议通信。
 * <p>
 * 支持 WebSocket 握手、RPC 方法调用、事件订阅、心跳保活和自动重连。
 * 其他 RPC 客户端（Session、Task、Event、Artifact）通过 {@link #invoke(String, Object)} 方法
 * 发送 WebSocket RPC 请求。
 */
public class OpenClawWebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(OpenClawWebSocketClient.class);

    private final String endpoint;
    private final String token;
    private final ObjectMapper objectMapper;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final int protocolVersion;
    private final int maxReconnectAttempts;

    // Connection state
    private volatile WebSocketSession session;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private volatile long tickIntervalMs = 15_000L;
    private final Sinks.Many<String> outbound = Sinks.many().multicast().onBackpressureBuffer();

    // RPC tracking
    private final AtomicLong requestIdCounter = new AtomicLong(0);
    private final ConcurrentHashMap<String, Sinks.One<JsonNode>> pendingRequests = new ConcurrentHashMap<>();

    // Event subscribers
    private final CopyOnWriteArrayList<EventSubscriber> eventSubscribers = new CopyOnWriteArrayList<>();

    // Tick timer
    private volatile Disposable tickDisposable;

    // Reconnection
    private volatile int reconnectAttempt = 0;
    private static final int DEFAULT_MAX_RECONNECT_ATTEMPTS = 10;
    private static final int MIN_RECONNECT_DELAY_SEC = 1;
    private static final int MAX_RECONNECT_DELAY_SEC = 30;

    // Chat streaming sink (backward compat)
    private volatile Sinks.Many<ChatStreamChunk> chatSink = Sinks.many().multicast().onBackpressureBuffer();

    enum ConnectionState {
        DISCONNECTED, CONNECTING, CHALLENGING, AUTHENTICATING, CONNECTED, CLOSING
    }

    // ─── Constructors ──────────────────────────────────────────────

    /**
     * 使用指定端点、令牌和 ObjectMapper 创建 WebSocket 客户端。
     *
     * @param endpoint     WebSocket 端点 URL（例如 ws://host:port）
     * @param token        认证令牌
     * @param objectMapper JSON 序列化器
     */
    public OpenClawWebSocketClient(String endpoint, String token, ObjectMapper objectMapper) {
        this(endpoint, token, objectMapper,
                Duration.ofSeconds(15), Duration.ofSeconds(30), 4, DEFAULT_MAX_RECONNECT_ATTEMPTS);
    }

    /**
     * 使用完整配置创建 WebSocket 客户端。
     *
     * @param endpoint             WebSocket 端点 URL
     * @param token                认证令牌
     * @param objectMapper         JSON 序列化器
     * @param connectTimeout       握手连接超时时间
     * @param requestTimeout       RPC 请求超时时间
     * @param protocolVersion      协议版本
     * @param maxReconnectAttempts 最大重连次数
     */
    public OpenClawWebSocketClient(String endpoint, String token, ObjectMapper objectMapper,
                                   Duration connectTimeout, Duration requestTimeout,
                                   int protocolVersion, int maxReconnectAttempts) {
        this.endpoint = endpoint;
        this.token = token;
        this.objectMapper = objectMapper;
        this.connectTimeout = connectTimeout;
        this.requestTimeout = requestTimeout;
        this.protocolVersion = protocolVersion;
        this.maxReconnectAttempts = maxReconnectAttempts;
    }

    /** 向后兼容的无参构造函数。 */
    @Deprecated
    public OpenClawWebSocketClient() {
        this(null, null, new ObjectMapper());
    }

    // ─── Public API ────────────────────────────────────────────────

    /**
     * 建立 WebSocket 连接并完成握手协议。
     * <p>
     * 握手流程：connect.challenge → connect request → hello-ok
     *
     * @return 连接成功时完成的 Mono
     */
    public Mono<Void> connect() {
        if (endpoint == null || endpoint.isBlank()) {
            return Mono.error(new ClientException(ErrorCode.CONNECTION_REFUSED,
                    "WebSocket endpoint not configured"));
        }
        if (state == ConnectionState.CONNECTED) {
            return Mono.empty();
        }

        return Mono.<Void>create(sink -> {
            state = ConnectionState.CONNECTING;
            WebSocketClient client = new ReactorNettyWebSocketClient();
            URI uri = URI.create(endpoint);

            client.execute(uri, wsSession -> {
                this.session = wsSession;
                state = ConnectionState.CHALLENGING;

                // Process incoming frames, routing each through handleIncomingFrame
                Mono<Void> incoming = wsSession.receive()
                        .map(WebSocketMessage::getPayloadAsText)
                        .doOnNext(text -> handleIncomingFrame(text, sink))
                        .doOnError(error -> handleConnectionError(error))
                        .doOnTerminate(this::handleConnectionClosed)
                        .then();

                // Pipe outbound sink to the WebSocket send channel
                Mono<Void> sending = wsSession.send(
                        outbound.asFlux().map(wsSession::textMessage));

                return Mono.when(incoming, sending);
            }).subscribe(
                    v -> { /* execute handler completed — connection closed normally */ },
                    error -> {
                        state = ConnectionState.DISCONNECTED;
                        sink.error(new ClientException(ErrorCode.CONNECTION_REFUSED,
                                "Failed to connect to " + endpoint, error));
                    }
            );
        }).timeout(connectTimeout, Mono.error(new ClientException(ErrorCode.CONNECTION_TIMEOUT,
                "WebSocket handshake timed out after " + connectTimeout)));
    }

    /**
     * 通过 WebSocket 调用 RPC 方法并返回响应负载。
     * <p>
     * 其他客户端（Session、Task、Event、Artifact）通过此方法发送 RPC 请求。
     *
     * @param method RPC 方法名（例如 "sessions.create"、"tasks.get"）
     * @param params 参数对象（将被序列化为 JSON）
     * @return 响应负载 JsonNode 的 Mono
     */
    public Mono<JsonNode> invoke(String method, Object params) {
        return Mono.create(sink -> {
            if (state != ConnectionState.CONNECTED) {
                sink.error(new ClientException(ErrorCode.WEBSOCKET_ERROR,
                        "WebSocket not connected, current state: " + state));
                return;
            }

            String id = String.valueOf(requestIdCounter.incrementAndGet());
            ObjectNode frame = objectMapper.createObjectNode();
            frame.put("type", "req");
            frame.put("id", id);
            frame.put("method", method);
            frame.set("params", objectMapper.valueToTree(params));

            Sinks.One<JsonNode> responseSink = Sinks.one();
            pendingRequests.put(id, responseSink);

            responseSink.asMono()
                    .timeout(requestTimeout)
                    .subscribe(
                            sink::success,
                            error -> {
                                pendingRequests.remove(id);
                                if (error instanceof java.util.concurrent.TimeoutException) {
                                    sink.error(new ClientException(ErrorCode.REQUEST_TIMEOUT,
                                            "RPC timeout for method: " + method));
                                } else {
                                    sink.error(error);
                                }
                            }
                    );

            sendFrame(frame);
        });
    }

    /** 关闭 WebSocket 连接，清理所有资源。 */
    public void close() {
        state = ConnectionState.CLOSING;
        stopTickTimer();

        // Fail all pending requests
        pendingRequests.forEach((id, responseSink) ->
                responseSink.tryEmitError(new ClientException(ErrorCode.WEBSOCKET_ERROR,
                        "WebSocket connection closing")));
        pendingRequests.clear();

        // Complete the outbound sink to signal the handler to finish
        outbound.tryEmitComplete();

        // Close the WebSocket session
        if (session != null) {
            session.close().subscribe(
                    v -> {}, e -> log.debug("Error closing WebSocket session", e));
        }

        state = ConnectionState.DISCONNECTED;
        chatSink.tryEmitComplete();
        log.info("WebSocket connection closed");
    }

    /**
     * 检查 WebSocket 连接是否处于活跃状态。
     *
     * @return 连接活跃时返回 {@code true}
     */
    public boolean isConnected() {
        return state == ConnectionState.CONNECTED && session != null;
    }

    /**
     * 订阅 WebSocket 事件。
     *
     * @param subscriber 事件订阅者
     * @return 用于取消订阅的 Disposable
     */
    public Disposable subscribe(EventSubscriber subscriber) {
        eventSubscribers.add(subscriber);
        return Mono.fromRunnable(() -> eventSubscribers.remove(subscriber)).subscribe();
    }

    /** WebSocket 事件订阅者接口。 */
    @FunctionalInterface
    public interface EventSubscriber {
        void onEvent(String eventType, JsonNode payload);
    }

    // ─── Frame Router ──────────────────────────────────────────────

    private void handleIncomingFrame(String text, MonoSink<Void> connectSink) {
        try {
            JsonNode frame = objectMapper.readTree(text);
            String type = frame.path("type").asText("");

            switch (type) {
                case "event" -> handleEvent(frame);
                case "res" -> handleResponse(frame, connectSink);
                case "req" -> {
                    // Server-side request (connect.challenge arrives as type:"req" from some gateways)
                    String method = frame.path("method").asText("");
                    if ("connect.challenge".equals(method)) {
                        handleChallenge(frame);
                    }
                }
                default -> log.warn("Unknown WebSocket frame type: {}", type);
            }
        } catch (Exception e) {
            log.error("Failed to parse WebSocket frame: {}", text, e);
        }
    }

    // ─── Handshake ─────────────────────────────────────────────────

    /**
     * 处理服务器发送的连接挑战，并发送连接请求。
     */
    private void handleChallenge(JsonNode frame) {
        state = ConnectionState.AUTHENTICATING;

        String nonce = null;
        if (frame.has("params") && frame.path("params").has("nonce")) {
            nonce = frame.path("params").path("nonce").asText();
        }

        // Build connect request params per gateway protocol spec
        ObjectNode connectParams = objectMapper.createObjectNode();
        connectParams.put("minProtocol", protocolVersion);
        connectParams.put("maxProtocol", protocolVersion);

        // Auth
        ObjectNode auth = connectParams.putObject("auth");
        auth.put("token", token);

        // Nonce (from challenge)
        if (nonce != null) {
            connectParams.put("nonce", nonce);
        }

        // Client info — id and mode must match gateway's allowed enum values
        ObjectNode clientInfo = connectParams.putObject("client");
        clientInfo.put("id", "cli");
        clientInfo.put("version", "1.0.0");
        clientInfo.put("platform", "java");
        clientInfo.put("mode", "cli");

        // Role and scopes
        connectParams.put("role", "operator");
        connectParams.putArray("scopes").add("operator.read").add("operator.write");

        // Empty capabilities
        connectParams.putArray("caps");
        connectParams.putArray("commands");
        connectParams.putObject("permissions");

        sendRequest("connect", connectParams);
    }

    /**
     * 处理 RPC 响应帧。握手期间处理 hello-ok，否则路由到对应的 pending 请求。
     */
    private void handleResponse(JsonNode frame, MonoSink<Void> connectSink) {
        String id = frame.path("id").asText("");

        // During handshake: handle hello-ok response
        if (state == ConnectionState.AUTHENTICATING || state == ConnectionState.CHALLENGING) {
            boolean ok = frame.path("ok").asBoolean(false);
            if (ok) {
                state = ConnectionState.CONNECTED;
                reconnectAttempt = 0;

                JsonNode payload = frame.path("payload");
                String serverVersion = payload.path("server").path("version").asText("unknown");
                if (payload.has("policy")) {
                    tickIntervalMs = payload.path("policy").path("tickIntervalMs").asLong(15_000);
                }

                JsonNode authNode = payload.path("auth");
                String grantedRole = authNode.path("role").asText("unknown");
                JsonNode grantedScopes = authNode.path("scopes");
                log.info("WebSocket connected: server={}, tickInterval={}ms, role={}, scopes={}",
                        serverVersion, tickIntervalMs, grantedRole, grantedScopes);
                startTickTimer();
                connectSink.success();
            } else {
                state = ConnectionState.DISCONNECTED;
                String errorMsg = frame.path("error").path("message")
                        .asText("Authentication failed");
                connectSink.error(new ClientException(ErrorCode.AUTHENTICATION_FAILED, errorMsg));
            }
            return;
        }

        // Regular RPC response — route to pending request
        Sinks.One<JsonNode> responseSink = pendingRequests.remove(id);
        if (responseSink != null) {
            boolean ok = frame.path("ok").asBoolean(false);
            if (ok) {
                responseSink.tryEmitValue(frame.path("payload"));
            } else {
                String errorMsg = frame.path("error").path("message").asText("RPC error");
                String errorCode = frame.path("error").path("code").asText("UNKNOWN");
                responseSink.tryEmitError(new ClientException(ErrorCode.WEBSOCKET_ERROR,
                        errorCode + ": " + errorMsg));
            }
        } else {
            log.debug("Received response for unknown request id: {}", id);
        }
    }

    // ─── Event Handling ────────────────────────────────────────────

    /**
     * 处理服务器推送的事件帧，路由到事件订阅者和聊天流。
     */
    private void handleEvent(JsonNode frame) {
        String eventType = frame.path("event").asText("");
        JsonNode payload = frame.path("payload");

        // Handle connect.challenge arriving as event type (documented gateway behavior)
        if ("connect.challenge".equals(eventType)) {
            handleChallenge(frame);
            return;
        }

        // Chat streaming events → feed to the chat streaming sink
        if ("chat".equals(eventType) || "agent".equals(eventType)) {
            String delta = payload.path("deltaText").asText(
                    payload.path("delta").asText(""));
            boolean done = payload.path("done").asBoolean(false);
            String type = payload.path("type").asText("text");

            if (!delta.isEmpty() || done) {
                chatSink.tryEmitNext(ChatStreamChunk.builder()
                        .delta(delta).type(type).done(done).build());
            }
            if (done) {
                chatSink.tryEmitComplete();
                chatSink = Sinks.many().multicast().onBackpressureBuffer();
            }
        }

        // Tick keepalive
        if ("tick".equals(eventType)) {
            log.trace("Tick received");
            return;
        }

        // Shutdown notification
        if ("shutdown".equals(eventType)) {
            log.warn("Gateway shutdown notification received");
            close();
            return;
        }

        // Notify all event subscribers
        for (EventSubscriber sub : eventSubscribers) {
            try {
                sub.onEvent(eventType, payload);
            } catch (Exception e) {
                log.error("Event subscriber error", e);
            }
        }
    }

    // ─── Low-Level Send ────────────────────────────────────────────

    private void sendRequest(String method, Object params) {
        String id = String.valueOf(requestIdCounter.incrementAndGet());
        ObjectNode frame = objectMapper.createObjectNode();
        frame.put("type", "req");
        frame.put("id", id);
        frame.put("method", method);
        frame.set("params", objectMapper.valueToTree(params));
        sendFrame(frame);
    }

    private void sendFrame(JsonNode frame) {
        if (session == null || state == ConnectionState.CLOSING
                || state == ConnectionState.DISCONNECTED) {
            log.warn("Cannot send frame: not connected (state={})", state);
            return;
        }
        try {
            String text = objectMapper.writeValueAsString(frame);
            outbound.tryEmitNext(text);
        } catch (Exception e) {
            log.error("Failed to serialize WebSocket frame", e);
        }
    }

    // ─── Tick Timer ────────────────────────────────────────────────

    private void startTickTimer() {
        stopTickTimer();
        long intervalMs = Math.max(tickIntervalMs / 2, 1000);
        tickDisposable = Flux.interval(Duration.ofMillis(intervalMs))
                .subscribe(tick -> {
                    if (state == ConnectionState.CONNECTED) {
                        ObjectNode tickFrame = objectMapper.createObjectNode();
                        tickFrame.put("type", "req");
                        tickFrame.put("method", "tick");
                        sendFrame(tickFrame);
                    }
                });
    }

    private void stopTickTimer() {
        if (tickDisposable != null && !tickDisposable.isDisposed()) {
            tickDisposable.dispose();
            tickDisposable = null;
        }
    }

    // ─── Reconnection ──────────────────────────────────────────────

    /**
     * 使用指数退避策略进行重连。
     */
    public Mono<Void> reconnect() {
        if (state == ConnectionState.CLOSING) {
            return Mono.empty();
        }
        return Mono.defer(() -> {
            int attempt = reconnectAttempt++;
            if (attempt >= maxReconnectAttempts) {
                return Mono.error(new ClientException(ErrorCode.CONNECTION_REFUSED,
                        "Max reconnection attempts reached (" + maxReconnectAttempts + ")"));
            }
            int delaySec = Math.min(MIN_RECONNECT_DELAY_SEC * (1 << attempt), MAX_RECONNECT_DELAY_SEC);
            log.info("Reconnecting in {}s (attempt {}/{})", delaySec, attempt + 1, maxReconnectAttempts);
            return Mono.delay(Duration.ofSeconds(delaySec))
                    .then(connect())
                    .onErrorResume(e -> reconnect());
        });
    }

    // ─── Connection Error/Closed Handlers ───────────────────────────

    private void handleConnectionError(Throwable error) {
        log.error("WebSocket connection error", error);
        boolean wasConnected = (state == ConnectionState.CONNECTED);
        state = ConnectionState.DISCONNECTED;
        stopTickTimer();

        pendingRequests.forEach((id, responseSink) ->
                responseSink.tryEmitError(new ClientException(ErrorCode.WEBSOCKET_ERROR,
                        "Connection error: " + error.getMessage())));
        pendingRequests.clear();

        if (wasConnected) {
            reconnect().subscribe(
                    v -> {},
                    e -> log.error("Reconnection failed after all attempts", e)
            );
        }
    }

    private void handleConnectionClosed() {
        if (state == ConnectionState.CLOSING) {
            return; // Expected close, no action needed
        }
        log.warn("WebSocket connection closed by server");
        boolean wasConnected = (state == ConnectionState.CONNECTED);
        state = ConnectionState.DISCONNECTED;
        stopTickTimer();

        pendingRequests.forEach((id, responseSink) ->
                responseSink.tryEmitError(new ClientException(ErrorCode.WEBSOCKET_ERROR,
                        "Connection closed by server")));
        pendingRequests.clear();

        if (wasConnected) {
            reconnect().subscribe(
                    v -> {},
                    e -> log.error("Reconnection failed after all attempts", e)
            );
        }
    }

    // ─── Backward-Compatible Chat Streaming ────────────────────────

    /**
     * 建立 WebSocket 连接并以流式方式接收聊天响应块。
     *
     * @param sessionId 流式会话的标识符
     * @param request   通过 WebSocket 发送的聊天请求
     * @return 表示流式响应的 {@link ChatStreamChunk} {@link Flux}
     * @deprecated 使用 {@link #connect()} + {@link #invoke(String, Object)} 替代
     */
    @Deprecated
    public Flux<ChatStreamChunk> connect(String sessionId, ChatRequest request) {
        chatSink = Sinks.many().multicast().onBackpressureBuffer();

        Mono<Void> ensureConnected = isConnected() ? Mono.empty() : connect();

        return ensureConnected.thenMany(Flux.create(sink -> {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("message", request.getMessage());
            if (sessionId != null) {
                params.put("sessionKey", sessionId);
            }
            if (request.getConversationId() != null) {
                params.put("conversationId", request.getConversationId());
            }

            invoke("chat.send", params).subscribe(
                    response -> chatSink.asFlux().subscribe(sink::next, sink::error, sink::complete),
                    sink::error
            );
        }));
    }

    // ─── Inner DTO ─────────────────────────────────────────────────

    /** WebSocket 流式传输中接收的单个数据块。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatStreamChunk {
        private String delta;
        private String type;
        private boolean done;
    }
}
