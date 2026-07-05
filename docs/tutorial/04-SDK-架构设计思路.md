# 第4章：SDK 架构设计思路

> **学习目标**：理解 SDK 的模块划分、依赖关系、双传输架构，以及背后的设计模式。

---

## 4.1 设计目标

我们要构建一个 Spring Boot Starter，让 Java 开发者只需添加一个依赖 + 配置端点，就能接入 OpenClaw：

```yaml
# application.yml — 用户只需写这些
openclaw:
  endpoint: http://gateway:13015/path
  token: your-auth-token
```

```java
// 业务代码 — 直接注入使用
@Autowired
private OpenClawRuntime runtime;

runtime.sendMessage("你好");
```

## 4.2 模块划分

SDK 采用 Maven 多模块结构，每个模块有明确职责：

```
openclaw-spring-boot-starter/
│
├── openclaw-runtime-api/           ← 🧱 地基：接口、DTO、事件、异常
│   └── 零内部依赖
│
├── openclaw-runtime-client/        ← 🌐 通信层：HTTP + WebSocket 客户端
│   └── 依赖 api
│
├── openclaw-runtime-event/         ← 📢 事件发布
│   └── 依赖 api
│
├── openclaw-runtime-session/       ← 🔄 会话管理
│   └── 依赖 api, client
│
├── openclaw-runtime-skill/         ← 🛠️ 技能框架
│   └── 依赖 api, client
│
├── openclaw-runtime-converter/     ← 🔀 DTO 转换
│   └── 依赖 api, client
│
├── openclaw-runtime-autoconfigure/ ← ⚙️ Spring Boot 自动配置
│   └── 依赖 ALL above
│
├── openclaw-runtime-starter/       ← 📦 依赖聚合器（无源码）
│   └── 依赖 autoconfigure
│
└── samples/                        ← 📝 示例应用
```

### 依赖关系图

```
api ◄─────────────────────────────────── 所有模块依赖
 │
 ├── client ◄── session
 │    │         skill
 │    │         converter
 │    │
 │    └──────── event
 │
 └──► autoconfigure ◄── 聚合所有模块
          │
      starter（纯 POM，无源码）
```

### 为什么这样划分？

| 原则 | 说明 |
|------|------|
| **单一职责** | 每个模块只做一件事 |
| **最小依赖** | `api` 模块零依赖，任何人都能引用 |
| **可替换** | 用户可以替换任何模块的实现（`@ConditionalOnMissingBean`） |
| **渐进复杂度** | 简单使用只需 `starter`；高级使用可以依赖具体模块 |

## 4.3 双传输架构

基于第3章发现的权限差异，SDK 采用 HTTP + WebSocket 双传输：

```
┌─────────────────────────────────────────────────────────────┐
│                      OpenClawClient（门面）                   │
│                                                              │
│   ┌────────────────────┐     ┌───────────────────────────┐  │
│   │   HTTP 传输层       │     │   WebSocket 传输层         │  │
│   │   (WebClient)      │     │   (ReactorNettyWSClient)  │  │
│   │                    │     │                           │  │
│   │  ChatClient        │     │  SessionHttpClient        │  │
│   │  UploadClient      │     │  TaskClient               │  │
│   │                    │     │  EventClient              │  │
│   │  用于：写操作       │     │  ArtifactClient           │  │
│   │  权限：完整 scope   │     │                           │  │
│   │                    │     │  用于：读操作 + 事件订阅    │  │
│   │                    │     │  权限：受限于设备配对       │  │
│   └────────────────────┘     └───────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## 4.4 关键设计模式

### 门面模式（Facade）

`OpenClawClient` 聚合所有领域客户端，提供统一入口：

```java
@Data @Builder
public class OpenClawClient {
    private ChatClient chatClient;
    private TaskClient taskClient;
    private SessionHttpClient sessionClient;
    private EventClient eventClient;
    private UploadClient uploadClient;
    private ArtifactClient artifactClient;
    private OpenClawWebSocketClient webSocketClient;
}
```

### 构建者模式（Builder）

所有 DTO 使用 Lombok `@Builder`：

```java
ChatRequest request = ChatRequest.builder()
    .sessionId("my-session")
    .message("你好")
    .mode(StreamMode.STREAM)
    .build();
```

### 条件装配（Conditional Configuration）

所有 Bean 都可以被用户覆盖：

```java
@Bean
@ConditionalOnMissingBean    // ← 如果用户定义了自己的，就不创建默认的
public ChatClient chatClient(WebClient webClient) {
    return new ChatClient(webClient);
}
```

### 响应式编程（Reactive）

底层使用 Project Reactor 的 `Mono`/`Flux`：

```java
// Mono = 0或1个结果的异步操作
public Mono<JsonNode> invoke(String method, Object params) { ... }

// Flux = 0到N个结果的异步流
public Flux<String> streamMessage(ChatRequest request) { ... }
```

## 4.5 异常体系

统一的异常层级，方便业务层捕获：

```
RuntimeException
└── OpenClawRuntimeException (abstract)
    ├── ClientException          ← HTTP/WS 通信错误
    ├── AuthenticationException  ← 认证失败
    ├── SessionException         ← 会话错误
    ├── SkillException           ← 技能调用失败
    ├── CallbackException        ← 回调处理错误
    ├── RegisterException        ← 注册失败
    ├── TimeoutException         ← 超时
    └── ConverterException       ← DTO 转换错误
```

每个异常携带 `ErrorCode` 枚举：

```java
public enum ErrorCode {
    HTTP_ERROR, WEBSOCKET_ERROR, CONNECTION_REFUSED,
    CONNECTION_TIMEOUT, REQUEST_TIMEOUT,
    AUTHENTICATION_FAILED, SESSION_NOT_FOUND,
    SKILL_NOT_FOUND, SKILL_INVOCATION_FAILED,
    CALLBACK_ERROR, CONVERSION_ERROR, UNKNOWN
}
```

## 4.6 数据流全景

### 发送聊天消息

```
业务代码
  │
  ▼
OpenClawRuntime.sendMessage("你好")
  │
  ▼
ChatClient.sendMessage(ChatRequest)
  │
  ├── 构建 OpenAI 格式请求体
  │   {model:"openclaw/default", messages:[...], stream:false}
  │
  ├── HTTP POST /v1/chat/completions
  │   Authorization: Bearer <token>
  │   x-openclaw-session-key: my-session
  │
  ├── 解析 OpenAI 格式响应
  │   choices[0].message.content → ChatResponse.content
  │
  ▼
返回 ChatResponse
```

### 查询会话

```
业务代码
  │
  ▼
SessionHttpClient.getSession("main")
  │
  ├── 构建 RPC 帧
  │   {type:"req", id:"42", method:"sessions.get",
  │    params:{key:"main"}}
  │
  ├── WebSocket 发送
  │   OpenClawWebSocketClient.invoke("sessions.get", params)
  │
  ├── 等待响应（通过 id 关联）
  │   {type:"res", id:"42", ok:true, payload:{...}}
  │
  ▼
返回 JsonNode (payload)
```

## 4.7 小结

| 设计决策 | 原因 |
|---------|------|
| 多模块划分 | 职责清晰，依赖最小化，可替换 |
| 双传输架构 | HTTP 有完整 Scope，WS 有实时事件 |
| 门面模式 | 统一入口，隐藏内部复杂度 |
| 条件装配 | 每个 Bean 都可被用户覆盖 |
| 统一异常体系 | 业务层方便捕获和处理 |

**下一章**：[第5章：WebSocket 通信层实现](05-WebSocket-通信层实现.md) — 动手实现 WebSocket 客户端的核心代码。
