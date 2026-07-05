# 第5章：WebSocket 通信层实现

> **学习目标**：理解如何用 Reactor Netty 实现 WebSocket 客户端，包括状态机、握手、RPC 调用、心跳和重连。

---

## 5.1 技术选型

| 方案 | 优点 | 缺点 |
|------|------|------|
| Spring WebSocket | Spring 原生 | 底层控制有限 |
| **Reactor Netty** | 响应式原生、细粒度控制 | 学习曲线 |
| Java-WebSocket | 简单 | 不支持响应式 |

我们选择 **Reactor Netty**，因为它与 Project Reactor 天然集成。

## 5.2 核心数据结构

```java
public class OpenClawWebSocketClient {
    // 连接状态
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private volatile WebSocketSession session;

    // 出站消息管道（线程安全的异步队列）
    private final Sinks.Many<String> outbound =
        Sinks.many().multicast().onBackpressureBuffer();

    // 待处理的 RPC 请求（通过 id 关联响应）
    private final ConcurrentHashMap<String, Sinks.One<JsonNode>> pendingRequests =
        new ConcurrentHashMap<>();

    // 事件订阅者列表
    private final CopyOnWriteArrayList<EventSubscriber> eventSubscribers =
        new CopyOnWriteArrayList<>();

    // 请求 ID 自增计数器
    private final AtomicLong requestIdCounter = new AtomicLong(0);
}
```

### 关键 Reactor 概念

| 类型 | 说明 | 用途 |
|------|------|------|
| `Mono<T>` | 0或1个结果的异步操作 | RPC 响应 |
| `Flux<T>` | 0到N个结果的异步流 | 事件流 |
| `Sinks.One<T>` | 手动完成的单值容器 | 等待 RPC 响应 |
| `Sinks.Many<T>` | 手动推送的多值容器 | 出站消息队列 |

## 5.3 连接状态机

```
                    connect()
DISCONNECTED ─────────────────► CONNECTING
     ▲                              │
     │                         TCP 连接建立
     │                              │
     │                              ▼
     │                         CHALLENGING
     │                              │
     │                        收到 challenge
     │                              │
     │                              ▼
     │                        AUTHENTICATING
     │                              │
     │  ┌───────────────────────────┤
     │  │ hello-ok (ok=false)       │ hello-ok (ok=true)
     │  │                           ▼
     │  │                       CONNECTED ◄── tick 心跳循环
     │  │                           │
     │  │                      连接断开/错误
     │  │                           │
     │  │                           ▼
     │  │                       CLOSING
     │  │                           │
     └──┴───────────────────────────┘
```

## 5.4 连接实现

```java
public Mono<Void> connect() {
    return Mono.<Void>create(sink -> {
        state = ConnectionState.CONNECTING;

        // 创建 Reactor Netty WebSocket 客户端
        WebSocketClient client = new ReactorNettyWebSocketClient();
        URI uri = URI.create(endpoint);

        client.execute(uri, wsSession -> {
            this.session = wsSession;
            state = ConnectionState.CHALLENGING;

            // 入站：接收帧 → 路由处理
            Mono<Void> incoming = wsSession.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(text -> handleIncomingFrame(text, sink))
                .then();

            // 出站：从 outbound 管道发送帧
            Mono<Void> sending = wsSession.send(
                outbound.asFlux().map(wsSession::textMessage));

            // 两个管道并行运行
            return Mono.when(incoming, sending);
        }).subscribe(
            v -> { /* 连接正常关闭 */ },
            error -> sink.error(new ClientException(
                ErrorCode.CONNECTION_REFUSED, "连接失败", error))
        );
    }).timeout(connectTimeout);
}
```

### 帧路由器

```java
private void handleIncomingFrame(String text, MonoSink<Void> connectSink) {
    JsonNode frame = objectMapper.readTree(text);
    String type = frame.path("type").asText("");

    switch (type) {
        case "event" -> handleEvent(frame);      // 事件推送
        case "res"   -> handleResponse(frame,     // RPC 响应
                               connectSink);
        case "req"   -> handleChallenge(frame);   // 握手挑战
    }
}
```

## 5.5 握手实现

### 处理 Challenge

```java
private void handleChallenge(JsonNode frame) {
    state = ConnectionState.AUTHENTICATING;

    String nonce = frame.path("payload").path("nonce").asText(null);

    // 构建 connect 请求
    ObjectNode params = objectMapper.createObjectNode();
    params.put("minProtocol", protocolVersion);
    params.put("maxProtocol", protocolVersion);

    // 认证信息
    params.putObject("auth").put("token", token);

    // 客户端信息（必须使用合法枚举值！）
    ObjectNode clientInfo = params.putObject("client");
    clientInfo.put("id", "cli");        // ← 枚举值
    clientInfo.put("version", "1.0.0");
    clientInfo.put("platform", "java");
    clientInfo.put("mode", "cli");      // ← 枚举值

    params.put("role", "operator");
    params.putArray("scopes")
        .add("operator.read").add("operator.write");

    sendRequest("connect", params);
}
```

