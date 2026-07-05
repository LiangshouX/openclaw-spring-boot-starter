# 第7章：Skill 技能框架与 MCP 对接

> **学习目标**：理解注解驱动的技能（Skill）系统：从定义、扫描、注册到调度的完整生命周期。

---

## 7.1 Skill 是什么？

Skill 是你自己编写的工具函数，注册到 Gateway 后，Agent 可以像调用内置工具一样调用它：

```
用户："帮我查一下今天的天气"
       │
       ▼
   Agent 推理：需要调用 weather_query 工具
       │
       ▼
   Gateway 发送 tool_call ──► SDK ──► SkillDispatcher
                                         │
                                    找到 WeatherSkill
                                         │
                                    调用 invoke(arguments)
                                         │
                                    返回 SkillResult
                                         │
       ◄── 天气数据返回 ◄─────────────────┘
       │
   Agent 生成回复："今天北京晴，25°C"
```

## 7.2 定义一个 Skill

### 注解 + 接口

```java
// 1. 用 @OpenClawSkill 注解标记
@OpenClawSkill(
    name = "weather_query",
    description = "查询指定城市的天气信息",
    version = "1.0"
)
public class WeatherSkill implements Skill {

    // 2. 实现 invoke 方法
    @Override
    public SkillResult invoke(JsonNode arguments) {
        String city = arguments.path("city").asText("北京");

        // 调用天气 API
        String weather = weatherService.query(city);

        // 返回结果
        JsonNode data = objectMapper.createObjectNode()
            .put("city", city)
            .put("weather", weather);

        return SkillResult.success("weather_query", data);
    }
}
```

### @OpenClawSkill 注解

```java
@Target(ElementType.TYPE)         // 只能注解类
@Retention(RetentionPolicy.RUNTIME) // 运行时可读
@Component                        // ← 自动成为 Spring Bean！
public @interface OpenClawSkill {
    String name();                    // 技能唯一名称
    String description() default "";  // 描述（Agent 用来决定是否调用）
    String version() default "1.0";   // 版本号
}
```

> **设计巧妙之处**：`@OpenClawSkill` 上标注了 `@Component`，所以你不需要再写 `@Service` 或 `@Component`。一个注解搞定一切。

### Skill 接口

```java
public interface Skill {
    SkillResult invoke(JsonNode arguments);
}
```

极简的单方法接口。`arguments` 是 Gateway 传来的 JSON 参数。

### SkillResult

```java
@Data @Builder
public class SkillResult {
    private String skillName;
    private boolean success;
    private JsonNode data;          // 成功时的输出
    private String errorMessage;    // 失败时的错误信息
    private long executionTimeMs;

    // 工厂方法
    public static SkillResult success(String name, JsonNode data) { ... }
    public static SkillResult failure(String name, String error) { ... }
}
```

## 7.3 Skill 生命周期全景

```
Spring Boot 启动
    │
    ▼
┌─ OpenClawLifecycleInitializer.run() ─────────────────────┐
│                                                            │
│  ① 扫描 ── SkillScanner.scan(context)                    │
│     └─ context.getBeansWithAnnotation(@OpenClawSkill)     │
│     └─ 找到所有 @OpenClawSkill 注解的 Bean                 │
│                                                            │
│  ② 构建元数据 ── SkillMetadataBuilder.build(bean, anno)   │
│     └─ 提取 name, description, version                    │
│     └─ 定位 invoke(JsonNode) 方法                          │
│     └─ 生成 SkillMetadata                                 │
│                                                            │
│  ③ 本地注册 ── SkillRegistry.registerAll(metadataList)    │
│     └─ 存入 ConcurrentHashMap<String, SkillMetadata>      │
│                                                            │
│  ④ 生成 Schema ── JsonSchemaGenerator.generate(class)     │
│     └─ 从类字段推导 JSON Schema                            │
│     └─ 设置到 definition.jsonSchema                       │
│                                                            │
│  ⑤ 远程注册 ── SkillRegistrar.registerToOpenClaw(manifest)│
│     └─ 将 SkillManifest 发送给 Gateway                    │
│                                                            │
│  ⑥ 发布事件 ── EventPublisher.publish(RuntimeStartedEvent)│
│                                                            │
└────────────────────────────────────────────────────────────┘
```

