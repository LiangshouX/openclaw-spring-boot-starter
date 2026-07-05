# 第1章：OpenClaw 平台概览与核心概念

> **学习目标**：理解 OpenClaw 平台的核心组件和它们之间的关系，为后续的协议学习和 SDK 开发打下基础。

---

## 1.1 OpenClaw 是什么？

OpenClaw 是一个 **AI 驱动的对话与任务执行平台**。你可以把它理解为一个"AI 中枢"：

- 它连接各种 AI 模型（OpenAI、Anthropic、本地模型等）
- 它管理对话会话、记忆、工具调用
- 它可以接入多种消息渠道（Discord、Telegram、iMessage 等）
- 它支持注册自定义技能（Skill），让 AI 能调用你的代码

如果你用过 OpenClaw 的 CLI 或 macOS App 跟 Agent 对话，你就已经在跟 OpenClaw 的 **Gateway** 交互了。

## 1.2 核心组件一览

```
┌─────────────────────────────────────────────────────────────┐
│                    OpenClaw 平台架构                          │
│                                                              │
│   ┌──────────┐    ┌──────────┐    ┌──────────┐             │
│   │  CLI     │    │ macOS App│    │ WebChat  │   ← 客户端   │
│   └────┬─────┘    └────┬─────┘    └────┬─────┘             │
│        │               │               │                    │
│        └───────────────┼───────────────┘                    │
│                        │ WebSocket / HTTP                    │
│                        ▼                                     │
│              ┌─────────────────┐                             │
│              │    Gateway      │   ← 核心网关                 │
│              │  (单进程服务)    │                             │
│              └────────┬────────┘                             │
│                       │                                      │
│         ┌─────────────┼─────────────┐                       │
│         ▼             ▼             ▼                        │
│   ┌──────────┐  ┌──────────┐  ┌──────────┐                 │
│   │  Agent   │  │ Channel  │  │   Node   │                 │
│   │  (AI)    │  │ (消息渠道)│  │ (设备节点)│                  │
│   └──────────┘  └──────────┘  └──────────┘                 │
└─────────────────────────────────────────────────────────────┘
```

## 1.3 Gateway — 核心网关

**Gateway 是 OpenClaw 的心脏。** 它是一个单进程服务，负责：

- 接收所有客户端的连接（CLI、App、WebChat、你的 SDK）
- 路由消息到正确的 Agent
- 管理会话（Session）的生命周期
- 协调工具调用（Tool Call）
- 推送事件到订阅者

### Gateway 的通信方式

Gateway 在**同一个端口**上同时提供两种通信方式：

| 通信方式 | 用途 | 协议 |
|---------|------|------|
| **WebSocket** | 核心控制面：会话管理、任务、事件、心跳 | JSON 文本帧 |
| **HTTP** | OpenAI 兼容端点、文件上传、健康检查 | REST API |

```
Gateway (端口 13015)
├── WebSocket  ─── 控制面 RPC（sessions.*, tasks.*, chat.send, ...）
├── HTTP       ─── /v1/chat/completions（OpenAI 兼容，默认关闭）
├── HTTP       ─── /v1/models, /v1/embeddings
├── HTTP       ─── /v1/files（文件上传）
└── HTTP       ─── /health（健康检查）
```

> **重点记忆**：WebSocket 是 Gateway 的"原生语言"，所有核心功能都通过 WebSocket RPC 实现。HTTP 端点是兼容性层。

## 1.4 Agent — AI 代理

Agent 是 OpenClaw 中执行对话的"大脑"。每个 Agent 配置了：

- **模型（Model）**：使用哪个 AI 模型（GPT-4、Claude、本地模型等）
- **指令（Instructions）**：Agent 的行为规则和人设
- **工具（Tools）**：Agent 可以调用的技能（Skill）
- **记忆（Memory）**：Agent 的长期记忆

你可以通过 `model: "openclaw/default"` 来路由到默认 Agent，也可以指定具体的 Agent ID。

## 1.5 Session — 会话

Session 是一次对话的上下文容器。它保存了：

- 对话历史（消息列表）
- Agent 状态
- 关联的任务和制品

### Session Key

