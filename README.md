# OpenClaw Spring Boot Starter

一个 Spring Boot Starter，提供与 **OpenClaw Runtime** 的无缝集成 — 一个 AI 驱动的运行时平台，支持聊天、任务执行和技能编排。该 SDK 自动配置 HTTP/WebSocket 客户端、会话生命周期、事件分发、技能注册和回调处理，使业务应用能够以最少的样板代码开始与 OpenClaw 交互。

## 功能特性

- **零配置自动装配** — 只需在 `application.yml` 中设置 `openclaw.endpoint` 和 `openclaw.token` 即可激活 SDK
- **同步和流式聊天** — `OpenClawRuntime.chat()` 用于阻塞调用，`OpenClawRuntime.stream()` 用于响应式 SSE 流式传输
- **会话生命周期管理** — 创建、恢复和关闭运行时会话，支持自动心跳保活
- **技能自动发现与注册** — 使用 `@OpenClawSkill` 注解标注类，SDK 会在启动时自动扫描、生成 JSON Schema 并注册
- **事件驱动架构** — 类型化事件模型（`RuntimeEvent`），包含 13+ 种事件子类型，分发到 `RuntimeListener` 实现
- **回调端点** — 可配置的 REST 控制器，接收来自 OpenClaw Gateway 的异步回调
- **基于 WebClient 的 HTTP 层** — 非阻塞 HTTP 客户端，支持认证拦截器、日志记录和重试
- **优雅关闭** — 应用退出时自动关闭会话、注销技能并发布停止事件
- **分布式链路追踪** — 可选的 OpenTelemetry 集成，通过 Micrometer Tracing 实现
- **多模块架构** — 清晰的关注点分离，具有明确定义的模块边界

## 架构



![](.\docs\imgs\Structure.png)



## 模块说明

| 模块 | 描述 |
|--------|-------------|
| `openclaw-runtime-api` | 公共 API 接口、DTO、事件、监听器和异常 |
| `openclaw-runtime-client` | 用于 OpenClaw Gateway 通信的 HTTP 和 WebSocket 客户端 |
| `openclaw-runtime-session` | 会话生命周期管理（创建、恢复、关闭、心跳） |
| `openclaw-runtime-skill` | 技能框架 — 注解扫描、注册表、调度器、JSON Schema 生成 |
| `openclaw-runtime-event` | 事件发布、回调分发和 Webhook 管理 |
| `openclaw-runtime-converter` | JSON、运行时和 OpenClaw 表示之间的 DTO 转换层 |
| `openclaw-runtime-autoconfigure` | Spring Boot 自动配置、回调控制器、生命周期初始化器、关闭处理器 |
| `openclaw-runtime-starter` | Spring Boot Starter — 业务应用所需的唯一依赖 |
| `samples/sample-runtime` | 示例应用，演示聊天、流式传输和会话管理 |
| `samples/sample-skill-provider` | 示例应用，演示技能自动发现和注册 |

## 快速开始

### 1. 添加依赖

将 starter 添加到你的 `pom.xml`：

```xml
<dependency>
    <groupId>io.openclaw.runtime</groupId>
    <artifactId>openclaw-runtime-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置 application.yml

```yaml
openclaw:
  # 必填：OpenClaw Gateway 端点 URL
  endpoint: https://gateway.openclaw.io
  # 必填：认证令牌
  token: your-api-token
  # 可选：默认工作空间 ID
  workspace: default-workspace
  # 可选：是否在启动时自动注册技能（默认：true）
  auto-register-skill: true
  # 可选：请求超时时间（默认：30s）
  timeout: 30s
  # 可选：HTTP 日志记录
  log-request: false
  log-response: false

  callback:
    # 可选：回调端点路径（默认：/openclaw/callback）
    path: /openclaw/callback
    # 可选：启用/禁用回调端点（默认：true）
    enabled: true

  retry:
    # 可选：最大重试次数（默认：3）
    max-attempts: 3
    # 可选：重试间隔时间（默认：1s）
    backoff: 1s

  heartbeat:
    # 可选：启用心跳（默认：true）
    enabled: true
    # 可选：心跳间隔（默认：30s）
    interval: 30s

  stream:
    # 可选：启用流式传输（默认：true）
    enabled: true
    # 可选：重连延迟（默认：5s）
    reconnect-delay: 5s

  trace:
    # 可选：启用分布式链路追踪（默认：false）
    enabled: false
    # 可选：追踪数据导出器类型（默认：otel）
    exporter: otel
