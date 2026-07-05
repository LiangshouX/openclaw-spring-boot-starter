# 第6章：HTTP 通信层实现

> **学习目标**：实现基于 HTTP 的聊天客户端（OpenAI 兼容）和文件上传客户端，理解 SSE 流式解析。

---

## 6.1 为什么需要 HTTP 层？

回顾第3章的结论：WebSocket 连接在没有设备配对时 `scopes=[]`，无法执行写操作。HTTP 端点使用 Token 认证时自动获得完整 Scope。

```
写操作（chat.send）──► HTTP /v1/chat/completions  ← 完整 Scope
读操作（sessions.get）──► WebSocket RPC              ← 基础连接即可
```

## 6.2 ChatClient 设计

### 请求格式（OpenAI 标准）

```json
{
  "model": "openclaw/default",
  "stream": false,
  "messages": [
    { "role": "user", "content": "你好" }
  ],
  "user": "my-session-key"
}
```

### 响应格式

```json
{
  "id": "chatcmpl-abc123",
  "model": "openclaw/default",
  "choices": [{
    "message": {
      "role": "assistant",
      "content": "你好！有什么可以帮你的？"
    },
    "finish_reason": "stop"
  }]
}
```

## 6.3 同步调用实现

```java
public class ChatClient {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ChatResponse sendMessage(ChatRequest request) {
        // 1. 构建 OpenAI 格式的请求体
        Map<String, Object> body = buildRequestBody(request, false);

        // 2. 发送 HTTP POST
        JsonNode response = webClient.post()
            .uri("/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .headers(h -> addSessionHeaders(h, request))
            .bodyValue(body)
            .retrieve()
            // 3. 错误处理（含启用提示）
            .onStatus(status -> status.isError(),
                clientResponse -> clientResponse.bodyToMono(String.class)
                    .map(errorBody -> new ClientException(
                        ErrorCode.HTTP_ERROR,
                        "错误: " + errorBody
                        + " (提示: 确保 chatCompletions.enabled=true)")))
            .bodyToMono(JsonNode.class)
            .block();

        // 4. 解析响应
        return parseChatResponse(response, request);
    }
}
```

### 构建请求体

```java
private Map<String, Object> buildRequestBody(ChatRequest request, boolean stream) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", "openclaw/default");  // Agent 路由目标
    body.put("stream", stream);

    List<Map<String, String>> messages = new ArrayList<>();
    messages.add(Map.of("role", "user", "content", request.getMessage()));
    body.put("messages", messages);

    // OpenAI user 字段用于 session 路由
    String sessionKey = resolveSessionKey(request);
    if (sessionKey != null) {
        body.put("user", sessionKey);
    }

    return body;
}
```

### Session 路由请求头

```java
private void addSessionHeaders(HttpHeaders headers, ChatRequest request) {
    String sessionKey = resolveSessionKey(request);
    if (sessionKey != null) {
        headers.set("x-openclaw-session-key", sessionKey);
    }
}

private String resolveSessionKey(ChatRequest request) {
    if (request.getSessionId() != null) return request.getSessionId();
    if (request.getConversationId() != null) return request.getConversationId();
    return null;  // 让 Gateway 自动生成
}
```

### 解析响应

```java
private ChatResponse parseChatResponse(JsonNode response, ChatRequest request) {
    JsonNode firstChoice = response.path("choices").get(0);
    JsonNode message = firstChoice.path("message");

    // 解析 tool_calls（如果有）
    List<ChatResponse.ToolCall> toolCalls = new ArrayList<>();
    JsonNode tcNode = message.path("tool_calls");
    if (tcNode.isArray()) {
        for (JsonNode tc : tcNode) {
            JsonNode fn = tc.path("function");
            toolCalls.add(ChatResponse.ToolCall.builder()
                .id(tc.path("id").asText(""))
                .skillName(fn.path("name").asText(""))
                .arguments(objectMapper.readTree(
                    fn.path("arguments").asText("{}")))
                .build());
        }
    }

    return ChatResponse.builder()
        .requestId(response.path("id").asText(""))
        .sessionId(request.getSessionId())
        .content(message.path("content").asText(""))
        .toolCalls(toolCalls)
        .metadata(Map.of(
            "finish_reason", firstChoice.path("finish_reason").asText(""),
            "model", response.path("model").asText("")))
        .timestamp(Instant.now())
        .build();
}
```

