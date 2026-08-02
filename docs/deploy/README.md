# OpenClaw Runtime SDK — 发布与部署指南

本目录包含将 OpenClaw Runtime SDK 发布到 Maven Central 的完整操作手册。

## 文档索引

| 文档 | 说明 |
|------|------|
| [01-前置准备.md](01-前置准备.md) | Sonatype 账号注册、Namespace 验证、GPG 密钥生成 |
| [02-GitHub-Secrets-配置.md](02-GitHub-Secrets-配置.md) | 在 GitHub 仓库中配置所需的 Secrets |
| [03-本地手动发布.md](03-本地手动发布.md) | 在本地环境手动执行发布流程 |
| [04-CI-CD-自动发布.md](04-CI-CD-自动发布.md) | 通过 GitHub Actions 自动化发布 |
| [05-版本管理策略.md](05-版本管理策略.md) | 版本号规范、分支策略、发布节奏 |
| [06-常见问题.md](06-常见问题.md) | FAQ 与故障排查 |

## 快速流程概览

```
Sonatype 注册 → Namespace 验证 → GPG 密钥生成 → 配置 GitHub Secrets → 触发 Release Workflow
```