```

### 3. 使用 OpenClawRuntime

注入 `OpenClawRuntime` 并开始与 OpenClaw 交互：

```java
import io.openclaw.runtime.api.OpenClawRuntime;
import io.openclaw.runtime.api.dto.ChatRequest;
import io.openclaw.runtime.api.dto.ChatResponse;
import io.openclaw.runtime.api.dto.RuntimeSession;
import io.openclaw.runtime.api.dto.StreamMode;
import org.springframework.stereotype.Service;

@Service
public class MyChatService {

    private final OpenClawRuntime runtime;

    public MyChatService(OpenClawRuntime runtime) {
        this.runtime = runtime;
    }

    public ChatResponse chat(String sessionId, String message) {
        ChatRequest request = ChatRequest.builder()
                .sessionId(sessionId)
                .message(message)
                .mode(StreamMode.SYNC)
                .build();
        return runtime.chat(request);
    }

    public RuntimeSession startSession() {
        return runtime.createSession();
    }

    public void endSession(String sessionId) {
        runtime.closeSession(sessionId);
    }
}
```

## 技能开发

### 定义技能

使用 `@OpenClawSkill` 注解标注任意类并实现 `Skill` 接口。SDK 会自动发现它、根据其结构生成 JSON Schema，并在启动时注册到 OpenClaw Gateway。

```java
import com.fasterxml.jackson.databind.JsonNode;
import io.openclaw.runtime.api.dto.SkillResult;
import io.openclaw.runtime.api.skill.Skill;
import io.openclaw.runtime.skill.annotation.OpenClawSkill;

@OpenClawSkill(
        name = "translate_text",
        description = "Translate text between languages"
)
public class TranslateTextSkill implements Skill {

