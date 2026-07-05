package io.openclaw.runtime.skill.loader;

import io.openclaw.runtime.skill.model.SkillDefinition;

import java.nio.file.Path;
import java.util.List;

/**
 * Skill 加载器接口，负责从文件系统加载 {@code SKILL.md} 文件。
 * <p>
 * 在 OpenClaw 规范中，Skill 通过目录中的 {@code SKILL.md} 文件定义，
 * 包含 YAML frontmatter 和 Markdown 正文。
 * <p>
 * 本接口为未来扩展预留，当前版本不包含完整实现。
 */
public interface SkillLoader {

    /**
     * 从指定目录加载所有 Skill 定义。
     *
     * @param directory 要扫描的目录路径
     * @return 发现的 Skill 定义列表
     */
    List<SkillDefinition> loadFromDirectory(Path directory);

    /**
     * 从单个 {@code SKILL.md} 文件加载 Skill 定义。
     *
     * @param skillFile SKILL.md 文件路径
     * @return 解析后的 Skill 定义
     */
    SkillDefinition loadFromFile(Path skillFile);
}
