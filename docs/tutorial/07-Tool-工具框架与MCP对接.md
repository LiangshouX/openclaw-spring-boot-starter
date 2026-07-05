# 第7章：Tool 工具框架与 MCP 对接

> **学习目标**：理解注解驱动的工具（Tool）系统：从定义、扫描、注册到调度的完整生命周期。同时了解 OpenClaw 的 Skill（SKILL.md 指令文件）管理框架。

---

## 7.1 Tool 与 Skill 的区别

在 OpenClaw 生态中，**Tool** 和 **Skill** 是两个不同的概念：

| 概念 | 本质 | SDK 中的载体 | 作用 |
|------|------|-------------|------|
| **Tool** | 可调用的函数 | `@OpenClawTool` 注解的 Java 类 | Agent 执行具体操作（查天气、发消息等） |
| **Skill** | Markdown 指令文件 | `SKILL.md` 文件 | 教导 Agent 何时以及如何使用 Tool |

```
用户："帮我查一下今天的天气"
       │
       ▼
   Agent 推理：需要调用 weather_query 工具
       │
       ▼
   Gateway 发送 tool_call ──► SDK ──► ToolDispatcher
                                         │
                                    找到 WeatherTool
                                         │
                                    调用 invoke(arguments)
                                         │
                                    返回 ToolResult
                                         │
       ◄── 天气数据返回 ◄─────────────────┘
       │
   Agent 生成回复："今天北京晴，25°C"
```

## 7.2 定义一个 Tool

### 注解 + 接口

```java
// 1. 用 @OpenClawTool 注解标记
@OpenClawTool(
    name = "weather_query",
    description = "查询指定城市的天气信息",
    version = "1.0"
)
public class WeatherTool implements Tool {

    // 2. 实现 invoke 方法
    @Override
    public ToolResult invoke(JsonNode arguments) {
        String city = arguments.path("city").asText("北京");

        // 调用天气 API
        String weather = weatherService.query(city);

        // 返回结果
        JsonNode data = objectMapper.createObjectNode()
            .put("city", city)
            .put("weather", weather);

        return ToolResult.success("weather_query", data);
    }
}
```

### @OpenClawTool 注解

```java
@Target(ElementType.TYPE)         // 只能注解类
@Retention(RetentionPolicy.RUNTIME) // 运行时可读
@Component                        // ← 自动成为 Spring Bean！
public @interface OpenClawTool {
    String name();                    // 工具唯一名称
    String description() default "";  // 描述（Agent 用来决定是否调用）
    String version() default "1.0";   // 版本号
}
```

> **设计巧妙之处**：`@OpenClawTool` 上标注了 `@Component`，所以你不需要再写 `@Service` 或 `@Component`。一个注解搞定一切。

### Tool 接口

```java
public interface Tool {
    ToolResult invoke(JsonNode arguments);
}
```

极简的单方法接口。`arguments` 是 Gateway 传来的 JSON 参数。

### ToolResult

```java
@Data @Builder
public class ToolResult {
    private String toolName;
    private boolean success;
    private JsonNode data;          // 成功时的输出
    private String errorMessage;    // 失败时的错误信息
    private long executionTimeMs;

    // 工厂方法
    public static ToolResult success(String name, JsonNode data) { ... }
    public static ToolResult failure(String name, String error) { ... }
}
```

## 7.3 Tool 生命周期全景