    @Override
    public SkillResult invoke(JsonNode arguments) {
        String text = arguments.get("text").asText();
        String targetLang = arguments.get("targetLanguage").asText();

        // ... 执行翻译 ...

        return SkillResult.builder()
                .skillName("translate_text")
                .success(true)
                .data(resultNode)
                .executionTimeMs(42L)
                .build();
    }
}
```

### 监听事件

实现 `RuntimeListener` 以响应运行时生命周期事件。所有方法都有默认的无操作实现，因此只需覆盖你需要的方法。

```java
import io.openclaw.runtime.api.event.*;
import io.openclaw.runtime.api.listener.RuntimeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MyRuntimeListener implements RuntimeListener {

    private static final Logger log = LoggerFactory.getLogger(MyRuntimeListener.class);

    @Override
    public void onRuntimeStarted(RuntimeStartedEvent event) {
        log.info("Runtime started: endpoint={}", event.getEndpoint());
    }

    @Override
    public void onSessionCreated(SessionCreatedEvent event) {
        log.info("Session created: id={}", event.getSessionId());
    }

    @Override
    public void onTaskFinished(TaskFinishedEvent event) {
        log.info("Task finished: id={}", event.getTaskId());
    }

    @Override
    public void onError(ErrorEvent event) {
        log.error("Runtime error: {}: {}", event.getErrorCode(), event.getErrorMessage());
    }
}
```

## 配置参考

| 配置项 | 类型 | 默认值 | 描述 |
|----------|------|---------|-------------|
| `openclaw.endpoint` | `String` | — | OpenClaw Gateway 端点 URL（**必填**） |
| `openclaw.token` | `String` | — | OpenClaw API 认证令牌 |
| `openclaw.workspace` | `String` | — | 默认工作空间 ID |
| `openclaw.auto-register-skill` | `boolean` | `true` | 是否在启动时自动注册技能 |
| `openclaw.timeout` | `Duration` | `30s` | 请求超时时间 |
| `openclaw.log-request` | `boolean` | `false` | 是否记录 HTTP 请求日志 |
| `openclaw.log-response` | `boolean` | `false` | 是否记录 HTTP 响应日志 |
| `openclaw.callback.path` | `String` | `/openclaw/callback` | 回调端点路径 |
| `openclaw.callback.enabled` | `boolean` | `true` | 是否启用回调端点 |
| `openclaw.retry.max-attempts` | `int` | `3` | 最大重试次数 |
| `openclaw.retry.backoff` | `Duration` | `1s` | 重试间隔时间 |
| `openclaw.heartbeat.enabled` | `boolean` | `true` | 是否启用心跳 |
| `openclaw.heartbeat.interval` | `Duration` | `30s` | 心跳间隔 |
| `openclaw.stream.enabled` | `boolean` | `true` | 是否启用流式传输 |
| `openclaw.stream.reconnect-delay` | `Duration` | `5s` | 流式连接重连延迟 |
| `openclaw.trace.enabled` | `boolean` | `false` | 是否启用分布式链路追踪 |
| `openclaw.trace.exporter` | `String` | `otel` | 追踪数据导出器类型（`otel` 表示 OpenTelemetry） |

## 从源码构建

**前置条件：** Java 21 或更高版本，Apache Maven 3.9+。

```bash
git clone <repository-url>
cd openclaw-spring-boot-starter
mvn clean install
```

开发时跳过测试：

```bash
mvn clean install -DskipTests
```

## 项目结构

```
openclaw-spring-boot-starter/
├── pom.xml                            # 父 POM（多模块聚合）
├── openclaw-runtime-api/              # 公共 API：接口、DTO、事件、异常
│   └── src/main/java/io/openclaw/runtime/api/
│       ├── OpenClawRuntime.java       # 主入口接口
│       ├── dto/                       # ChatRequest、ChatResponse、RuntimeSession、SkillDefinition、SkillResult
│       ├── event/                     # RuntimeEvent 及 13+ 种事件子类型
│       ├── exception/                 # OpenClawRuntimeException 及类型化子类型
│       ├── interceptor/               # LifecycleInterceptor
│       ├── listener/                  # RuntimeListener
│       └── skill/                     # Skill 接口
├── openclaw-runtime-client/           # HTTP 和 WebSocket 客户端层
│   └── src/main/java/io/openclaw/runtime/client/
│       ├── OpenClawClient.java        # 聚合所有领域客户端的门面
│       ├── dto/                       # 传输层 DTO
│       ├── http/                      # ChatClient、TaskClient、SessionHttpClient、EventClient、UploadClient、ArtifactClient
│       ├── interceptor/               # AuthInterceptor、LoggingInterceptor
│       └── websocket/                 # OpenClawWebSocketClient
├── openclaw-runtime-session/          # 会话生命周期管理
│   └── src/main/java/io/openclaw/runtime/session/
│       ├── SessionManager.java
│       ├── SessionLifecycleManager.java
│       └── HeartbeatManager.java
├── openclaw-runtime-skill/            # 技能框架
│   └── src/main/java/io/openclaw/runtime/skill/
│       ├── annotation/                # @OpenClawSkill
│       ├── dispatcher/                # SkillDispatcher
│       ├── model/                     # SkillMetadata
│       ├── registry/                  # SkillRegistry、SkillRegistrar
│       ├── scanner/                   # SkillScanner、SkillMetadataBuilder
│       └── schema/                    # JsonSchemaGenerator
├── openclaw-runtime-event/            # 事件发布和回调处理
│   └── src/main/java/io/openclaw/runtime/event/
│       ├── EventPublisher.java
│       ├── callback/                  # CallbackDispatcher、CallbackPayloadParser
│       └── webhook/                   # WebhookManager
├── openclaw-runtime-converter/        # DTO 转换层
│   └── src/main/java/io/openclaw/runtime/converter/
│       ├── RuntimeConverter.java      # 门面转换器
│       ├── ChatConverter.java
│       ├── SessionConverter.java
│       ├── SkillConverter.java
│       └── EventConverter.java
├── openclaw-runtime-autoconfigure/    # Spring Boot 自动配置
│   └── src/main/java/io/openclaw/runtime/autoconfigure/
│       ├── OpenClawAutoConfiguration.java
│       ├── OpenClawCallbackAutoConfiguration.java
│       ├── OpenClawCallbackController.java
│       ├── OpenClawLifecycleInitializer.java
│       ├── OpenClawProperties.java
│       ├── OpenClawShutdownHandler.java
│       ├── OpenClawSkillAutoConfiguration.java
│       ├── runtime/                   # DefaultOpenClawRuntime
│       └── trace/                     # TraceAutoConfiguration、OpenClawTracer
├── openclaw-runtime-starter/          # Spring Boot Starter（聚合器）
│   └── src/main/resources/
├── samples/
│   ├── sample-runtime/                # 示例：聊天、流式传输、会话管理
│   │   └── src/main/java/io/openclaw/runtime/sample/
│   │       ├── SampleRuntimeApplication.java
│   │       ├── controller/ChatController.java
│   │       ├── listener/SampleRuntimeListener.java
│   │       └── service/ChatDemoService.java
│   └── sample-skill-provider/         # 示例：技能自动发现
│       └── src/main/java/io/openclaw/runtime/sample/skill/
│           ├── SampleSkillProviderApplication.java
│           ├── script/GenerateScriptSkill.java
│           └── translate/TranslateTextSkill.java
└── README.md
```

## 许可证

本项目基于 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) 提供。
