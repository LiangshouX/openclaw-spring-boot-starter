package io.openclaw.runtime.skill.archive;

import io.openclaw.runtime.skill.model.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 将 {@link SkillDefinition} 打包为 OpenClaw Gateway 接受的 zip 归档格式。
 * <p>
 * 归档结构：
 * <pre>{@code
 * <skill-name>/
 *   SKILL.md
 * }</pre>
 * <p>
 * SKILL.md 内容从 {@link SkillDefinition} 的 frontmatter 字段和 body 重建。
 * 生成的字节数组用于 {@code skills.upload.begin/chunk/commit} RPC 流程。
 */
public class SkillArchiveBuilder {

    private static final Logger log = LoggerFactory.getLogger(SkillArchiveBuilder.class);

    /**
     * 将指定的 Skill 定义打包为 zip 归档字节数组。
     *
     * @param skill Skill 定义
     * @return zip 归档的字节数组
     * @throws IOException 打包失败时
     */
    public byte[] buildArchive(SkillDefinition skill) throws IOException {
        String skillContent = reconstructSkillMd(skill);
        byte[] contentBytes = skillContent.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Create the skill directory entry
            String dirEntry = skill.getName() + "/";
            zos.putNextEntry(new ZipEntry(dirEntry));
            zos.closeEntry();

            // Create the SKILL.md entry inside the directory
            String fileEntry = skill.getName() + "/SKILL.md";
            zos.putNextEntry(new ZipEntry(fileEntry));
            zos.write(contentBytes);
            zos.closeEntry();
        }

        byte[] archive = baos.toByteArray();
        log.debug("Built skill archive for '{}': {} bytes (SKILL.md content: {} bytes)",
                skill.getName(), archive.length, contentBytes.length);
        return archive;
    }

    /**
     * 计算字节数组的 SHA-256 哈希值（十六进制字符串）。
     *
     * @param data 原始字节数组
     * @return SHA-256 哈希的十六进制字符串
     */
    public String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in Java
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * 从 {@link SkillDefinition} 重建 SKILL.md 文件内容。
     * <p>
     * 格式：
     * <pre>{@code
     * ---
     * name: my-skill
     * description: One-line description
     * user-invocable: true
     * ...
     * ---
     *
     * # Markdown body
     * }</pre>
     */
    private String reconstructSkillMd(SkillDefinition skill) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");

        // Required fields
        sb.append("name: ").append(skill.getName()).append('\n');
        if (skill.getDescription() != null) {
            sb.append("description: ").append(skill.getDescription()).append('\n');
        }

        // Optional fields — only include if non-default
        if (!skill.isUserInvocable()) {
            sb.append("user-invocable: false\n");
        }
        if (skill.isDisableModelInvocation()) {
            sb.append("disable-model-invocation: true\n");
        }
        if (skill.getCommandDispatch() != null) {
            sb.append("command-dispatch: ").append(skill.getCommandDispatch()).append('\n');
        }
        if (skill.getCommandTool() != null) {
            sb.append("command-tool: ").append(skill.getCommandTool()).append('\n');
        }
        if (skill.getCommandArgMode() != null && !"raw".equals(skill.getCommandArgMode())) {
            sb.append("command-arg-mode: ").append(skill.getCommandArgMode()).append('\n');
        }
        if (skill.getHomepage() != null) {
            sb.append("homepage: ").append(skill.getHomepage()).append('\n');
        }

        // Metadata as single-line JSON
        if (skill.getMetadata() != null && !skill.getMetadata().isEmpty()) {
            sb.append("metadata: ").append(mapToJson(skill.getMetadata())).append('\n');
        }

        sb.append("---\n");

        // Body
        if (skill.getBody() != null && !skill.getBody().isEmpty()) {
            sb.append('\n');
            sb.append(skill.getBody());
            if (!skill.getBody().endsWith("\n")) {
                sb.append('\n');
            }
        }

        return sb.toString();
    }

    /**
     * 将 Map 序列化为紧凑的单行 JSON 字符串。
     * <p>
     * 这里使用简单的手动序列化以避免对 ObjectMapper 的硬依赖。
     * 对于复杂场景（嵌套对象），可后续替换为 Jackson ObjectMapper。
     */
    @SuppressWarnings("unchecked")
    private String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append('"').append(escapeJson(entry.getKey())).append("\": ");
            appendJsonValue(sb, entry.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendJsonValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append('"').append(escapeJson((String) value)).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map) {
            sb.append(mapToJson((Map<String, Object>) value));
        } else if (value instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) sb.append(", ");
                first = false;
                appendJsonValue(sb, item);
            }
            sb.append(']');
        } else {
            // Fallback: treat as string
            sb.append('"').append(escapeJson(value.toString())).append('"');
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