```
Spring Boot 启动
    │
    ▼
┌─ OpenClawLifecycleInitializer.run() ─────────────────────┐
│                                                            │
│  ① 扫描 ── ToolScanner.scan(context)                      │
│     └─ context.getBeansWithAnnotation(@OpenClawTool)       │
│     └─ 找到所有 @OpenClawTool 注解的 Bean                   │
│                                                            │
│  ② 构建元数据 ── ToolMetadataBuilder.build(bean, anno)     │
│     └─ 提取 name, description, version                    │
│     └─ 定位 invoke(JsonNode) 方法                          │
│     └─ 生成 ToolMetadata                                   │
│                                                            │
│  ③ 本地注册 ── ToolRegistry.registerAll(metadataList)      │
│     └─ 存入 ConcurrentHashMap<String, ToolMetadata>        │
│                                                            │
│  ④ 生成 Schema ── JsonSchemaGenerator.generate(class)     │
│     └─ 从类字段推导 JSON Schema                            │
│     └─ 设置到 definition.jsonSchema                       │
│                                                            │
│  ⑤ 远程注册 ── ToolRegistrar.registerToOpenClaw(manifest) │
│     └─ 策略: ChatRequestToolStrategy + WebSocketToolStrategy│
│                                                            │
│  ⑥ 发布事件 ── EventPublisher.publish(RuntimeStartedEvent)│
│                                                            │
└────────────────────────────────────────────────────────────┘
```

## 7.4 扫描：ToolScanner

```java
public class ToolScanner {
    private final ToolMetadataBuilder metadataBuilder;

    public List<ToolMetadata> scan(ApplicationContext context) {
        List<ToolMetadata> metadataList = new ArrayList<>();

        // 利用 Spring 的注解发现机制
        Map<String, Object> beans =
            context.getBeansWithAnnotation(OpenClawTool.class);

        for (Object bean : beans.values()) {
            OpenClawTool annotation =
                bean.getClass().getAnnotation(OpenClawTool.class);

            if (annotation == null) continue;  // CGLIB 代理保护

            ToolMetadata metadata =
                metadataBuilder.build(bean, annotation);
            metadataList.add(metadata);

            log.info("发现工具: name={}, class={}",
                annotation.name(), bean.getClass().getName());
        }
        return metadataList;
    }
}
```

## 7.5 元数据构建：ToolMetadataBuilder

```java
public ToolMetadata build(Object bean, OpenClawTool annotation) {
    // 1. 从注解提取定义
    ToolDefinition definition = ToolDefinition.builder()
        .name(annotation.name())
        .description(annotation.description())
        .version(annotation.version())
        .className(bean.getClass().getName())
        .build();

    // 2. 定位 invoke 方法
    Method invokeMethod = findInvokeMethod(bean.getClass());

    // 3. 组装元数据
    return ToolMetadata.builder()
        .definition(definition)
        .targetBean(bean)           // 持有 Bean 引用
        .invokeMethod(invokeMethod) // 持有方法引用
        .registered(false)
        .build();
}

private Method findInvokeMethod(Class<?> clazz) {
    for (Method method : clazz.getMethods()) {
        if (method.getName().equals("invoke")
            && method.getParameterCount() == 1
            && method.getParameterTypes()[0] == JsonNode.class) {
            return method;
        }
    }
    throw new IllegalArgumentException(
        "找不到 invoke(JsonNode) 方法: " + clazz.getName());
}
```

## 7.6 JSON Schema 生成

Gateway 需要知道每个 Tool 接受什么参数。SDK 通过反射自动生成 JSON Schema：

```java
public JsonNode generate(Class<?> clazz) {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");

    ObjectNode properties = objectMapper.createObjectNode();
    for (Field field : clazz.getDeclaredFields()) {
        ObjectNode fieldSchema = objectMapper.createObjectNode();
        fieldSchema.put("type",
            mapJavaTypeToJsonSchemaType(field.getType()));
        properties.set(field.getName(), fieldSchema);
    }
    schema.set("properties", properties);
    return schema;
}

// Java 类型 → JSON Schema 类型映射
private String mapJavaTypeToJsonSchemaType(Class<?> type) {
    if (type == String.class) return "string";
    if (type == int.class || type == Integer.class) return "integer";
    if (type == double.class || type == Double.class) return "number";
    if (type == boolean.class || type == Boolean.class) return "boolean";
    if (type.isArray() || Collection.class.isAssignableFrom(type))
        return "array";
    return "object";
}
```

