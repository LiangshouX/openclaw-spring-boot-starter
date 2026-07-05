# 第8章：Spring Boot 自动配置实战

> **学习目标**：理解 Spring Boot AutoConfiguration 的工作原理，掌握条件装配、属性绑定和生命周期管理。

---

## 8.1 自动配置的目标

用户只需做两件事：

```xml
<!-- 1. 添加依赖 -->
<dependency>
    <groupId>io.openclaw</groupId>
    <artifactId>openclaw-runtime-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

```yaml
# 2. 配置端点
openclaw:
  endpoint: http://gateway:13015/path
  token: your-auth-token
```

其余一切（WebSocket 连接、HTTP 客户端、技能扫描、事件发布）都**自动完成**。

## 8.2 激活条件

整个 SDK 由一个属性激活：

```java
@AutoConfiguration
@EnableConfigurationProperties(OpenClawProperties.class)
@ConditionalOnProperty(prefix = "openclaw", name = "endpoint")
public class OpenClawAutoConfiguration {
    // 只有设置了 openclaw.endpoint 才会加载
}
```

| 配置 | 结果 |
|------|------|
| `openclaw.endpoint` 已设置 | SDK 激活，创建所有 Bean |
| `openclaw.endpoint` 未设置 | SDK 不激活，零开销 |

## 8.3 属性配置类

```java
@Data
@ConfigurationProperties(prefix = "openclaw")
public class OpenClawProperties {
    private String endpoint;           // Gateway HTTP 端点（必填）
    private String token;              // 认证令牌（必填）
    private String workspace = "main"; // 默认工作区
    private boolean autoRegisterSkill = true;
    private boolean logRequest = false;
    private boolean logResponse = false;
    private Duration timeout = Duration.ofSeconds(30);

    // WebSocket 配置（嵌套类）
    private WebSocketProperties websocket = new WebSocketProperties();

    // 回调配置
    private CallbackProperties callback = new CallbackProperties();

    @Data
    public static class WebSocketProperties {
        private String endpoint;                // WS 端点（默认从 endpoint 推导）
        private int protocolVersion = 4;
        private Duration connectTimeout = Duration.ofSeconds(15);
        private Duration requestTimeout = Duration.ofSeconds(30);
        private boolean autoConnect = true;
        private int maxReconnectAttempts = 10;
    }

    @Data
    public static class CallbackProperties {
        private String path = "/openclaw/callback";
        private boolean enabled = true;
    }
}
```

### 完整配置示例

```yaml
openclaw:
  endpoint: http://47.108.28.113:13015/ad258850
  token: 8e5b30281096f27deb91931ff09adc18
  workspace: main
  timeout: 30s
  auto-register-skill: true
  log-request: true
  log-response: true
  websocket:
    endpoint: null              # 自动推导: http→ws, https→wss
    protocol-version: 4
    connect-timeout: 15s
    request-timeout: 30s
    auto-connect: true
    max-reconnect-attempts: 10
  callback:
    path: /openclaw/callback
    enabled: true
