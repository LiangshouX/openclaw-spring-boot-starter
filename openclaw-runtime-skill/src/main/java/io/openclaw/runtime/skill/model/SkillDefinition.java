package io.openclaw.runtime.skill.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * OpenClaw Skill 定义，对应 {@code SKILL.md} 文件的 YAML frontmatter。
 * <p>
 * 在 OpenClaw 规范中，Skill 是 Markdown 指令文件，用于教导 Agent 如何使用工具。
 * 这与 SDK 的 {@code @OpenClawTool} 注解标记的可调用函数（Tool）是不同的概念。
 * <p>
 * 本模块为未来扩展预留，当前版本不包含完整实现。
 *
 * @see <a href="https://agentskills.io">AgentSkills Specification</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDefinition {

    /** Skill 名称（唯一标识，小写字母、数字和连字符）。 */
    private String name;

    /** 一行描述，展示给 Agent 和发现输出（不超过 160 字符）。 */
    private String description;

    /** 是否作为用户斜杠命令暴露。 */
    @Builder.Default
    private boolean userInvocable = true;

    /** 是否禁止将 Skill 指令注入 Agent 的 system prompt。 */
    @Builder.Default
    private boolean disableModelInvocation = false;

    /** 命令分派模式：设置为 "tool" 时直接将斜杠命令路由到工具。 */
    private String commandDispatch;

    /** 当 commandDispatch 为 "tool" 时要调用的工具名称。 */
    private String commandTool;

    /** 命令参数模式（默认 "raw"）。 */
    @Builder.Default
    private String commandArgMode = "raw";

    /** Skill 主页 URL。 */
    private String homepage;

    /** 附加元数据（如 gating 配置、依赖要求等）。 */
    private Map<String, Object> metadata;

    /** SKILL.md 文件的 Markdown 正文（Agent 指令内容）。 */
    private String body;

    /** Skill 来源目录路径。 */
    private String sourcePath;
}