### 生成结果示例

对于输入类：
```java
public class WeatherInput {
    private String city;
    private int days;
    private boolean includeForecast;
}
```

生成的 JSON Schema：
```json
{
  "type": "object",
  "properties": {
    "city": { "type": "string" },
    "days": { "type": "integer" },
    "includeForecast": { "type": "boolean" }
  }
}
```

## 7.7 注册中心：ToolRegistry

```java
public class ToolRegistry {
    private final ConcurrentHashMap<String, ToolMetadata> tools =
        new ConcurrentHashMap<>();

    public void register(ToolMetadata metadata) {
        tools.put(metadata.getDefinition().getName(), metadata);
        metadata.setRegistered(true);
    }

    public ToolMetadata get(String toolName) {
        return tools.get(toolName);
    }

    public ToolManifest buildManifest() {
        List<ToolDefinition> definitions = tools.values().stream()
            .map(ToolMetadata::getDefinition)
            .collect(Collectors.toList());

        return ToolManifest.builder()
            .version("1.0")
            .generatedAt(Instant.now())
            .tools(definitions)
            .build();
    }
}
```

## 7.8 注册策略：ToolRegistrationStrategy

Tool 注册使用**策略模式**，支持多种注册通道：

```java
public interface ToolRegistrationStrategy {
    void register(ToolManifest manifest);
    void unregister(List<String> toolNames);
    String getName();
}
```

### ChatRequestToolStrategy

将工具定义转换为 OpenAI 兼容的 `tools` 数组格式，注入到每次聊天请求中：

```json
[
  {
    "type": "function",
    "function": {
      "name": "weather_query",
      "description": "查询指定城市的天气信息",
      "parameters": { "type": "object", "properties": { "city": { "type": "string" } } }
    }
  }
]
```

### WebSocketToolStrategy

通过 `tools.catalog` RPC 查询 Gateway 工具目录，验证工具可见性。

## 7.9 调度分发：ToolDispatcher

当 Gateway 回调 Tool 时，Dispatcher 负责找到对应的 Bean 并执行：

```java
public ToolResult dispatch(String toolName, JsonNode arguments) {
    // 1. 查找工具
    ToolMetadata metadata = toolRegistry.get(toolName);
    if (metadata == null) {
        throw new ToolException(ErrorCode.TOOL_NOT_FOUND,
            "未找到工具: " + toolName);
    }

    try {
        // 2. 前置拦截器
        for (LifecycleInterceptor interceptor : interceptors) {
            interceptor.beforeToolCall(toolName, arguments);
        }

        // 3. 反射调用
        Object result = metadata.getInvokeMethod()
            .invoke(metadata.getTargetBean(), arguments);

        ToolResult toolResult = (ToolResult) result;

        // 4. 后置拦截器
        for (LifecycleInterceptor interceptor : interceptors) {
            interceptor.afterToolCall(toolName, toolResult);
        }

        return toolResult;
    } catch (Exception e) {
        throw new ToolException(ErrorCode.TOOL_INVOCATION_FAILED,
            "工具调用失败: " + toolName, e);
    }
}
```

### 调度流程图

```
Gateway 回调
    │
    ▼
ToolDispatcher.dispatch("weather_query", {"city":"北京"})
    │
    ├── ToolRegistry.get("weather_query")
    │   └── 返回 ToolMetadata { targetBean: weatherTool,
    │                            invokeMethod: invoke(JsonNode) }
    │
    ├── LifecycleInterceptor.beforeToolCall("weather_query", args)
    │   └── 可以做日志、鉴权、监控
    │
    ├── weatherTool.invoke({"city":"北京"})  ← 反射调用
    │   └── 执行业务逻辑
    │   └── 返回 ToolResult { success:true, data:{...} }
    │
    ├── LifecycleInterceptor.afterToolCall("weather_query", result)
    │   └── 可以做日志、审计
    │
    ▼
返回 ToolResult → Gateway → Agent → 用户
```

