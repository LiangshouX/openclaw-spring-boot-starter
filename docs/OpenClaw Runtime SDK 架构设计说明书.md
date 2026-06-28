# OpenClaw Spring Boot Starter 架构设计说明书（Architecture Design Specification）

**项目名称**

OpenClaw Spring Boot Starter

**版本**

V1.0

**技术栈**

- Java 21
- Spring Boot 3.x
- Maven Multi Module
- Spring WebClient
- Jackson
- Reactor（仅SDK内部使用）
- Micrometer + OpenTelemetry（可选）
- JUnit5

------

# 1. 项目背景

## 1.1 项目目标

一个用于 Spring Boot 应用无缝集成 OpenClaw Runtime 的企业级 Starter，提供 Runtime 管理、Session 生命周期、Skill 自动发现与注册、事件同步、Callback 管理以及统一编程模型。

SDK作为业务系统与OpenClaw之间唯一通信层，实现：

- OpenClaw Runtime调用
- Session管理
- Skill自动注册
- Tool调用
- Event同步
- Callback处理
- DTO转换
- Runtime生命周期管理

SDK不包含任何业务逻辑。

SDK不修改OpenClaw源码。

SDK不依赖OpenClaw Plugin。

SDK通过官方Gateway/App API与OpenClaw通信。

------

# 2. 设计原则

## 2.1 唯一职责

每个Module只负责一个领域。

------

## 2.2 高内聚低耦合

业务层不得直接调用OpenClaw API。

所有调用统一经过Runtime。

------

## 2.3 Spring Native

SDK完全Spring化。

所有Bean自动配置。

业务零配置接入。

------

## 2.4 Adapter模式

业务DTO

↓

Runtime DTO

↓

OpenClaw DTO

↓

OpenClaw

------

## 2.5 Stateless

SDK不保存业务数据。

Session状态仅保存运行时信息。

Conversation、Project、Task等业务对象全部由业务系统维护。

------

## 2.6 Auto Discovery

Skill自动发现。

自动生成Schema。

自动注册。

------

## 2.7 Event Driven

所有生命周期全部事件化。

------

# 3. 总体架构

```text
                       Spring Boot Application
                               │
                               │
                     OpenClawRuntime
                               │
 ┌─────────────────────────────────────────────────────┐
 │              OpenClaw Spring Boot Starter                   │
 │                                                     │
 │ Runtime API                                         │
 │ Client                                              │
 │ Session                                             │
 │ Skill                                               │
 │ Registry                                            │
 │ Callback                                            │
 │ Event                                               │
 │ Converter                                           │
 │ Trace                                               │
 │ AutoConfiguration                                   │
 └─────────────────────────────────────────────────────┘
                               │
                        HTTP / WebSocket
                               │
                       OpenClaw Gateway
                               │
                         OpenClaw Cloud
```

------

# 4. Maven工程结构

```text
openclaw-runtime-sdk
│
├── pom.xml
│
├── openclaw-runtime-api
│
├── openclaw-runtime-client
│
├── openclaw-runtime-session
│
├── openclaw-runtime-skill
│
├── openclaw-runtime-event
│
├── openclaw-runtime-converter
│
├── openclaw-runtime-autoconfigure
│
├── openclaw-runtime-starter
│
└── samples
    │
    ├── sample-runtime
    │
    └── sample-skill-provider
```

------

# 5. Module职责

## openclaw-runtime-api

提供SDK全部公开接口。

包含：

- Runtime接口
- DTO
- Listener
- Event定义
- Session对象
- Skill接口

禁止依赖任何实现模块。

------

## openclaw-runtime-client

负责所有HTTP/WebSocket通信。

包含：

- Chat API
- Task API
- Session API
- Event API
- Artifact API
- Upload API

只负责协议通信。

不包含业务逻辑。

------

## openclaw-runtime-session

负责：

Session生命周期。

包含：

Create

Load

Resume

Close

Expire

Heartbeat

------

## openclaw-runtime-skill

负责：

Skill扫描。

Skill注册。

Skill调用。

Schema生成。

Dispatcher。

------

## openclaw-runtime-event

负责：

SDK事件。

OpenClaw事件。

Listener。

Publisher。

Webhook。

------

## openclaw-runtime-converter

负责：

DTO转换。

业务对象

↓

Runtime对象

↓

OpenClaw对象

反向同理。

------

## openclaw-runtime-autoconfigure

负责：

Spring自动装配。

Bean注册。

Properties绑定。

------

## openclaw-runtime-starter

提供：

Spring Boot Starter。

业务只依赖此模块。

------

# 6. Package结构

```text
com.xxx.openclaw.runtime

├── api
│
├── configuration
│
├── client
│      ├── http
│      ├── websocket
│      ├── dto
│      └── interceptor
│
├── runtime
│
├── session
│
├── skill
│      ├── annotation
│      ├── scanner
│      ├── dispatcher
│      ├── registry
│      ├── schema
│      └── model
│
├── callback
│
├── converter
│
├── event
│
├── trace
│
├── exception
│
└── util
```

------

# 7. Runtime API

SDK唯一入口：

```java
public interface OpenClawRuntime {

    ChatResponse chat(ChatRequest request);

    Flux<RuntimeEvent> stream(ChatRequest request);

    RuntimeSession createSession();

    RuntimeSession resumeSession(String sessionId);

    void closeSession(String sessionId);

    void registerSkill();

    void addListener(RuntimeListener listener);

}
```

业务系统禁止直接调用Client。

------

# 8. Client设计

Client只负责通信。

