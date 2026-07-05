package io.openclaw.runtime.skill.loader;

import io.openclaw.runtime.skill.model.SkillDefinition;
import io.openclaw.runtime.skill.parser.SkillFrontmatterParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 默认 Skill 加载器实现，从文件系统扫描 {@code SKILL.md} 文件并解析为 {@link SkillDefinition}。
 * <p>
 * 遵循 OpenClaw Skill 的目录约定：每个 Skill 是一个包含 {@code SKILL.md} 的目录，
 * 目录名作为 Skill 的 fallback 名称（当 frontmatter 中未指定 name 时）。
 * <p>
 * 支持扫描以下目录结构：
 * <pre>{@code
 * skills/
 * ├── weather/
 * │   └── SKILL.md
 * ├── github/
 * │   └── SKILL.md
 * └── my-skill/
 *     └── SKILL.md
 * }</pre>
 */
public class DefaultSkillLoader implements SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(DefaultSkillLoader.class);
    private static final String SKILL_FILENAME = "SKILL.md";

    private final SkillFrontmatterParser parser;

    public DefaultSkillLoader() {
        this(new SkillFrontmatterParser());
    }

    public DefaultSkillLoader(SkillFrontmatterParser parser) {
        this.parser = parser;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 递归扫描指定目录及其所有子目录，查找名为 {@code SKILL.md} 的文件并逐个解析。
     * 解析失败的文件会被跳过并记录警告日志，不会中断整个加载过程。
     */
    @Override
    public List<SkillDefinition> loadFromDirectory(Path directory) {
        List<SkillDefinition> skills = new ArrayList<>();

        if (directory == null || !Files.isDirectory(directory)) {
            log.warn("Skill directory does not exist or is not a directory: {}", directory);
            return skills;
        }

        log.info("Scanning for SKILL.md files in: {}", directory);

        try {
            // Find all SKILL.md files recursively
            List<Path> skillFiles = findSkillFiles(directory);

            for (Path skillFile : skillFiles) {
                try {
                    SkillDefinition definition = loadFromFile(skillFile);
                    skills.add(definition);
                    log.info("Loaded skill: name='{}', source={}",
                            definition.getName(), skillFile.getParent());
                } catch (Exception e) {
                    log.warn("Failed to load skill from {}: {}", skillFile, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan skill directory {}: {}", directory, e.getMessage());
        }

        log.info("Loaded {} skills from {}", skills.size(), directory);
        return skills;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SkillDefinition loadFromFile(Path skillFile) {
        try {
            return parser.parse(skillFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse SKILL.md: " + skillFile, e);
        }
    }

    /**
     * 递归查找目录下所有的 SKILL.md 文件。
     */
    private List<Path> findSkillFiles(Path root) throws IOException {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(SKILL_FILENAME))
                    .sorted()
                    .forEach(result::add);
        }
        return result;
    }
}
