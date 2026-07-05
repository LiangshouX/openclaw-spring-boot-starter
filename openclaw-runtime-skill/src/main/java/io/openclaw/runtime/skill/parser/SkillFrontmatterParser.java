package io.openclaw.runtime.skill.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openclaw.runtime.skill.model.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SKILL.md 文件解析器，解析 YAML frontmatter 和 Markdown 正文。
 * <p>
 * SKILL.md 文件格式：
 * <pre>{@code
 * ---
 * name: my-skill
 * description: One-line description
 * user-invocable: true
 * disable-model-invocation: false
 * command-dispatch: tool
 * command-tool: my_tool
 * command-arg-mode: raw
 * homepage: https://example.com
 * metadata: {"openclaw": {"requires": {"bins": ["uv"]}}}
 * ---
 *
 * # Instructions for the agent
 * When the user asks to ..., use the `my_tool` tool...
 * }</pre>
 * <p>
 * 遵循 AgentSkills 规范：frontmatter 仅支持单行键值对，
 * {@code metadata} 必须为单行 JSON 对象。
 *
 * @see <a href="https://agentskills.io">AgentSkills Specification</a>
 */
public class SkillFrontmatterParser {

    private static final Logger log = LoggerFactory.getLogger(SkillFrontmatterParser.class);
    private static final String DELIMITER = "---";
    private static final String SEPARATOR = ": ";

    private final ObjectMapper objectMapper;

    public SkillFrontmatterParser() {
        this(new ObjectMapper());
    }

    public SkillFrontmatterParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析 SKILL.md 文件，返回完整的 {@link SkillDefinition}。
     *
     * @param skillFile SKILL.md 文件路径
     * @return 解析后的 Skill 定义
     * @throws IOException 当文件读取失败或格式不合法时
     */
    public SkillDefinition parse(Path skillFile) throws IOException {
        String content = Files.readString(skillFile);
        return parse(content, skillFile);
    }

    /**
     * 从字符串内容解析 SKILL.md。
     *
     * @param content   SKILL.md 的完整文本内容
     * @param sourceFile 来源文件路径（可为 null）
     * @return 解析后的 Skill 定义
     * @throws IOException 当格式不合法时
     */
    public SkillDefinition parse(String content, Path sourceFile) throws IOException {
        String trimmed = content.strip();

        if (!trimmed.startsWith(DELIMITER)) {
            throw new IOException("SKILL.md must start with '---' frontmatter delimiter: " + sourceFile);
        }

        // Find the closing delimiter
        int closingIndex = trimmed.indexOf(DELIMITER, 3);
        if (closingIndex < 0) {
            throw new IOException("SKILL.md missing closing '---' frontmatter delimiter: " + sourceFile);
        }

        String frontmatterBlock = trimmed.substring(3, closingIndex).strip();
        String body = trimmed.substring(closingIndex + 3).strip();

        // Parse frontmatter key-value pairs
        Map<String, String> frontmatter = parseFrontmatter(frontmatterBlock);

        // Build SkillDefinition from parsed frontmatter
        SkillDefinition.SkillDefinitionBuilder builder = SkillDefinition.builder();

        builder.name(frontmatter.getOrDefault("name", deriveNameFromPath(sourceFile)));
        builder.description(frontmatter.get("description"));
        builder.body(body);
        builder.sourcePath(sourceFile != null ? sourceFile.getParent().toString() : null);

        if (frontmatter.containsKey("user-invocable")) {
            builder.userInvocable(parseBoolean(frontmatter.get("user-invocable"), true));
        }
        if (frontmatter.containsKey("disable-model-invocation")) {
            builder.disableModelInvocation(parseBoolean(frontmatter.get("disable-model-invocation"), false));
        }
        if (frontmatter.containsKey("command-dispatch")) {
            builder.commandDispatch(frontmatter.get("command-dispatch"));
        }
        if (frontmatter.containsKey("command-tool")) {
            builder.commandTool(frontmatter.get("command-tool"));
        }
        if (frontmatter.containsKey("command-arg-mode")) {
            builder.commandArgMode(frontmatter.get("command-arg-mode"));
        }
        if (frontmatter.containsKey("homepage")) {
            builder.homepage(frontmatter.get("homepage"));
        }

        // Parse metadata as single-line JSON object
        if (frontmatter.containsKey("metadata")) {
            try {
                Map<String, Object> metadata = objectMapper.readValue(
                        frontmatter.get("metadata"),
                        new TypeReference<Map<String, Object>>() {});
                builder.metadata(metadata);
            } catch (Exception e) {
                log.warn("Failed to parse metadata JSON in {}: {}", sourceFile, e.getMessage());
            }
        }

        SkillDefinition definition = builder.build();
        log.debug("Parsed SKILL.md: name='{}', description='{}', body={} chars",
                definition.getName(), definition.getDescription(),
                body != null ? body.length() : 0);
        return definition;
    }

    /**
     * 逐行解析 frontmatter 为键值对。
     * 遵循 OpenClaw 规范：仅支持单行键值对。
     */
    private Map<String, String> parseFrontmatter(String block) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : block.split("\n")) {
            String stripped = line.strip();
            if (stripped.isEmpty() || stripped.startsWith("#")) {
                continue; // skip blank lines and comments
            }
            int sepIndex = stripped.indexOf(SEPARATOR);
            if (sepIndex > 0) {
                String key = stripped.substring(0, sepIndex).strip();
                String value = stripped.substring(sepIndex + SEPARATOR.length()).strip();
                result.put(key, value);
            }
        }
        return result;
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.strip());
    }

    /**
     * 当 frontmatter 中没有 name 字段时，从文件所在目录名推导。
     */
    private String deriveNameFromPath(Path sourceFile) {
        if (sourceFile != null && sourceFile.getParent() != null) {
            return sourceFile.getParent().getFileName().toString();
        }
        return "unnamed-skill";
    }
}