```text
OpenClawClient

├── ChatClient

├── TaskClient

├── SessionClient

├── EventClient

├── UploadClient

└── ArtifactClient
```

统一：

Request

↓

HTTP

↓

Response

------

# 9. Session设计

Session对象：

```text
RuntimeSession

sessionId

conversationId

taskId

workspaceId

runtimeId

createTime

expireTime

status
```

生命周期：

```text
Create

↓

Active

↓

Running

↓

Suspend

↓

Resume

↓

Completed

↓

Closed
```

SDK只管理Runtime Session。

Conversation由业务维护。

------

# 10. Skill Framework

Skill采用注解。

```java
@OpenClawSkill(
    name="generate_script",
    description="生成剧本"
)
```

接口：

```java
public interface Skill {

    SkillResult invoke(JsonNode arguments);

}
```

启动：

Spring扫描

↓

生成SkillDefinition

↓

生成JSON Schema

↓

注册OpenClaw

↓

Ready

------

# 11. Skill Registry

Registry负责：

Skill

↓

Manifest

↓

Register API

↓

OpenClaw

Manifest结构：

```json
{
  "skills":[]
}
```

自动维护版本。

自动重新注册。

------

# 12. Dispatcher

收到ToolCall：

↓

SkillDispatcher

↓

Skill Bean

↓

invoke()

↓

Result

↓

OpenClaw

------

# 13. Event Framework

统一事件：

```text
RuntimeStarted

RuntimeStopped

SessionCreated

SessionClosed

TaskStarted

TaskFinished

TaskFailed

ToolCalling

ToolFinished

ArtifactCreated

Reasoning

Streaming

Error
```

全部继承：

RuntimeEvent

------

# 14. Callback Framework

默认：

```text
POST

/openclaw/callback
```

内部：

CallbackController

↓

CallbackDispatcher

↓

EventPublisher

↓

Listener

业务无需Controller。

------

# 15. Converter

统一DTO转换。

```text
Business DTO

↓

Runtime DTO

↓

OpenClaw DTO

↓

Runtime DTO

↓

Business DTO
```

所有映射集中管理。

------

# 16. Trace

自动创建Span。

Span包括：

Runtime

↓

Session

↓

Task

↓

Tool

↓

HTTP

支持OTEL。

默认关闭。

------

# 17. 配置项

```yaml
openclaw:

  endpoint:

  token:

  workspace:

  callback:

  timeout:

  retry:

  heartbeat:

  stream:

  auto-register-skill:

  trace:

  log-request:

  log-response:
```

------

# 18. 自动装配

AutoConfiguration负责：

OpenClawRuntime

OpenClawClient

SessionManager

SkillScanner

SkillRegistry

Dispatcher

CallbackController

EventPublisher

Converter

全部自动注入。

------

# 19. 生命周期

```text
Application Start

↓

Load Properties

↓

Create Client

↓

Create Runtime

↓

Scan Skills

↓

Generate Schema

↓

Register Skills

↓

Start Callback

↓

Start Heartbeat

↓

Ready
```

关闭：

```text
Application Stop

↓

Close Session

↓

Unregister Skills

↓

Destroy Client

↓

Shutdown
```

------

# 20. 异常体系

统一异常：

```text
RuntimeException

│

├── ClientException

├── AuthenticationException

├── SessionException

├── SkillException

├── CallbackException

├── RegisterException

├── TimeoutException

└── ConverterException
```

统一ErrorCode。

------

# 21. 扩展点

提供生命周期Hook。

```text
BeforeRequest

AfterResponse

BeforeRegisterSkill

AfterRegisterSkill

BeforeToolCall

AfterToolCall

BeforeCallback

AfterCallback
```

统一接口：

LifecycleInterceptor

------

# 22. 日志规范

统一Logger。

请求ID贯穿全链路。

日志级别：

INFO：

生命周期。

DEBUG：

请求响应。

ERROR：

异常。

TRACE：

HTTP Body。

------

# 23. 测试规范

每个Module：

- Unit Test覆盖率≥80%
- Integration Test
- Mock OpenClaw Gateway
- TestContainers支持

------

# 24. 示例工程

## sample-runtime

演示：

Runtime调用。

Session。

Streaming。

## sample-skill-provider

演示：

Skill开发。

自动扫描。

自动注册。

Tool调用。

------

# 25. 开发里程碑

## Milestone 1

基础工程。

Multi Module。

Starter。

Properties。

API。

预计：

2天。

------

## Milestone 2

HTTP Client。

Gateway通信。

Streaming。

预计：

3天。

------

## Milestone 3

Session。

Runtime。

生命周期。

预计：

2天。

------

## Milestone 4

Skill。

Annotation。

Scanner。

Registry。

Dispatcher。

Schema。

预计：

4天。

------

## Milestone 5

Event。

Callback。

Trace。

Listener。

预计：

3天。

------

## Milestone 6

测试。

Sample。

文档。

发布。

预计：

2天。

------

# 26. 对业务系统暴露能力

业务仅依赖：

```java
@Autowired
private OpenClawRuntime runtime;
```

即可获得：

- Runtime调用
- Streaming
- Session管理
- Skill自动注册
- Callback
- Event监听
- Tool调用
- 生命周期管理

业务无需关心：

- HTTP协议
- OpenClaw API
- Session同步
- Skill注册
- Schema生成
- Callback处理
- Runtime初始化
- Bean装配
- Event分发

SDK作为Spring Boot应用与OpenClaw之间唯一的Runtime集成层，对上提供统一Java API，对下屏蔽OpenClaw协议细节，保证业务代码稳定、可维护、可升级。