### 处理 Hello-OK

```java
private void handleResponse(JsonNode frame, MonoSink<Void> connectSink) {
    if (state == ConnectionState.AUTHENTICATING) {
        boolean ok = frame.path("ok").asBoolean(false);

        if (ok) {
            state = ConnectionState.CONNECTED;
            JsonNode payload = frame.path("payload");

            // 读取服务端配置
            tickIntervalMs = payload.path("policy")
                .path("tickIntervalMs").asLong(15_000);

            // 记录授予的权限
            log.info("Connected: role={}, scopes={}",
                payload.path("auth").path("role").asText(),
                payload.path("auth").path("scopes"));

            startTickTimer();       // 启动心跳
            connectSink.success();  // 完成 connect() Mono
        } else {
            String error = frame.path("error")
                .path("message").asText("认证失败");
            connectSink.error(new ClientException(
                ErrorCode.AUTHENTICATION_FAILED, error));
        }
    }
}
```

## 5.6 RPC 调用实现

```java
public Mono<JsonNode> invoke(String method, Object params) {
    return Mono.create(sink -> {
        // 生成唯一请求 ID
        String id = String.valueOf(requestIdCounter.incrementAndGet());

        // 构建请求帧
        ObjectNode frame = objectMapper.createObjectNode();
        frame.put("type", "req");
        frame.put("id", id);
        frame.put("method", method);
        frame.set("params", objectMapper.valueToTree(params));

        // 创建响应等待槽
        Sinks.One<JsonNode> responseSink = Sinks.one();
        pendingRequests.put(id, responseSink);

        // 设置超时
        responseSink.asMono()
            .timeout(requestTimeout)
            .subscribe(
                sink::success,
                error -> {
                    pendingRequests.remove(id);
                    sink.error(error);
                }
            );

        // 发送帧
        sendFrame(frame);
    });
}
```

### 响应路由

```java
// 在 handleResponse 中，非握手阶段的响应：
Sinks.One<JsonNode> responseSink = pendingRequests.remove(id);
if (responseSink != null) {
    if (frame.path("ok").asBoolean(false)) {
        responseSink.tryEmitValue(frame.path("payload"));
    } else {
        String errorMsg = frame.path("error")
            .path("message").asText("RPC error");
        responseSink.tryEmitError(
            new ClientException(ErrorCode.WEBSOCKET_ERROR, errorMsg));
    }
}
```

## 5.7 心跳保活

```java
private void startTickTimer() {
    // 在 tickIntervalMs 的一半间隔发送心跳
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
```

## 5.8 指数退避重连

```java
public Mono<Void> reconnect() {
    return Mono.defer(() -> {
        int attempt = reconnectAttempt++;

        if (attempt >= maxReconnectAttempts) {
            return Mono.error(new ClientException(
                ErrorCode.CONNECTION_REFUSED,
                "达到最大重连次数: " + maxReconnectAttempts));
        }

        // 指数退避：1s → 2s → 4s → 8s → ... → 30s (上限)
        int delaySec = Math.min(1 * (1 << attempt), 30);

        log.info("将在 {}s 后重连 (尝试 {}/{})",
            delaySec, attempt + 1, maxReconnectAttempts);

        return Mono.delay(Duration.ofSeconds(delaySec))
            .then(connect())
            .onErrorResume(e -> reconnect());  // 递归重连
    });
}
```

## 5.9 事件订阅

```java
@FunctionalInterface
public interface EventSubscriber {
    void onEvent(String eventType, JsonNode payload);
}

public Disposable subscribe(EventSubscriber subscriber) {
    eventSubscribers.add(subscriber);
    return Mono.fromRunnable(() ->
        eventSubscribers.remove(subscriber)
    ).subscribe();
}

// 在 handleEvent 中广播：
for (EventSubscriber sub : eventSubscribers) {
    try {
        sub.onEvent(eventType, payload);
    } catch (Exception e) {
        log.error("事件订阅者异常", e);
    }
}
```

## 5.10 发送帧

```java
private void sendFrame(JsonNode frame) {
    if (session == null || state == ConnectionState.CLOSING) {
        return;  // 静默丢弃
    }
    String text = objectMapper.writeValueAsString(frame);
    outbound.tryEmitNext(text);  // 推入出站管道
}
```

出站管道由 `wsSession.send()` 消费，自动发送到 WebSocket。

## 5.11 小结

| 知识点 | 实现要点 |
|--------|---------|
| 状态机 | 6 个状态，严格控制转换 |
| 握手 | challenge → connect → hello-ok |
| RPC 关联 | `ConcurrentHashMap<id, Sinks.One>` |
| 心跳 | `Flux.interval` 在半间隔发送 |
| 重连 | 指数退避，上限 30s，最多 10 次 |
| 事件 | `CopyOnWriteArrayList` 线程安全广播 |

**下一章**：[第6章：HTTP 通信层实现](06-HTTP-通信层实现.md) — 实现 OpenAI 兼容的聊天和文件上传客户端。
