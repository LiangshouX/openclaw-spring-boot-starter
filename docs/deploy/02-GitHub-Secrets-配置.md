# GitHub Secrets 配置

GitHub Actions 的发布工作流需要访问 Sonatype 和 GPG 签名所需的凭证。这些凭证通过 GitHub Secrets 注入。

## 配置步骤

1. 打开 GitHub 仓库页面：https://github.com/LiangshouX/openclaw-spring-boot-starter
2. 进入 **Settings** → **Secrets and variables** → **Actions**
3. 点击 **New repository secret**，逐一添加以下 Secrets：

## 所需 Secrets 列表

| Secret 名称 | 说明 | 来源 |
|---|---|---|
| `OSSRH_USERNAME` | Sonatype Token 的 username | [01-前置准备.md](01-前置准备.md) → 第 4 步 |
| `OSSRH_TOKEN` | Sonatype Token 的 password | [01-前置准备.md](01-前置准备.md) → 第 4 步 |
| `GPG_PRIVATE_KEY` | GPG 私钥（ASCII 格式完整内容） | [01-前置准备.md](01-前置准备.md) → 第 3 步 |
| `GPG_PASSPHRASE` | GPG 私钥密码（passphrase） | [01-前置准备.md](01-前置准备.md) → 第 3 步 |

## 详细说明

### OSSRH_USERNAME / OSSRH_TOKEN

在 Sonatype Central Portal 的 **Account** → **Generate User Token** 页面获取。

注意：这是 User Token，不是你的登录账号密码。Token 格式类似：
```
username: a1b2c3d4e5
password: xXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxX
```

将 `username` 部分存入 `OSSRH_USERNAME`，`password` 部分存入 `OSSRH_TOKEN`。

### GPG_PRIVATE_KEY

导出命令：
```bash
gpg --armor --export-secret-keys YOUR_KEY_ID
```

输出以 `-----BEGIN PGP PRIVATE KEY BLOCK-----` 开头，以 `-----END PGP PRIVATE KEY BLOCK-----` 结尾。

**复制整个输出**（包括首尾标记行）粘贴到 Secret 值中。

### GPG_PASSPHRASE

生成 GPG 密钥时设置的密码。

## 验证配置

配置完成后，可以手动触发一次 CI 工作流验证 Secrets 是否正确：

1. 进入 **Actions** 页面
2. 选择 **Release to Maven Central** 工作流
3. 点击 **Run workflow**
4. 输入测试版本号（建议先用 SNAPSHOT 版本测试）

## 安全建议

- 定期轮换 Sonatype Token
- GPG 私钥密码不要与其他密码重复
- 限制仓库的写入权限，只有可信的维护者才能触发 release 工作流