## 6.4 SSE 流式调用实现

### SSE 协议

Server-Sent Events 是一种基于 HTTP 的单向推送协议：

```
HTTP 响应头：Content-Type: text/event-stream

data: {"choices":[{"delta":{"content":"你"}}]}
                                        ← 空行分隔
data: {"choices":[{"delta":{"content":"好"}}]}

data: {"choices":[{"delta":{"content":"！"}}]}

data: [DONE]                            ← 结束标记
```

### 流式实现

```java
public Flux<String> streamMessage(ChatRequest request) {
    Map<String, Object> body = buildRequestBody(request, true);

    return webClient.post()
        .uri("/v1/chat/completions")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .bodyValue(body)
        .retrieve()
        .bodyToFlux(String.class)           // 每行作为一个 String
        .filter(line -> !line.isBlank()
            && !"[DONE]".equals(line.trim()))  // 过滤空行和结束标记
        .mapNotNull(this::extractDeltaContent)  // 提取 delta.content
        .filter(delta -> !delta.isEmpty());
}

private String extractDeltaContent(String sseLine) {
    try {
        String json = sseLine.startsWith("data:")
            ? sseLine.substring(5).trim()
            : sseLine.trim();

        JsonNode node = objectMapper.readTree(json);
        JsonNode content = node.path("choices").path(0)
            .path("delta").path("content");

        return content.isMissingNode() ? null : content.asText("");
    } catch (Exception e) {
        return null;  // 解析失败，跳过
    }
}
```

### 数据流

```
HTTP Response Stream
    │
    ▼
bodyToFlux(String)
    │ "data: {json1}"
    │ "data: {json2}"
    │ ""
    │ "data: [DONE]"
    │
    ▼ filter（去空行、去 [DONE]）
    │ "data: {json1}"
    │ "data: {json2}"
    │
    ▼ mapNotNull（提取 delta.content）
    │ "你"
    │ "好"
    │
    ▼ filter（去空字符串）
    │
Flux<String> → 业务代码逐块消费
```

## 6.5 UploadClient 实现

文件上传使用 HTTP Multipart：

```java
public class UploadClient {
    private final WebClient webClient;

    public JsonNode upload(String sessionId, String fileName,
                           byte[] content, String contentType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(content) {
                @Override
                public String getFilename() { return fileName; }
            })
            .contentType(MediaType.parseMediaType(contentType));

        if (sessionId != null) {
            builder.part("sessionKey", sessionId);
        }

        return webClient.post()
            .uri("/v1/files")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .bodyValue(builder.build())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    }
}
```

## 6.6 WebClient 配置

WebClient 在自动配置中创建，携带认证头：

```java
@Bean
public WebClient openClawWebClient(OpenClawProperties properties) {
    return WebClient.builder()
        .baseUrl(properties.getEndpoint())
        .defaultHeader("Authorization",
            "Bearer " + properties.getToken())
        .build();
}
```

## 6.7 错误处理策略

| HTTP 状态码 | 含义 | SDK 处理 |
|------------|------|---------|
| 200 | 成功 | 正常解析 |
| 400 | 请求格式错误 | `ClientException(HTTP_ERROR)` |
| 401 | 认证失败 | `ClientException(AUTHENTICATION_FAILED)` |
| 403 | 权限不足 | `ClientException(HTTP_ERROR)` |
| 404 | 端点不存在 | 提示启用 `chatCompletions.enabled` |
| 429 | 请求过快 | 提示限流 |
| 500+ | 服务端错误 | `ClientException(HTTP_ERROR)` |

## 6.8 小结

| 知识点 | 要点 |
|--------|------|
| OpenAI 兼容 | `model: "openclaw/default"` 路由到 Agent |
| Session 路由 | `x-openclaw-session-key` 头或 `user` 字段 |
| SSE 流式 | `bodyToFlux` + `filter` + `mapNotNull` |
| 文件上传 | Multipart POST `/v1/files` |
| 错误提示 | 404 时提示用户启用端点 |

**下一章**：[第7章：Skill 技能框架与 MCP 对接](07-Skill-技能框架与MCP对接.md) — 实现注解驱动的技能注册和调度系统。
