# OpenClaw Spring Boot Starter 实现原理与技术实践

**版本**：V1.0  
**作者**：OpenClaw SDK Team  
**适用读者**：SDK 开发者、OpenClaw 集成工程师、Spring Boot 架构师

---

## 目录

- [1. 引言](#1-引言)
- [2. OpenClaw Gateway 协议概览](#2-openclaw-gateway-协议概览)
- [3. 双传输架构：HTTP + WebSocket](#3-双传输架构http--websocket)
- [4. WebSocket 协议实现](#4-websocket-协议实现)
- [5. HTTP 聊天端点实现](#5-http-聊天端点实现)
- [6. RPC 客户端实现](#6-rpc-客户端实现)
- [7. Spring Boot 自动配置集成](#7-spring-boot-自动配置集成)
- [8. 事件订阅与流式传输](#8-事件订阅与流式传输)
- [9. 权限与作用域设计](#9-权限与作用域设计)
- [10. 关键技术决策回顾](#10-关键技术决策回顾)
- [11. 总结](#11-总结)

---

## 1. 引言

OpenClaw 是一个 AI 驱动的对话与任务执行平台，提供聊天、技能编排、任务管理、制品存储等能力。Spring Boot Starter SDK 的目标是让 Spring Boot 应用以最小成本接入 OpenClaw 生态。

在实现过程中，我们发现 OpenClaw Gateway 的协议设计有其独特的架构特点：

- **WebSocket-first**：核心控制面基于 WebSocket RPC 协议
- **HTTP 兼容层**：提供 OpenAI 兼容的 HTTP 端点（默认关闭）
- **权限分离**：WebSocket 和 HTTP 的作用域（scope）授予机制不同

这些特点直接影响了 SDK 的传输层架构设计，最终形成了 **HTTP + WebSocket 双传输** 模式。

---

## 2. OpenClaw Gateway 协议概览

OpenClaw Gateway 是一个单进程服务，在同一端口上多路复用 WebSocket 和 HTTP：

```
┌─────────────────────────────────────────────────┐
│              OpenClaw Gateway                    │
│                                                  │
│  Port 13015 ─┬─ WebSocket (控制面 RPC)           │
│              ├─ HTTP /v1/* (OpenAI 兼容端点)      │
│              ├─ HTTP /health (健康检查)           │
│              └─ HTTP /openclaw/callback (回调)    │
└─────────────────────────────────────────────────┘
```

### 2.1 WebSocket 协议帧格式

WebSocket 通信基于 JSON 文本帧，共三种帧类型：

| 帧类型 | 方向 | 用途 |
|--------|------|------|
| `{type:"req"}` | Client → Server | RPC 请求 |
| `{type:"res"}` | Server → Client | RPC 响应 |
| `{type:"event"}` | Server → Client | 服务端推送事件 |

**请求帧示例：**
```json
{
  "type": "req",
  "id": "42",
  "method": "sessions.get",
  "params": { "key": "agent:main:main" }
}
```

**响应帧示例：**
```json
{
  "type": "res",
  "id": "42",
  "ok": true,
  "payload": { "sessionKey": "agent:main:main", "status": "active" }
}
```

**事件帧示例：**
```json
{
  "type": "event",
  "event": "chat",
  "payload": { "deltaText": "Hello ", "type": "text" }
}
```

### 2.2 核心 RPC 方法

| 领域 | RPC 方法 | 说明 |
|------|---------|------|
| 会话 | `sessions.create` | 创建新会话 |
| 会话 | `sessions.get` | 获取会话状态 |
| 会话 | `sessions.delete` | 关闭会话 |
| 任务 | `tasks.get` | 获取任务详情 |
| 任务 | `tasks.list` | 列出会话任务 |
| 任务 | `tasks.cancel` | 取消任务 |
| 聊天 | `chat.send` | 发送聊天消息（需 `operator.write` scope） |
| 聊天 | `chat.history` | 获取聊天历史 |
| 制品 | `artifacts.get` | 获取制品信息 |
| 制品 | `artifacts.list` | 列出会话制品 |
| 制品 | `artifacts.download` | 下载制品内容 |
| 事件 | `system-event` | 发布系统事件 |
| 心跳 | `tick` | 保活心跳 |

---

## 3. 双传输架构：HTTP + WebSocket

### 3.1 核心发现

在实现过程中，我们发现了一个关键的权限设计差异：

```
┌──────────────────────────────────────────────────────────────┐
│                    权限授予机制对比                             │
├──────────────┬───────────────────────────────────────────────┤
│ 传输方式      │ Scope 授予规则                                 │
├──────────────┼───────────────────────────────────────────────┤
│ WebSocket    │ 需要设备配对 (device pairing) 才能获得 scope     │
│              │ 无配对时 scopes=[] ，无法执行写操作              │
├──────────────┼───────────────────────────────────────────────┤
│ HTTP 端点     │ Shared-secret auth 自动获得完整 operator scope │
│              │ 包括 operator.write、operator.admin 等         │
└──────────────┴───────────────────────────────────────────────┘
```

这意味着：

- **WebSocket RPC `chat.send`**：需要 `operator.write` scope，但无设备配对时 scope 为空 → **不可用**
- **HTTP `POST /v1/chat/completions`**：Shared-secret Bearer auth 自动恢复完整 scope → **可用**

### 3.2 最终传输分工

```
┌─────────────────────────────────────────────────────────┐
│                   SDK 传输层架构                          │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  HTTP 传输（WebClient）                           │   │
│  │  ├─ ChatClient → POST /v1/chat/completions       │   │
│  │  └─ UploadClient → POST /v1/files (multipart)    │   │
│  │  用途：需要 operator.write 的写操作               │   │
│  │  权限：Shared-secret auth → 完整 scope            │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  WebSocket 传输（ReactorNettyWebSocketClient）    │   │
│  │  ├─ SessionHttpClient → sessions.* RPC           │   │
│  │  ├─ TaskClient → tasks.* RPC                     │   │
│  │  ├─ EventClient → system-event / chat.history    │   │
│  │  ├─ ArtifactClient → artifacts.* RPC             │   │
│  │  └─ EventSubscriber → 实时事件流                  │   │
│  │  用途：读操作、事件订阅、实时流                    │   │
│  │  权限：基础连接，scope 受限于设备配对状态          │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  OpenClawClient（门面）                           │   │
│  │  聚合所有领域客户端，统一对外暴露                  │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## 4. WebSocket 协议实现

### 4.1 握手协议

WebSocket 连接遵循三步握手：

```
Client                                    Gateway
  │                                          │
  │  ── WebSocket 连接建立 ──►               │
  │                                          │
  │  ◄── connect.challenge 事件              │
  │      {type:"event",                      │
  │       event:"connect.challenge",         │
  │       payload:{nonce, ts}}               │
  │                                          │
  │  ── connect 请求 ──►                     │
  │      {type:"req",                        │
  │       method:"connect",                  │
  │       params:{                           │
  │         minProtocol: 4,                  │
  │         maxProtocol: 4,                  │
  │         client: {id, version, platform,  │
  │                  mode},                  │
  │         role, scopes, auth:{token},      │
  │         caps, commands, permissions      │
  │       }}                                 │
  │                                          │
  │  ◄── hello-ok 响应                       │
  │      {type:"res", ok:true,               │
  │       payload:{                          │
  │         server: {version, connId},       │
  │         auth: {role, scopes},            │
  │         policy: {tickIntervalMs,         │
  │                  maxPayload}             │
  │       }}                                 │
  │                                          │
  │  ═══ 连接已建立，开始 RPC 通信 ═══       │
```

### 4.2 连接状态机

```
DISCONNECTED ──connect()──► CONNECTING
     ▲                          │
     │                     收到 challenge
     │                          │
     │                          ▼
     │                     AUTHENTICATING
     │                          │
     │                   hello-ok (ok=true)
     │                          │
     │                          ▼
     │                      CONNECTED ◄── tick 心跳保活
     │                          │
     │                     连接断开/error
     │                          │
     │                          ▼
     │                       CLOSING
     │                          │
     └──────────────────────────┘
```

### 4.3 connect 请求参数

`client` 字段的 `id` 和 `mode` 受 Gateway 严格枚举校验：

```java
ObjectNode clientInfo = connectParams.putObject("client");
clientInfo.put("id", "cli");        // 枚举值: cli, ios-node, gateway-client, openclaw-macos
clientInfo.put("version", "1.0.0");
clientInfo.put("platform", "java");
clientInfo.put("mode", "cli");      // 枚举值: ui, webchat, cli, backend, probe, test, node
```

### 4.4 RPC 调用实现

RPC 调用通过请求 ID 进行关联：

```java
public Mono<JsonNode> invoke(String method, Object params) {
    return Mono.create(sink -> {
        String id = String.valueOf(requestIdCounter.incrementAndGet());

        // 构建请求帧
        ObjectNode frame = objectMapper.createObjectNode();
        frame.put("type", "req");
        frame.put("id", id);
        frame.put("method", method);
        frame.set("params", objectMapper.valueToTree(params));

        // 注册 pending 请求
        Sinks.One<JsonNode> responseSink = Sinks.one();
        pendingRequests.put(id, responseSink);

        // 响应到达时完成 Mono
        responseSink.asMono()
            .timeout(requestTimeout)
            .subscribe(sink::success, error -> {
                pendingRequests.remove(id);
                sink.error(error);
            });

        sendFrame(frame);
    });
}
```

响应帧通过 `id` 字段路由到对应的 pending 请求：

```java
private void handleResponse(JsonNode frame) {
    String id = frame.path("id").asText("");
    Sinks.One<JsonNode> responseSink = pendingRequests.remove(id);
    if (responseSink != null) {
        if (frame.path("ok").asBoolean(false)) {
            responseSink.tryEmitValue(frame.path("payload"));
        } else {
            String errorMsg = frame.path("error").path("message").asText("RPC error");
            responseSink.tryEmitError(new ClientException(
                ErrorCode.WEBSOCKET_ERROR, errorMsg));
        }
    }
}
```

### 4.5 心跳保活

Gateway 在 `hello-ok` 中声明 `tickIntervalMs`（默认 15 秒），SDK 在**一半间隔**时发送心跳：

```java
private void startTickTimer() {
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

### 4.6 重连策略

采用指数退避策略（1s → 2s → 4s → ... → 30s 上限），最大重连 10 次：

```java
public Mono<Void> reconnect() {
    return Mono.defer(() -> {
        int attempt = reconnectAttempt++;
        if (attempt >= maxReconnectAttempts) {
            return Mono.error(new ClientException(
                ErrorCode.CONNECTION_REFUSED, "Max reconnection attempts reached"));
        }
        int delaySec = Math.min(1 * (1 << attempt), 30);
        return Mono.delay(Duration.ofSeconds(delaySec))
            .then(connect())
            .onErrorResume(e -> reconnect());
    });
}
```

---

## 5. HTTP 聊天端点实现

### 5.1 端点概述

`/v1/chat/completions` 是 Gateway 提供的 OpenAI 兼容 HTTP 端点：

- **默认关闭**，需在 Gateway 配置中启用：`gateway.http.endpoints.chatCompletions.enabled: true`
- **完整权限**：Shared-secret Bearer auth 自动恢复 `operator.admin`、`operator.write`、`operator.read` 等全部 scope
- **OpenAI 兼容**：请求/响应格式遵循 OpenAI Chat Completions 规范

### 5.2 请求格式

```json
{
  "model": "openclaw/default",
  "stream": false,
  "messages": [
    { "role": "user", "content": "你好" }
  ],
  "user": "session-key-for-routing"
}
```

关键字段说明：

| 字段 | 用途 |
|------|------|
| `model` | Agent 路由目标，`"openclaw/default"` 指向默认 agent |
| `stream` | `true` 时返回 SSE 流 |
| `user` | OpenAI 标准字段，Gateway 用于 session 路由 |
| `x-openclaw-session-key` | 请求头，显式控制 session 路由 |

### 5.3 同步调用实现

```java
public ChatResponse sendMessage(ChatRequest request) {
    Map<String, Object> body = buildRequestBody(request, false);

    JsonNode response = webClient.post()
        .uri("/v1/chat/completions")
        .contentType(MediaType.APPLICATION_JSON)
        .headers(h -> addSessionHeaders(h, request))
        .bodyValue(body)
        .retrieve()
        .onStatus(status -> status.isError(),
            clientResponse -> clientResponse.bodyToMono(String.class)
                .map(errorBody -> new ClientException(ErrorCode.HTTP_ERROR,
                    "Chat error: " + errorBody
                    + " (hint: ensure chatCompletions.enabled=true)")))
        .bodyToMono(JsonNode.class)
        .block();

    return parseChatResponse(response, request);
}
```

### 5.4 流式调用实现

流式传输使用 Server-Sent Events (SSE)：

```java
public Flux<String> streamMessage(ChatRequest request) {
    Map<String, Object> body = buildRequestBody(request, true);

    return webClient.post()
        .uri("/v1/chat/completions")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .headers(h -> addSessionHeaders(h, request))
        .bodyValue(body)
        .retrieve()
        .bodyToFlux(String.class)
        .filter(line -> !line.isBlank() && !"[DONE]".equals(line.trim()))
        .mapNotNull(this::extractDeltaContent)
        .filter(delta -> !delta.isEmpty());
}
```

SSE 数据格式：

```
data: {"choices":[{"delta":{"content":"Hello "}}]}

data: {"choices":[{"delta":{"content":"World"}}]}

data: [DONE]
```

### 5.5 响应解析

OpenAI 格式到 SDK `ChatResponse` 的映射：

```
OpenAI Response                          SDK ChatResponse
─────────────────                        ────────────────
id                          →            requestId
choices[0].message.content  →            content
choices[0].finish_reason    →            metadata.finish_reason
model                       →            metadata.model
choices[0].message.tool_calls →          toolCalls[] (ToolCall DTO)
```

---

## 6. RPC 客户端实现

### 6.1 通用模式

所有 RPC 客户端（Session、Task、Event、Artifact）遵循统一模式：

```java
public class SessionHttpClient {
    private final WebClient webClient;                    // HTTP（备用）
    private final OpenClawWebSocketClient wsClient;       // WebSocket RPC
    private final ObjectMapper objectMapper;

    public JsonNode createSession(String workspaceId) {
        requireWsClient();
        Map<String, Object> params = Map.of("workspaceId", workspaceId);
        return wsClient.invoke("sessions.create", params)
            .block(Duration.ofSeconds(30));
    }
}
```

### 6.2 响应解析

RPC 响应的 payload 结构因方法而异：

```java
// tasks.list → payload.tasks[]
public List<JsonNode> listTasks(String sessionId) {
    JsonNode result = wsClient.invoke("tasks.list", params).block();
    JsonNode tasks = result.path("tasks");
    if (tasks.isArray()) {
        List<JsonNode> list = new ArrayList<>();
        tasks.forEach(list::add);
        return list;
    }
    return Collections.emptyList();
}

// artifacts.download → payload.content (base64)
public byte[] downloadArtifact(String artifactId) {
    JsonNode result = wsClient.invoke("artifacts.download", params).block();
    String base64 = result.path("content").asText("");
    return Base64.getDecoder().decode(base64);
}
```

---

## 7. Spring Boot 自动配置集成

### 7.1 激活条件

整个 SDK 通过 `openclaw.endpoint` 属性激活：

```java
@AutoConfiguration
@EnableConfigurationProperties(OpenClawProperties.class)
@ConditionalOnProperty(prefix = "openclaw", name = "endpoint")
public class OpenClawAutoConfiguration { ... }
```

### 7.2 WebSocket 端点自动推导

WebSocket 端点从 HTTP 端点自动推导（`http` → `ws`，`https` → `wss`）：

```java
@Bean
public OpenClawWebSocketClient openClawWebSocketClient(
        OpenClawProperties properties, ObjectMapper objectMapper) {
    String wsEndpoint = properties.getWebsocket().getEndpoint();
    if (wsEndpoint == null || wsEndpoint.isBlank()) {
        wsEndpoint = properties.getEndpoint()
            .replaceFirst("^https://", "wss://")
            .replaceFirst("^http://", "ws://");
    }
    return new OpenClawWebSocketClient(wsEndpoint, token, objectMapper, ...);
}
```

### 7.3 自动连接

通过 `SmartInitializingSingleton` 在所有 Bean 初始化后自动建立 WebSocket 连接：

```java
@Bean
public SmartInitializingSingleton openClawWebSocketInitializer(
        OpenClawWebSocketClient webSocketClient,
        OpenClawProperties properties) {
    return () -> {
        if (properties.getWebsocket().isAutoConnect()) {
            webSocketClient.connect()
                .doOnSuccess(v -> log.info("WebSocket connected to gateway"))
                .doOnError(e -> log.warn("WebSocket auto-connect failed: {}", e.getMessage()))
                .subscribe();
        }
    };
}
```

### 7.4 Bean 依赖图

```
OpenClawProperties
    │
    ├──► WebClient (HTTP 传输)
    │       │
    │       ├──► ChatClient (HTTP /v1/chat/completions)
    │       └──► UploadClient (HTTP /v1/files)
    │
    ├──► OpenClawWebSocketClient (WS 传输)
    │       │
    │       ├──► SessionHttpClient (WS RPC)
    │       ├──► TaskClient (WS RPC)
    │       ├──► EventClient (WS RPC)
    │       └──► ArtifactClient (WS RPC)
    │
    └──► OpenClawClient (门面，聚合所有客户端)
            │
            └──► DefaultOpenClawRuntime (公开 API)
```

### 7.5 配置属性

```yaml
openclaw:
  endpoint: http://host:port/path       # HTTP 端点（必填，激活 SDK）
  token: your-auth-token                # 认证令牌（必填）
  workspace: main                       # 默认工作区
  auto-register-skill: false            # 是否自动注册 Skill
  websocket:
    endpoint: null                      # WS 端点（默认从 endpoint 推导）
    protocol-version: 4                 # 协议版本
    connect-timeout: 15s                # 握手超时
    request-timeout: 30s                # RPC 超时
    auto-connect: true                  # 启动时自动连接
    max-reconnect-attempts: 10          # 最大重连次数
```

---

## 8. 事件订阅与流式传输

### 8.1 事件订阅模型

WebSocket 客户端提供事件订阅机制，支持多种事件类型：

```java
@FunctionalInterface
public interface EventSubscriber {
    void onEvent(String eventType, JsonNode payload);
}

// 订阅事件
Disposable subscription = wsClient.subscribe((eventType, payload) -> {
    if ("chat".equals(eventType)) {
        String delta = payload.path("deltaText").asText("");
        // 处理文本增量
    } else if ("agent".equals(eventType)) {
        String status = payload.path("status").asText("");
        // 处理 agent 状态变化
    }
});

// 取消订阅
subscription.dispose();
```

### 8.2 事件类型

| 事件类型 | 说明 | 关键 payload 字段 |
|---------|------|-------------------|
| `chat` | 聊天文本增量 | `deltaText`、`type`、`done` |
| `agent` | Agent 状态变化 | `status`（ok/error）、`runId` |
| `connect.challenge` | 握手挑战 | `nonce`、`ts` |
| `tick` | 服务端心跳 | — |
| `shutdown` | Gateway 关闭通知 | — |
| `system-presence` | 在线状态更新 | `instanceId`、`mode` |

### 8.3 线程安全

事件订阅列表使用 `CopyOnWriteArrayList` 保证线程安全，支持并发订阅/取消：

```java
private final CopyOnWriteArrayList<EventSubscriber> eventSubscribers
    = new CopyOnWriteArrayList<>();
```

---

## 9. 权限与作用域设计

### 9.1 Scope 层级

OpenClaw 定义了多层 operator scope：

| Scope | 说明 |
|-------|------|
| `operator.read` | 只读：状态查询、列表、日志 |
| `operator.write` | 读写：发送消息、调用工具、更新设置 |
| `operator.admin` | 管理员：配置变更、更新、高权限审批 |
| `operator.pairing` | 设备/节点配对管理 |
| `operator.approvals` | 执行与插件审批 |

### 9.2 WebSocket Scope 授予

WebSocket 连接的 scope 通过以下机制协商：

1. **connect 请求中声明** `role` 和 `scopes`
2. **Gateway 根据以下因素决定实际授予的 scope**：
   - 设备配对记录（`devices/paired.json`）
   - 认证方式（token / device-token）
   - `client.mode` 枚举值

**无设备配对时**，Gateway 返回 `scopes: []`，所有需要 scope 的 RPC（如 `chat.send`）将被拒绝：

```
INVALID_REQUEST: missing scope: operator.write
```

### 9.3 HTTP Scope 授予

HTTP 端点使用 Shared-secret Bearer auth 时，**自动恢复完整 operator scope**：

```
operator.admin, operator.approvals, operator.pairing,
operator.read, operator.talk.secrets, operator.write
```

这是 SDK 选择 HTTP 作为聊天传输方式的根本原因。

### 9.4 设备配对

完整的设备配对流程（当前 SDK 未实现）：

```
Client                              Gateway
  │                                    │
  │  ── connect (with device identity) ──►
  │                                    │
  │  ◄── pending pairing request       │
  │      (需管理员审批)                 │
  │                                    │
  │  ── 管理员 approve ──►             │
  │                                    │
  │  ◄── device token                  │
  │                                    │
  │  ── reconnect (with device token) ──►
  │                                    │
  │  ◄── hello-ok (with full scopes)   │
```

---

## 10. 关键技术决策回顾

### 10.1 WebSocket vs HTTP：为什么不能只用 WebSocket？

**决策**：聊天操作使用 HTTP，其他操作使用 WebSocket。

**原因**：

| 方案 | 优点 | 缺点 |
|------|------|------|
| 纯 WebSocket | 统一传输层 | 无设备配对时无法获得写 scope |
| 纯 HTTP | 完整 scope | 无实时事件推送、RPC 端点有限 |
| **HTTP + WebSocket** | 各取所长 | 双传输层复杂度 |

### 10.2 Reactor Netty vs Spring WebSocket

**决策**：使用 `ReactorNettyWebSocketClient` 而非 Spring WebSocket。

**原因**：
- WebFlux 生态原生支持
- `Mono`/`Flux` 响应式模型与 RPC 的 `Sinks.One` 天然匹配
- Reactor Netty 提供更细粒度的连接控制

### 10.3 `chat.send` 的 WebSocket RPC 特性

**发现**：`chat.send` 是**非阻塞** RPC：

```
Client → Gateway:  chat.send {message, sessionKey}
Gateway → Client:  {runId, status: "started"}           ← 立即 ack
Gateway → Client:  event:chat {deltaText: "Hello "}     ← 流式推送
Gateway → Client:  event:chat {deltaText: "World"}
Gateway → Client:  event:agent {status: "ok"}           ← 完成信号
```

这意味着通过 WebSocket 调用 `chat.send` 时，`invoke()` 返回的只是 ack，实际响应需要通过事件订阅获取。HTTP 端点则直接返回完整响应或 SSE 流。

### 10.4 `client.id` 和 `client.mode` 的枚举约束

**发现**：Gateway 对 `client.id` 和 `client.mode` 使用严格的 JSON Schema 枚举校验。

| 字段 | 合法值 |
|------|--------|
| `client.id` | `cli`, `ios-node`, `gateway-client`, `openclaw-macos` |
| `client.mode` | `ui`, `webchat`, `cli`, `backend`, `probe`, `test`, `node` |

SDK 选择 `id: "cli"` + `mode: "cli"`，符合编程客户端的定位。

### 10.5 `/v1/chat/completions` 默认关闭

**发现**：HTTP 聊天端点默认关闭，需要 Gateway 管理员显式启用：

```json5
{
  gateway: {
    http: {
      endpoints: {
        chatCompletions: { enabled: true }
      }
    }
  }
}
```

SDK 在错误信息中包含启用提示，帮助用户快速定位问题。

---

## 11. 总结

### 架构设计原则

1. **双传输分工明确**：HTTP 处理需要写权限的操作，WebSocket 处理读操作和实时事件
2. **协议忠实**：严格遵循 Gateway 的 WebSocket 帧格式和握手协议
3. **响应式优先**：使用 Project Reactor 的 `Mono`/`Flux`/`Sinks` 构建非阻塞通信
4. **容错设计**：指数退避重连、请求超时、pending 请求清理
5. **Spring Native**：条件装配、属性绑定、生命周期管理完全 Spring 化

### 关键经验

1. **不要假设协议**：文档中的示例参数（如 `mode: "operator"`）可能在特定 Gateway 版本中不合法，必须验证枚举值
2. **关注权限边界**：WebSocket 和 HTTP 的 scope 授予机制可能完全不同，需要在架构层面考虑
3. **错误信息是最好的调试工具**：Gateway 的 `missing scope` 错误直接指向了架构问题的根源
4. **日志是金**：握手成功后打印 Gateway 授予的实际 `role` 和 `scopes`，可以快速定位权限问题

### 未来方向

1. **设备配对支持**：实现完整的 device pairing 流程，使 WebSocket 获得完整 scope
2. **scope 自适应**：根据 Gateway 授予的实际 scope 动态选择传输方式
3. **RPC 端点扩展**：随着 Gateway HTTP 端点的扩展，逐步将更多操作迁移到 HTTP
4. **连接池优化**：WebSocket 连接复用和池化管理