## 7.10 生命周期拦截器

`LifecycleInterceptor` 提供了多个维度的钩子：

```java
public interface LifecycleInterceptor {
    // 聊天生命周期
    default void beforeRequest(ChatRequest request) {}
    default void afterResponse(ChatResponse response) {}

    // 工具注册生命周期
    default void beforeRegisterTool(String toolName) {}
    default void afterRegisterTool(String toolName) {}

    // 工具调用生命周期（Dispatcher 使用）
    default void beforeToolCall(String toolName, JsonNode arguments) {}
    default void afterToolCall(String toolName, ToolResult result) {}

    // Skill 注册生命周期
    default void beforeRegisterSkill(String skillName) {}
    default void afterRegisterSkill(String skillName) {}

    // 回调生命周期
    default void beforeCallback(String callbackType, JsonNode payload) {}
    default void afterCallback(String callbackType, JsonNode payload) {}
}
```

### 使用示例：日志拦截器

```java
@Component
public class ToolLogInterceptor implements LifecycleInterceptor {
    @Override
    public void beforeToolCall(String toolName, JsonNode arguments) {
        log.info("调用工具: {} 参数: {}", toolName, arguments);
    }

    @Override
    public void afterToolCall(String toolName, ToolResult result) {
        log.info("工具 {} 完成, 成功: {}, 耗时: {}ms",
            toolName, result.isSuccess(), result.getExecutionTimeMs());
    }
}
```

## 7.11 Skill 框架（SKILL.md 管理）

除了 Tool（可调用函数）之外，OpenClaw 还有 **Skill** 概念 —— Markdown 指令文件，用于教导 Agent 何时以及如何使用工具。

### SKILL.md 文件格式

```markdown
---
name: weather
description: 天气查询助手，根据用户问题判断是否需要查询天气
user-invocable: true
metadata: {"openclaw": {"requires": {"bins": ["curl"]}}}
---

# Weather Skill

当用户询问天气相关问题时，使用 `weather_query` 工具。

## 使用场景
- 用户问"今天天气怎么样"
- 用户问"北京明天会下雨吗"

## 调用方式
调用 `weather_query` 工具，传入 `city` 参数。
```

### SDK 中的 Skill 管理类

```
openclaw-runtime-skill/
├── model/SkillDefinition.java          ← SKILL.md 解析后的数据模型
├── parser/SkillFrontmatterParser.java  ← 解析 YAML frontmatter + Markdown body
├── loader/SkillLoader.java             ← 加载器接口
├── loader/DefaultSkillLoader.java      ← 从文件系统递归扫描 SKILL.md
├── loader/SkillManager.java            ← 管理器接口
├── loader/DefaultSkillManager.java     ← 内存注册表 + 启用/禁用管理
├── archive/SkillArchiveBuilder.java    ← 将 SKILL.md 打包为 zip 归档
└── registry/
    ├── SkillRegistrationStrategy.java  ← 注册策略接口
    ├── UploadSkillStrategy.java        ← 通过 skills.upload + skills.install RPC 安装
    ├── SkillStatusStrategy.java        ← 查询 skills.status + skills.update 配置
    └── SkillRegistrar.java            ← 编排器（组合策略 + 拦截器链）
```

### Skill 注册到 Gateway

Skill 通过 Gateway 的 WebSocket RPC 协议注册：

```
启动 → OpenClawSkillLifecycleInitializer
       ├── 从 openclaw.skill.directories 扫描 SKILL.md
       ├── SkillFrontmatterParser 解析 frontmatter + body
       ├── DefaultSkillManager 注册到内存
       └── SkillRegistrar.registerToOpenClaw(eligibleSkills)
           ├── UploadSkillStrategy:
           │   ├── SkillArchiveBuilder → zip 字节
           │   ├── skills.upload.begin → uploadId
           │   ├── skills.upload.chunk → 64KB 分块
           │   ├── skills.upload.commit → 验证 SHA-256
           │   └── skills.install({source:"upload"}) → 安装
           └── SkillStatusStrategy:
               └── skills.status → 验证可见性
```

