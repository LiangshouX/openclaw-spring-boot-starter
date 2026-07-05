package io.openclaw.runtime.sample.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 示例应用，演示 OpenClaw Skill (SKILL.md) 的加载、管理和远程注册。
 * <p>
 * 本示例展示的是 OpenClaw 规范中的 <b>Skill</b> 概念 —— 即 Markdown 指令文件，
 * 用于教导 Agent 如何使用工具。这与 {@code @OpenClawTool} 注解的 Tool（可调用函数）不同。
 * <p>
 * 配置 {@code openclaw.auto-register-skill=true} 后，SDK 会自动：
 * <ol>
 *   <li>从 {@code openclaw.skill.directories} 配置的目录扫描 SKILL.md 文件</li>
 *   <li>解析每个文件的 YAML frontmatter 和 Markdown 正文</li>
 *   <li>注册到 {@link io.openclaw.runtime.skill.loader.DefaultSkillManager} 内存注册表</li>
 *   <li>通过 WebSocket RPC {@code skills.upload.* + skills.install} 上传到 Gateway</li>
 * </ol>
 * <p>
 * <b>前提条件</b>：Gateway 需要启用 {@code skills.install.allowUploadedArchives: true}。
 * 如果 Gateway 未启用此配置，上传会静默失败，但 Skill 仍会被本地加载并可通过 REST API 查询。
 */
@SpringBootApplication
public class SampleSkillProviderApplication {

    private static final Logger log = LoggerFactory.getLogger(SampleSkillProviderApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SampleSkillProviderApplication.class, args);
    }
}
