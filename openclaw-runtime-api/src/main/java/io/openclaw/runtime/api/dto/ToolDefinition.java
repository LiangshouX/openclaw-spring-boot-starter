package io.openclaw.runtime.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** 定义已注册工具的元数据和 Schema。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {

    /** 工具名称（唯一标识）。 */
    private String name;
    /** 工具描述，用于向 LLM 说明工具的功能。 */
    private String description;
    /** 工具实现类的全限定名。 */
    private String className;
    /** 工具参数的 JSON Schema。 */
    private JsonNode jsonSchema;
    /** 附加元数据。 */
    private Map<String, Object> metadata;
    /** 工具版本号。 */
    private String version;
}