```

## 8.4 Bean 创建顺序

```
┌─────────────────────────────────────────────────────────┐
│  Phase 1: 基础设施                                       │
│  ├─ ObjectMapper (Jackson)                              │
│  ├─ WebClient (HTTP 传输)                                │
│  ├─ AuthInterceptor                                     │
│  └─ LoggingInterceptor                                  │
├─────────────────────────────────────────────────────────┤
│  Phase 2: 传输层                                         │
│  ├─ OpenClawWebSocketClient (WebSocket 客户端)           │
│  ├─ ChatClient (HTTP 聊天)                              │
│  ├─ UploadClient (HTTP 文件上传)                         │
│  ├─ SessionHttpClient (WS 会话)                          │
│  ├─ TaskClient (WS 任务)                                 │
│  ├─ EventClient (WS 事件)                                │
│  └─ ArtifactClient (WS 制品)                             │
├─────────────────────────────────────────────────────────┤
│  Phase 3: 领域层                                         │
│  ├─ OpenClawClient (门面)                                │
│  ├─ SessionManager                                      │
│  ├─ HeartbeatManager                                    │
│  ├─ EventPublisher                                      │
│  ├─ SkillScanner + SkillRegistry + SkillDispatcher      │
│  └─ Converters (Chat/Session/Skill/Event)               │
├─────────────────────────────────────────────────────────┤
│  Phase 4: 生命周期                                       │
│  ├─ OpenClawRuntime (主入口)                             │
│  ├─ OpenClawShutdownHandler                             │
│  └─ OpenClawWebSocketInitializer (自动连接)              │
├─────────────────────────────────────────────────────────┤
│  Phase 5: 技能注册（条件激活）                            │
│  ├─ JsonSchemaGenerator                                 │
│  ├─ SkillRegistrar                                      │
│  └─ OpenClawLifecycleInitializer (扫描 + 注册)           │
└─────────────────────────────────────────────────────────┘
```

## 8.5 条件装配详解

### @ConditionalOnMissingBean

每个 Bean 都可以被用户覆盖：

```java
@Bean
@ConditionalOnMissingBean
public ChatClient chatClient(WebClient webClient, ObjectMapper objectMapper) {
    return new ChatClient(webClient, objectMapper);
}
```

如果用户自己定义了 `ChatClient` Bean，SDK 就不会创建默认的：

```java
// 用户自定义覆盖
@Configuration
public class MyConfig {
    @Bean
    public ChatClient chatClient(WebClient webClient) {
        return new MyCustomChatClient(webClient);
    }
}
```

### @ConditionalOnProperty

技能注册可以单独关闭：

```java
@AutoConfiguration(after = OpenClawAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "openclaw",
    name = "auto-register-skill",
    havingValue = "true",
    matchIfMissing = true    // ← 默认开启
)
public class OpenClawSkillAutoConfiguration { ... }
```

| 配置 | 结果 |
|------|------|
| `auto-register-skill: true`（或未设置） | 技能自动注册 |
| `auto-register-skill: false` | 跳过技能注册 |

## 8.6 WebSocket 端点自动推导

用户通常只配置 HTTP 端点，WS 端点自动推导：

```java
@Bean
public OpenClawWebSocketClient openClawWebSocketClient(
        OpenClawProperties properties, ObjectMapper objectMapper) {

    String wsEndpoint = properties.getWebsocket().getEndpoint();

    if (wsEndpoint == null || wsEndpoint.isBlank()) {
        // http://host:port → ws://host:port
        // https://host:port → wss://host:port
        wsEndpoint = properties.getEndpoint()
            .replaceFirst("^https://", "wss://")
            .replaceFirst("^http://", "ws://");
    }

    return new OpenClawWebSocketClient(
        wsEndpoint,
        properties.getToken(),
        objectMapper,
        properties.getWebsocket().getConnectTimeout(),
        properties.getWebsocket().getRequestTimeout(),
        properties.getWebsocket().getProtocolVersion(),
        properties.getWebsocket().getMaxReconnectAttempts());
}
```

## 8.7 启动时自动连接

```java
@Bean
public SmartInitializingSingleton openClawWebSocketInitializer(
        OpenClawWebSocketClient webSocketClient,
        OpenClawProperties properties) {
    return () -> {
        if (properties.getWebsocket().isAutoConnect()) {
            webSocketClient.connect()
                .doOnSuccess(v ->
                    log.info("WebSocket connected to gateway"))
                .doOnError(e ->
                    log.warn("WebSocket auto-connect failed: {}",
                        e.getMessage()))
                .subscribe();
        }
    };
}
```

`SmartInitializingSingleton` 在**所有单例 Bean 初始化后**执行，确保依赖的 Bean 都已就绪。

## 8.8 AutoConfiguration 注册

Spring Boot 3.x 使用 `AutoConfiguration.imports` 文件注册自动配置类：

```
# META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
io.openclaw.runtime.autoconfigure.OpenClawAutoConfiguration
io.openclaw.runtime.autoconfigure.OpenClawSkillAutoConfiguration
io.openclaw.runtime.autoconfigure.OpenClawCallbackAutoConfiguration
io.openclaw.runtime.autoconfigure.TraceAutoConfiguration
```

## 8.9 关闭时清理

```java
public class OpenClawShutdownHandler implements DisposableBean {
    @Override
    public void destroy() {
        // 1. 关闭会话
        sessionManager.closeAll();

        // 2. 注销技能
        if (skillRegistrar != null) {
            List<String> names = skillRegistry.getAll().stream()
                .map(m -> m.getDefinition().getName())
                .collect(Collectors.toList());
            skillRegistrar.unregisterFromOpenClaw(names);
        }

        // 3. 停止心跳
        heartbeatManager.shutdown();

        // 4. 发布事件
        eventPublisher.publish(new RuntimeStoppedEvent());
    }
}
```

## 8.10 启动日志示例

```
INFO  OpenClawAutoConfiguration - Creating WebClient for endpoint: http://...
INFO  OpenClawWebSocketClient - Connecting to ws://...
INFO  OpenClawWebSocketClient - WebSocket connected: server=2026.5.22,
       tickInterval=30000ms, role=operator, scopes=[]
INFO  OpenClawAutoConfiguration - WebSocket connected to gateway
INFO  OpenClawLifecycleInitializer - Initializing OpenClaw Runtime...
INFO  OpenClawLifecycleInitializer - OpenClaw Runtime initialized successfully
```

## 8.11 小结

| 知识点 | 要点 |
|--------|------|
| 激活条件 | `openclaw.endpoint` 设置即激活 |
| 属性绑定 | `@ConfigurationProperties` 自动映射 YAML |
| 条件装配 | `@ConditionalOnMissingBean` + `@ConditionalOnProperty` |
| WS 端点推导 | `http→ws`，`https→wss` |
| 自动连接 | `SmartInitializingSingleton` |
| 关闭清理 | `DisposableBean` |
| 注册文件 | `AutoConfiguration.imports` |

**下一章**：[第9章：打包发布与依赖管理](09-打包发布与依赖管理.md) — 如何把项目封装为一个可复用的 SDK。
