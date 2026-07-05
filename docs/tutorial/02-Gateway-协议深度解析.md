# 第2章：Gateway 协议深度解析

> **学习目标**：掌握 OpenClaw Gateway 的 WebSocket 协议和 HTTP 端点，理解帧格式、握手流程、RPC 调用机制。

---

## 2.1 协议总览

Gateway 在同一端口上多路复用两种协议：

```
┌──────────────────────────────────────────────┐
│           Gateway 端口（例如 13015）           │
│                                               │
│   WebSocket ──── JSON 文本帧（RPC + 事件）     │
│   HTTP      ──── REST API（OpenAI 兼容）       │
│                                               │
│   同一个端口，通过协议自动区分                   │
└──────────────────────────────────────────────┘
```

## 2.2 WebSocket 帧格式

WebSocket 通信基于 **JSON 文本帧**。每个帧都有一个 `type` 字段标识类型：

### 请求帧（Client → Server）

```json
{
  "type": "req",
  "id": "42",
  "method": "sessions.get",
  "params": { "key": "agent:main:main" }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | 固定为 `"req"` |
| `id` | string | 请求唯一 ID，用于关联响应 |
| `method` | string | RPC 方法名 |
| `params` | object | 方法参数 |

### 响应帧（Server → Client）

```json
{
  "type": "res",
  "id": "42",
  "ok": true,
  "payload": { "sessionKey": "agent:main:main", "status": "active" }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | 固定为 `"res"` |
| `id` | string | 对应请求的 ID |
| `ok` | boolean | 是否成功 |
| `payload` | object | 成功时的响应数据 |
| `error` | object | 失败时的错误信息 `{message, code}` |

### 事件帧（Server → Client）

```json
{
  "type": "event",
  "event": "chat",
  "payload": { "deltaText": "Hello World", "type": "text" }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | 固定为 `"event"` |
| `event` | string | 事件类型名称 |
| `payload` | object | 事件数据 |

## 2.3 握手流程

WebSocket 连接建立后，需要完成三步握手才能开始 RPC 通信：

```
Client                                    Gateway
  │                                          │
  │  ═══ WebSocket TCP 连接建立 ═══          │
  │                                          │
  │  ◄──── ① connect.challenge 事件          │
  │        {type:"event",                    │
  │         event:"connect.challenge",       │
  │         payload:{nonce:"abc", ts:...}}   │
  │                                          │
  │  ──── ② connect 请求 ───►                │
  │        {type:"req",                      │
  │         method:"connect",                │
  │         params:{...认证信息...}}          │
  │                                          │
  │  ◄──── ③ hello-ok 响应                   │
  │        {type:"res", ok:true,             │
  │         payload:{                        │
  │           server:{version, connId},      │
  │           auth:{role, scopes},           │
  │           policy:{tickIntervalMs}}}      │
  │                                          │
  │  ═══ 握手完成，开始正常 RPC 通信 ═══      │
```

### 步骤①：connect.challenge

Gateway 在连接建立后立即发送一个挑战事件，包含一个随机 nonce：

```json
{
  "type": "event",
  "event": "connect.challenge",
  "payload": {
    "nonce": "random-string-123",
    "ts": 1719648000000
  }
}
```

### 步骤②：connect 请求

客户端发送认证请求。**这个请求的格式非常严格**，Gateway 会通过 JSON Schema 校验每个字段：

```json
{
  "type": "req",
  "id": "c1",
  "method": "connect",
  "params": {
    "minProtocol": 4,
    "maxProtocol": 4,
    "client": {
      "id": "cli",
      "version": "1.0.0",
      "platform": "java",
      "mode": "cli"
    },
    "role": "operator",
    "scopes": ["operator.read", "operator.write"],
    "auth": { "token": "your-gateway-token" },
    "caps": [],
    "commands": [],
    "permissions": {}
  }
}
```

### client.id 和 client.mode 的枚举约束

Gateway 对这两个字段进行严格枚举校验，**使用非法值会直接被拒绝**：

| 字段 | 合法值 | 说明 |
|------|--------|------|
| `client.id` | `cli` | CLI 客户端 |
|  | `ios-node` | iOS 设备节点 |
|  | `gateway-client` | 内部后端（保留） |
|  | `openclaw-macos` | macOS 原生应用 |
| `client.mode` | `ui` | 原生 UI 客户端 |
|  | `webchat` | Web 聊天 |
|  | `cli` | CLI 模式 |
|  | `backend` | 后端模式 |
|  | `probe` | 探测模式 |
|  | `test` | 测试模式 |
|  | `node` | 设备节点模式 |

> **踩坑记录**：我们最初使用 `client.id: "openclaw-sdk"`，被 Gateway 直接拒绝。必须使用枚举中的值。

### 步骤③：hello-ok

Gateway 验证通过后返回：

```json
{
  "type": "res",
  "id": "c1",
  "ok": true,
  "payload": {
    "type": "hello-ok",
    "protocol": 4,
    "server": { "version": "2026.5.22", "connId": "conn-abc123" },
    "auth": { "role": "operator", "scopes": [] },
    "policy": { "tickIntervalMs": 30000, "maxPayload": 26214400 }
  }
}
```

> **注意 `scopes: []`**：无设备配对时，WebSocket 连接获得的作用域为空。这是第3章会详细讨论的关键问题。

## 2.4 RPC 调用

握手完成后，客户端可以发送 RPC 请求：

### 请求-响应关联

```
Client                              Gateway
  │                                    │
  │  ── {id:"1", method:"health"} ──►  │
  │                                    │
  │  ── {id:"2", method:"sessions.get",│
  │       params:{key:"main"}} ──►     │
  │                                    │
  │  ◄── {id:"2", ok:true, payload}    │  ← 先收到 id:2 的响应
  │                                    │
  │  ◄── {id:"1", ok:true, payload}    │  ← 再收到 id:1 的响应
  │                                    │
```

**关键点**：响应可能乱序到达，必须通过 `id` 字段关联请求和响应。

### 常用 RPC 方法速查表

```
┌──────────────────┬────────────────────────────┬──────────────┐
│ 方法名            │ 参数                        │ 说明          │
├──────────────────┼────────────────────────────┼──────────────┤
│ health           │ {}                          │ 健康检查       │
│ sessions.create  │ {workspaceId}               │ 创建会话       │
│ sessions.get     │ {key}                       │ 获取会话       │
│ sessions.delete  │ {key}                       │ 删除会话       │
│ sessions.describe│ {key}                       │ 会话详情（心跳）│
│ tasks.get        │ {taskId}                    │ 获取任务       │
│ tasks.list       │ {sessionKey}                │ 列出任务       │
│ tasks.cancel     │ {taskId}                    │ 取消任务       │
│ chat.send        │ {message, sessionKey, ...}   │ 发送聊天      │
│ chat.history     │ {sessionKey}                │ 聊天历史       │
│ artifacts.get    │ {artifactId}                │ 获取制品       │
│ artifacts.list   │ {sessionKey}                │ 列出制品       │
│ artifacts.download│ {artifactId}               │ 下载制品       │
│ system-event     │ {sessionKey, event}         │ 发布系统事件   │
│ tick             │ {}                          │ 心跳保活       │
└──────────────────┴────────────────────────────┴──────────────┘
```

## 2.5 事件推送

Gateway 会主动推送事件给所有连接的客户端：

```
┌──────────────┬───────────────────────────────────┐
│ 事件类型      │ 说明                                │
├──────────────┼───────────────────────────────────┤
│ chat         │ 聊天文本增量 {deltaText, type}      │
│ agent        │ Agent 状态 {status:"ok"/"error"}   │
│ tick         │ 服务端心跳                           │
│ shutdown     │ Gateway 即将关闭                     │
│ system-      │ 在线状态更新                         │
│   presence   │ {instanceId, mode, host}            │
└──────────────┴───────────────────────────────────┘
```

### chat.send 的特殊行为

`chat.send` 是一个**非阻塞** RPC：

```
Client                              Gateway
  │                                    │
  │  ── chat.send ──►                  │
  │  {message:"你好", sessionKey:"main"}│
  │                                    │
  │  ◄── {runId:"r1", status:"started"}│  ← 立即返回 ack
  │                                    │
  │  ◄── event:chat                    │  ← 文本增量推送
  │  {deltaText:"你"}                   │
  │                                    │
  │  ◄── event:chat                    │
  │  {deltaText:"好！"}                 │
  │                                    │
  │  ◄── event:chat                    │
  │  {deltaText:"有什么可以帮你的？"}    │
  │                                    │
  │  ◄── event:agent                   │  ← 完成信号
  │  {status:"ok", runId:"r1"}         │
```

这意味着 `invoke("chat.send")` 返回的只是 ack，实际响应需要通过**事件订阅**获取。

## 2.6 心跳保活

Gateway 在 `hello-ok` 中声明 `tickIntervalMs`（通常 30000ms）。客户端应在**一半间隔**时发送心跳：

```
Client                              Gateway
  │                                    │
  │  ── {type:"req", method:"tick"} ──►│  ← 每 15 秒发送
  │                                    │
  │  ◄── event:tick                    │  ← 服务端心跳
  │                                    │
```

如果客户端长时间不发心跳，Gateway 可能断开连接。

## 2.7 HTTP 端点

### OpenAI 兼容端点

Gateway 提供 OpenAI 兼容的 HTTP 端点（**默认关闭**）：

| 端点 | 方法 | 说明 |
|------|------|------|
| `/v1/chat/completions` | POST | 聊天（支持 SSE 流式） |
| `/v1/responses` | POST | OpenAI Responses API |
| `/v1/models` | GET | 列出可用 Agent |
| `/v1/models/{id}` | GET | 获取单个 Agent |
| `/v1/embeddings` | POST | 文本嵌入 |

**启用方式**（Gateway 配置）：

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

### 请求格式（OpenAI 标准）

```bash
curl -X POST http://gateway:13015/v1/chat/completions \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "openclaw/default",
    "messages": [{"role": "user", "content": "你好"}],
    "stream": false
  }'