每个 Session 有一个唯一的 **Session Key**，格式通常是 `agent:<workspace>:<sessionName>`：

```
agent:main:main          ← 默认会话
agent:main:dev-chat      ← 自定义会话
```

在 HTTP 端点中，你可以通过 `x-openclaw-session-key` 请求头或 `user` 字段来指定 Session。

## 1.6 Channel — 消息渠道

Channel 是 OpenClaw 连接外部消息平台的插件：

```
OpenClaw Gateway
├── Discord Channel    ← 连接 Discord 服务器
├── Telegram Channel   ← 连接 Telegram Bot
├── Slack Channel      ← 连接 Slack Workspace
├── iMessage Channel   ← 连接 iMessage
├── WebChat            ← 内置 Web 聊天界面
└── 自定义 Channel     ← 你可以通过插件开发
```

当用户在 Discord 中 @Bot 时，消息通过 Discord Channel 进入 Gateway，路由到 Agent，回复再通过 Channel 发回 Discord。

## 1.7 Node — 设备节点

Node 是连接到 Gateway 的物理设备，它可以暴露硬件能力：

- **macOS Node**：屏幕录制、摄像头、系统命令执行
- **iOS Node**：摄像头、位置信息
- **Headless Node**：无头服务器，执行自动化任务

Node 通过 WebSocket 连接 Gateway，需要 **设备配对（Device Pairing）** 来获得权限。

## 1.8 Skill — 技能

Skill 是你自己编写的函数，注册到 Gateway 后，Agent 可以像调用工具一样调用它：

```java
@OpenClawSkill(name = "search_docs", description = "搜索文档库")
public class SearchDocsSkill implements Skill {
    @Override
    public SkillResult invoke(JsonNode arguments) {
        String query = arguments.path("query").asText();
        // 执行搜索逻辑
        return SkillResult.success("search_docs", resultJson);
    }
}
```

Skill 的生命周期：

```
1. 定义 → 2. 扫描发现 → 3. 生成 JSON Schema → 4. 注册到 Gateway
                                                     │
5. Agent 决定调用 ← 6. Gateway 发送调用请求 ←────────┘
       │
7. SDK 调度执行 → 8. 返回结果 → 9. Agent 继续推理
```

## 1.9 MCP — Model Context Protocol

MCP（Model Context Protocol）是一个开放标准，定义了 AI 模型如何与外部工具交互。OpenClaw 的 Skill 框架在概念上与 MCP 兼容：

| MCP 概念 | OpenClaw 对应 |
|---------|-------------|
| Tool | Skill |
| Tool Schema (JSON Schema) | SkillDefinition.jsonSchema |
| tools/list | SkillManifest |
| tools/call | SkillDispatcher.dispatch() |
| Tool Result | SkillResult |

> **注意**：当前 SDK 的 Skill 框架是 MCP-ready 的（数据结构兼容），但尚未实现 MCP 的 JSON-RPC 传输层。

## 1.10 组件关系总结

```
用户 ──► 客户端（CLI/App/WebChat/SDK）
              │
              ▼
         ┌─────────┐
         │ Gateway  │ ◄── 认证、权限、路由
         └────┬────┘
              │
    ┌─────────┼─────────┐
    ▼         ▼         ▼
  Agent    Channel    Node
  (AI)    (消息渠道)  (设备)
    │         │         │
    ▼         ▼         ▼
  Model    外部平台   硬件能力
  Skill    (Discord   (摄像头
  Memory    Telegram)  屏幕)
```

## 1.11 小结

| 概念 | 一句话解释 |
|------|-----------|
| **Gateway** | OpenClaw 的核心网关，所有通信的枢纽 |
| **Agent** | AI 代理，配置了模型、工具和指令 |
| **Session** | 对话上下文，保存消息历史 |
| **Channel** | 外部消息平台的连接插件 |
| **Node** | 物理设备节点，暴露硬件能力 |
| **Skill** | 自定义工具函数，供 Agent 调用 |
| **MCP** | 工具调用的开放标准协议 |

**下一章**：[第2章：Gateway 协议深度解析](02-Gateway-协议深度解析.md) — 深入了解 Gateway 的 WebSocket 协议和 HTTP 端点。
