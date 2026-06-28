package io.openclaw.runtime.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** 定义已注册技能的元数据和 Schema。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDefinition {

    private String name;
    private String description;
    private String className;
    private JsonNode jsonSchema;
    private Map<String, Object> metadata;
    private String version;
}
