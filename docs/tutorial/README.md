# OpenClaw Spring Boot Starter 教程

> 面向 Java 开发者的 OpenClaw 集成实战指南

## 关于本教程

本教程将带你从零开始理解 OpenClaw 平台的架构原理，并动手实现一个 Spring Boot Starter SDK 来集成 OpenClaw。

**适合人群**：
- 使用过 OpenClaw（CLI / macOS App / WebChat），但不了解底层协议
- 有基础 Java 和 Spring Boot 经验
- 希望在自己的 Java 项目中集成 OpenClaw 能力

**你将学到**：
- OpenClaw 平台的核心概念和架构
- Gateway WebSocket 协议和 HTTP 兼容端点
- 认证、权限和作用域（Scope）机制
- 如何设计和实现一个双传输层 SDK
- Spring Boot 自动配置的最佳实践
- 技能（Skill）注册和调度的完整流程
- 如何打包发布为 Maven 依赖

## 学习路线

```
基础概念                    协议与权限                SDK 设计与实现
─────────                  ──────────               ──────────────
┌─────────────┐           ┌─────────────┐          ┌─────────────┐
│ 第1章        │          │ 第2章        │         │ 第4章        │
│ OpenClaw    │──►       │ Gateway     │──►     │ SDK 架构     │
│ 平台概览     │          │ 协议深度解析  │         │ 设计思路     │
└─────────────┘           └──────┬──────┘          └──────┬──────┘
                                 │                        │
                          ┌──────▼──────┐          ┌──────▼──────┐
                          │ 第3章        │         │ 第5章        │
                          │ 认证与权限    │         │ WebSocket   │
                          │             │         │ 通信层实现    │
                          └─────────────┘          └──────┬──────┘
                                                          │
 进阶主题                    实战与发布              ┌──────▼──────┐
 ────────                   ──────────              │ 第6章        │
┌─────────────┐           ┌─────────────┐          │ HTTP        │
│ 第7章        │          │ 第9章        │          │ 通信层实现    │
│ Skill 技能   │◄──       │ 打包发布     │◄──      └─────────────┘
│ 框架与 MCP   │          │ 与依赖管理    │
└──────┬──────┘           └──────┬──────┘          ┌─────────────┐
       │                         │                  │ 第8章        │
┌──────▼──────┐           ┌──────▼──────┐          │ Spring Boot │
│ 第10章       │◄──────────│ 实战：从零    │          │ 自动配置     │
│ 调试与排错   │           │ 搭建接入项目  │          └─────────────┘
└─────────────┘           └─────────────┘
```

## 章节列表

### 第一部分：基础概念

| 章节 | 标题 | 你将学到 |
|------|------|---------|
| [第1章](01-OpenClaw-平台概览与核心概念.md) | OpenClaw 平台概览与核心概念 | Gateway、Agent、Session、Channel、Node、Skill 是什么 |
| [第2章](02-Gateway-协议深度解析.md) | Gateway 协议深度解析 | WebSocket 帧格式、握手流程、RPC 调用、事件推送 |
| [第3章](03-认证权限与作用域.md) | 认证、权限与作用域 | Token 认证、设备配对、Scope 层级、HTTP vs WS 权限差异 |

### 第二部分：SDK 设计与实现

| 章节 | 标题 | 你将学到 |
|------|------|---------|
| [第4章](04-SDK-架构设计思路.md) | SDK 架构设计思路 | 模块划分、依赖图、双传输架构、设计模式 |
| [第5章](05-WebSocket-通信层实现.md) | WebSocket 通信层实现 | 状态机、握手协议、RPC 关联、心跳、重连 |
| [第6章](06-HTTP-通信层实现.md) | HTTP 通信层实现 | OpenAI 兼容端点、SSE 流式解析、文件上传 |

### 第三部分：进阶与实战

| 章节 | 标题 | 你将学到 |
|------|------|---------|
| [第7章](07-Skill-技能框架与MCP对接.md) | Skill 技能框架与 MCP 对接 | 注解驱动、扫描注册、调度分发、JSON Schema |
| [第8章](08-Spring-Boot-自动配置实战.md) | Spring Boot 自动配置实战 | 条件装配、属性绑定、生命周期、启动流程 |
| [第9章](09-打包发布与依赖管理.md) | 打包发布与依赖管理 | Maven 多模块、Starter 模式、版本管理 |
| [第10章](10-实战-从零搭建接入项目.md) | 实战：从零搭建接入项目 | 完整示例、调试技巧、常见错误排查 |

## 前置知识

- **Java 17+**：了解基本的类、接口、注解、Lambda 表达式
- **Spring Boot 3.x**：了解 `@Bean`、`@Configuration`、`application.yml`
- **Maven**：了解 `pom.xml`、依赖管理、`mvn` 命令
- **OpenClaw 使用经验**：用过 CLI 或 macOS App 与 Agent 对话即可

## 项目源码

本教程的代码示例来自 `openclaw-spring-boot-starter` 项目：

```
openclaw-spring-boot-starter/
├── openclaw-runtime-api/           ← 接口、DTO、事件、异常
├── openclaw-runtime-client/        ← HTTP/WebSocket 通信层
├── openclaw-runtime-event/         ← 事件发布
├── openclaw-runtime-session/       ← 会话管理
├── openclaw-runtime-skill/         ← 技能框架
├── openclaw-runtime-converter/     ← DTO 转换
├── openclaw-runtime-autoconfigure/ ← Spring Boot 自动配置
├── openclaw-runtime-starter/       ← 依赖聚合器
└── samples/                        ← 示例应用
```

## 开始学习

准备好了吗？从 [第1章：OpenClaw 平台概览与核心概念](01-OpenClaw-平台概览与核心概念.md) 开始。
