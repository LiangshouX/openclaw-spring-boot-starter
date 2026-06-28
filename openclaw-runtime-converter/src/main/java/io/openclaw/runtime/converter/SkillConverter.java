package io.openclaw.runtime.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openclaw.runtime.api.dto.SkillDefinition;
import io.openclaw.runtime.api.dto.SkillResult;

/** 技能相关 DTO 转换器，负责 JSON 和运行时表示之间的转换。 */
public class SkillConverter {

    private final ObjectMapper objectMapper;

    public SkillConverter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 将 JSON 节点转换为运行时 {@link SkillDefinition}。
     *
     * @param node 技能定义的 JSON 表示
     * @return 转换后的 {@link SkillDefinition}
     */
    public SkillDefinition toSkillDefinition(JsonNode node) {
        return objectMapper.convertValue(node, SkillDefinition.class);
    }

    /**
     * 将 JSON 节点转换为业务 {@link SkillResult}。
     *
     * @param node 技能结果的 JSON 表示
     * @return 转换后的 {@link SkillResult}
     */
    public SkillResult toBusinessSkillResult(JsonNode node) {
        return objectMapper.convertValue(node, SkillResult.class);
    }

    /**
     * 将运行时 {@link SkillDefinition} 转换为 OpenClaw 兼容的 JSON 节点。
     *
     * @param definition 运行时技能定义
     * @return 用于 OpenClaw Gateway 的 JSON 表示
     */
    public JsonNode toOpenClawSkillDefinition(SkillDefinition definition) {
        return objectMapper.valueToTree(definition);
    }
}
