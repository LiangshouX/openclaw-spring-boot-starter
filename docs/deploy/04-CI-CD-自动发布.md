# CI/CD 自动发布

本项目通过 GitHub Actions 实现自动化的构建测试和发布流程。

## 工作流概览

### CI 工作流 (`ci.yml`)

- **触发条件**：push 到 `main` 分支或 PR 到 `main` 分支
- **执行内容**：构建所有模块、运行测试
- **矩阵策略**：JDK 17

### Release 工作流 (`release.yml`)

- **触发条件**：手动触发（`workflow_dispatch`）
- **执行内容**：修改版本号 → 构建签名 → 部署到 Maven Central → 打 Tag → 创建 GitHub Release → 推进到下一个 SNAPSHOT 版本

## 发布操作步骤

### Step 1：确认 CI 通过

发布前确保 `main` 分支的 CI 工作流是绿色的：

1. 打开 GitHub 仓库 → **Actions** 页面
2. 确认最近一次 CI 运行状态为 ✅

### Step 2：触发 Release 工作流

1. 进入 **Actions** → **Release to Maven Central**
2. 点击 **Run workflow**
3. 填写参数：
   - **release_version**：发布版本号，例如 `0.0.1`
   - **development_version**：下一个开发版本号，例如 `0.0.2-SNAPSHOT`
4. 点击 **Run workflow** 按钮

### Step 3：监控工作流执行

工作流会依次执行以下步骤：

```
Checkout → Setup JDK → 修改版本号 → 构建签名部署 → 打 Tag → 设置下一版本 → 提交推送 → 创建 GitHub Release
```

每个步骤都可以在 Actions 页面实时查看日志。

### Step 4：在 Sonatype Central Portal 确认发布

工作流完成 `deploy` 步骤后，artifact 会被上传到 Sonatype staging 仓库。

1. 登录 https://central.sonatype.com/
2. 进入 **Deployments** 页面
3. 找到对应的 deployment
4. 点击 **Publish** 完成发布

> **注意**：当前配置为 `autoReleaseAfterClose: false`，需要手动在 Central Portal 确认发布。
> 如果希望自动发布，可修改 `nexus-staging-maven-plugin` 的配置：
> ```xml
> <autoReleaseAfterClose>true</autoReleaseAfterClose>
> ```

### Step 5：验证

- 检查 GitHub Releases 页面是否有新的 release
- 检查 Maven Central 是否已同步（10-30 分钟）
- 检查 `main` 分支版本号是否已推进到下一个 SNAPSHOT

## 工作流文件说明

### ci.yml

```yaml
触发: push/PR to main
Job: Build & Test (JDK 17)
Steps:
  1. Checkout
  2. Setup JDK (带 Maven 缓存)
  3. mvn clean verify
  4. 上传测试报告作为 artifact
```

### release.yml

```yaml
触发: workflow_dispatch (手动)
Inputs:
  - release_version: 发布版本号
  - development_version: 下一个开发版本号
Steps:
  1. Checkout
  2. Setup JDK (带 Maven 缓存 + GPG 密钥导入)
  3. 配置 Git 用户
  4. 修改版本号 (去掉 -SNAPSHOT)
  5. mvn clean deploy -Prelease (构建签名部署)
  6. Git tag + push
  7. 修改版本号 (设置下一个 SNAPSHOT)
  8. Git commit + push
  9. 创建 GitHub Release (自动生成 release notes)
```

## 首次发布注意事项

1. **先用 SNAPSHOT 测试**：在触发 release 前，先手动执行一次 SNAPSHOT 发布，验证 Secrets 配置正确
2. **检查 Namespace**：确保 Sonatype Central Portal 中 `io.openclaw.runtime` 的 namespace 验证已通过
3. **GPG 公钥同步**：确保 GPG 公钥已上传到至少一个密钥服务器
4. **确认 staging**：首次发布建议手动在 Central Portal 确认，不要开启 autoRelease