```

### Session 路由

HTTP 端点通过以下方式控制 Session：

```bash
# 方式1：x-openclaw-session-key 请求头
-H "x-openclaw-session-key: my-session"

# 方式2：OpenAI user 字段
-d '{"user": "my-session", ...}'
```

### SSE 流式响应

设置 `stream: true` 时返回 Server-Sent Events：

```
data: {"choices":[{"delta":{"content":"你"}}]}

data: {"choices":[{"delta":{"content":"好"}}]}

data: {"choices":[{"delta":{"content":"！"}}]}

data: [DONE]
```

## 2.8 协议版本号

当前 Gateway 要求 **Protocol v4**：

```json
{
  "minProtocol": 4,
  "maxProtocol": 4
}
```

如果客户端声明的协议版本不在 Gateway 支持范围内，握手会失败。

## 2.9 小结

| 知识点 | 要点 |
|--------|------|
| 帧类型 | `req`（请求）、`res`（响应）、`event`（事件） |
| 握手 | challenge → connect → hello-ok |
| 枚举约束 | `client.id` 和 `client.mode` 必须使用合法枚举值 |
| RPC 关联 | 通过 `id` 字段关联请求和响应 |
| 心跳 | 在 `tickIntervalMs / 2` 间隔发送 `tick` |
| HTTP 端点 | 默认关闭，需显式启用 |
| chat.send | 非阻塞，实际响应通过事件推送 |

**下一章**：[第3章：认证、权限与作用域](03-认证权限与作用域.md) — 理解 Token 认证、设备配对和 Scope 机制。