## 7.4 扫描：SkillScanner

```java
public class SkillScanner {
    private final SkillMetadataBuilder metadataBuilder;

    public List<SkillMetadata> scan(ApplicationContext context) {
        List<SkillMetadata> metadataList = new ArrayList<>();

        // 利用 Spring 的注解发现机制
        Map<String, Object> beans =
            context.getBeansWithAnnotation(OpenClawSkill.class);

        for (Object bean : beans.values()) {
            OpenClawSkill annotation =
                bean.getClass().getAnnotation(OpenClawSkill.class);

            if (annotation == null) continue;  // CGLIB 代理保护

            SkillMetadata metadata =
                metadataBuilder.build(bean, annotation);
            metadataList.add(metadata);

            log.info("发现技能: name={}, class={}",
                annotation.name(), bean.getClass().getName());
        }
        return metadataList;
    }
}
```

## 7.5 元数据构建：SkillMetadataBuilder

```java
public SkillMetadata build(Object bean, OpenClawSkill annotation) {
    // 1. 从注解提取定义
    SkillDefinition definition = SkillDefinition.builder()
        .name(annotation.name())
        .description(annotation.description())
        .version(annotation.version())
        .className(bean.getClass().getName())
        .build();

    // 2. 定位 invoke 方法
    Method invokeMethod = findInvokeMethod(bean.getClass());

    // 3. 组装元数据
    return SkillMetadata.builder()
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

Gateway 需要知道每个 Skill 接受什么参数。SDK 通过反射自动生成 JSON Schema：

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

## 7.7 注册中心：SkillRegistry

```java
public class SkillRegistry {
    private final ConcurrentHashMap<String, SkillMetadata> skills =
        new ConcurrentHashMap<>();

    public void register(SkillMetadata metadata) {
        skills.put(metadata.getDefinition().getName(), metadata);
        metadata.setRegistered(true);
    }

    public SkillMetadata get(String skillName) {
        return skills.get(skillName);
    }

    public SkillManifest buildManifest() {
        List<SkillDefinition> definitions = skills.values().stream()
            .map(SkillMetadata::getDefinition)
            .collect(Collectors.toList());

        return SkillManifest.builder()
            .version("1.0")
            .generatedAt(Instant.now())
            .skills(definitions)
            .build();
    }
}
```

## 7.8 调度分发：SkillDispatcher

当 Gateway 回调 Skill 时，Dispatcher 负责找到对应的 Bean 并执行：

```java
public SkillResult dispatch(String skillName, JsonNode arguments) {
    // 1. 查找技能
    SkillMetadata metadata = skillRegistry.get(skillName);
    if (metadata == null) {
        throw new SkillException(ErrorCode.SKILL_NOT_FOUND,
            "未找到技能: " + skillName);
    }

    try {
        // 2. 前置拦截器
        for (LifecycleInterceptor interceptor : interceptors) {
            interceptor.beforeToolCall(skillName, arguments);
        }

        // 3. 反射调用
        Object result = metadata.getInvokeMethod()
            .invoke(metadata.getTargetBean(), arguments);

        SkillResult skillResult = (SkillResult) result;

        // 4. 后置拦截器
        for (LifecycleInterceptor interceptor : interceptors) {
            interceptor.afterToolCall(skillName, skillResult);
        }

        return skillResult;
    } catch (Exception e) {
        throw new SkillException(ErrorCode.SKILL_INVOCATION_FAILED,
            "技能调用失败: " + skillName, e);
    }
}
```

### 调度流程图

```
Gateway 回调
    │
    ▼