### 配置

```yaml
openclaw:
  # Tool 自动注册（默认开启）
  auto-register-tool: true

  # Skill 自动注册（默认关闭，需要 Gateway 启用 allowUploadedArchives）
  auto-register-skill: false
  skill:
    directories:
      - src/main/resources/skills
```

### Tool 与 Skill 的协作关系

```
┌──────────────────────────────────────────────────────────┐
│                      OpenClaw Gateway                     │
│                                                           │
│  SKILL.md (Skill) ──► 教导 Agent 何时调用 Tool            │
│                                                           │
│  Tool (函数)     ──► Agent 实际执行的操作                  │
│                                                           │
│  例：                                                     │
│    Skill: "当用户问天气时，调用 weather_query 工具"         │
│    Tool:  weather_query.invoke({city:"北京"})              │
└──────────────────────────────────────────────────────────┘
```

## 7.12 MCP 对接现状

### 概念映射

| MCP 协议 | OpenClaw SDK |
|---------|-------------|
| `tools/list` 请求 | `ToolRegistry.buildManifest()` |
| `tools/call` 请求 | `ToolDispatcher.dispatch()` |
| Tool 定义 (JSON Schema) | `ToolDefinition.jsonSchema` |
| Tool Result | `ToolResult` |

### 当前状态

SDK 的数据结构**与 MCP 兼容**，但尚未实现 MCP 传输层：

- ✅ Tool 定义、JSON Schema、调度分发 — 已实现
- ✅ Tool 注册策略（ChatRequest + WebSocket）— 已实现
- ✅ Skill (SKILL.md) 加载、解析、上传注册 — 已实现
- ❌ MCP JSON-RPC 2.0 传输 — 未实现
- ❌ MCP `initialize` 握手 — 未实现
- ❌ MCP `capabilities` 协商 — 未实现

**架构已 MCP-ready**：未来只需添加 MCP 传输层即可。

## 7.13 关闭时清理

```java
// Tool 关闭
public class OpenClawShutdownHandler implements DisposableBean {
    @Override
    public void destroy() {
        // 1. 关闭所有活跃会话
        // 2. 从 Gateway 注销工具
        toolRegistrar.unregisterFromOpenClaw(toolNames);
        // 3. 停止心跳
        heartbeatManager.shutdown();
        // 4. 发布 RuntimeStoppedEvent
        eventPublisher.publish(new RuntimeStoppedEvent());
    }
}

// Skill 关闭
public class OpenClawSkillShutdownHandler implements DisposableBean {
    @Override
    public void destroy() {
        // 通过 skills.update config 模式禁用已注册的 Skill
        skillRegistrar.unregisterFromOpenClaw(skillNames);
    }
}
```

## 7.14 小结

| 知识点 | 要点 |
|--------|------|
| Tool vs Skill | Tool = 可调用函数，Skill = SKILL.md 指令文件 |
| @OpenClawTool | 注解 + @Component，一步到位 |
| Tool 接口 | `invoke(JsonNode)` 单方法 |
| 扫描 | `getBeansWithAnnotation` 自动发现 |
| JSON Schema | 反射生成，Java 类型映射 |
| 注册策略 | ChatRequest 注入 + WebSocket RPC 验证 |
| 调度 | 反射调用 + 拦截器链 |
| Skill 管理 | SKILL.md 解析 → zip 打包 → Gateway 上传安装 |
| MCP | 数据结构兼容，传输层待实现 |

**下一章**：[第8章：Spring Boot 自动配置实战](08-Spring-Boot-自动配置实战.md) — 把一切粘合在一起。
