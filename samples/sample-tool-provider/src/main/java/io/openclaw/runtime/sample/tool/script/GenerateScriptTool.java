package io.openclaw.runtime.sample.tool.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openclaw.runtime.api.dto.ToolResult;
import io.openclaw.runtime.api.tool.Tool;
import io.openclaw.runtime.tool.annotation.OpenClawTool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 示例工具，根据提示词生成脚本。
 * 演示如何使用 @OpenClawTool 注解并实现 Tool 接口。
 */
@OpenClawTool(
        name = "generate_script",
        description = "Generate a script from a prompt"
)
public class GenerateScriptTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(GenerateScriptTool.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ToolResult invoke(JsonNode arguments) {
        log.info("Generating script with arguments: {}", arguments);

        long startTime = System.currentTimeMillis();

        // 模拟脚本生成
        String prompt = arguments.has("prompt") ? arguments.get("prompt").asText() : "default prompt";

        ObjectNode resultData = objectMapper.createObjectNode();
        resultData.put("script", "#!/bin/bash\necho 'Generated from prompt: " + prompt + "'");
        resultData.put("language", "bash");
        resultData.put("generatedAt", java.time.Instant.now().toString());

        long executionTime = System.currentTimeMillis() - startTime;

        return ToolResult.builder()
                .toolName("generate_script")
                .success(true)
                .data(resultData)
                .executionTimeMs(executionTime)
                .build();
    }
}