SkillDispatcher.dispatch("weather_query", {"city":"北京"})
    │
    ├── SkillRegistry.get("weather_query")
    │   └── 返回 SkillMetadata { targetBean: weatherSkill,
    │                             invokeMethod: invoke(JsonNode) }
    │
    ├── LifecycleInterceptor.beforeToolCall("weather_query", args)
    │   └── 可以做日志、鉴权、监控
    │
    ├── weatherSkill.invoke({"city":"北京"})  ← 反射调用
    │   └── 执行业务逻辑
    │   └── 返回 SkillResult { success:true, data:{...} }
    │
    ├── LifecycleInterceptor.afterToolCall("weather_query", result)
    │   └── 可以做日志、审计
    │
    ▼
返回 SkillResult → Gateway → Agent → 用户
```

## 7.9 生命周期拦截器

`LifecycleInterceptor` 提供了四个维度的钩子：

```java
public interface LifecycleInterceptor {
    // 聊天生命周期
    default void beforeRequest(ChatRequest request) {}
    default void afterResponse(ChatResponse response) {}

    // 技能注册生命周期
    default void beforeRegisterSkill(String skillName) {}
    default void afterRegisterSkill(String skillName) {}

    // 工具调用生命周期（Dispatcher 使用）
    default void beforeToolCall(String skillName, JsonNode arguments) {}
    default void afterToolCall(String skillName, SkillResult result) {}

    // 回调生命周期
    default void beforeCallback(String callbackType, JsonNode payload) {}
    default void afterCallback(String callbackType, JsonNode payload) {}
}
```

### 使用示例：日志拦截器

```java
@Component
public class SkillLogInterceptor implements LifecycleInterceptor {
    @Override
    public void beforeToolCall(String skillName, JsonNode arguments) {
        log.info("调用技能: {} 参数: {}", skillName, arguments);
    }

    @Override
    public void afterToolCall(String skillName, SkillResult result) {
        log.info("技能 {} 完成, 成功: {}, 耗时: {}ms",
            skillName, result.isSuccess(), result.getExecutionTimeMs());
    }
}
```

## 7.10 MCP 对接现状

### 概念映射

| MCP 协议 | OpenClaw SDK |
|---------|-------------|
| `tools/list` 请求 | `SkillRegistry.buildManifest()` |
| `tools/call` 请求 | `SkillDispatcher.dispatch()` |
| Tool 定义 (JSON Schema) | `SkillDefinition.jsonSchema` |
| Tool Result | `SkillResult` |

### 当前状态

SDK 的数据结构**与 MCP 兼容**，但尚未实现 MCP 传输层：

- ✅ Skill 定义、JSON Schema、调度分发 — 已实现
- ❌ MCP JSON-RPC 2.0 传输 — 未实现
- ❌ MCP `initialize` 握手 — 未实现
- ❌ MCP `capabilities` 协商 — 未实现

**架构已 MCP-ready**：未来只需添加 MCP 传输层即可。

## 7.11 关闭时清理

```java
public class OpenClawShutdownHandler implements DisposableBean {
    @Override
    public void destroy() {
        // 1. 关闭所有活跃会话
        // 2. 从 Gateway 注销技能
        skillRegistrar.unregisterFromOpenClaw(skillNames);
        // 3. 停止心跳
        heartbeatManager.shutdown();
        // 4. 发布 RuntimeStoppedEvent
        eventPublisher.publish(new RuntimeStoppedEvent());
    }
}
```

## 7.12 小结

| 知识点 | 要点 |
|--------|------|
| @OpenClawSkill | 注解 + @Component，一步到位 |
| Skill 接口 | `invoke(JsonNode)` 单方法 |
| 扫描 | `getBeansWithAnnotation` 自动发现 |
| JSON Schema | 反射生成，Java 类型映射 |
| 注册 | 本地 Registry + 远程 Gateway |
| 调度 | 反射调用 + 拦截器链 |
| MCP | 数据结构兼容，传输层待实现 |

**下一章**：[第8章：Spring Boot 自动配置实战](08-Spring-Boot-自动配置实战.md) — 把一切粘合在一起。
