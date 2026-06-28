package io.openclaw.runtime.sample.skill.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openclaw.runtime.api.dto.SkillResult;
import io.openclaw.runtime.api.skill.Skill;
import io.openclaw.runtime.skill.annotation.OpenClawSkill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 示例技能，根据提示词生成脚本。
 * 演示如何使用 @OpenClawSkill 注解并实现 Skill 接口。
 */
@OpenClawSkill(
        name = "generate_script",
        description = "Generate a script from a prompt"
)
public class GenerateScriptSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(GenerateScriptSkill.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public SkillResult invoke(JsonNode arguments) {
        log.info("Generating script with arguments: {}", arguments);

        long startTime = System.currentTimeMillis();

        // 模拟脚本生成
        String prompt = arguments.has("prompt") ? arguments.get("prompt").asText() : "default prompt";

        ObjectNode resultData = objectMapper.createObjectNode();
        resultData.put("script", "#!/bin/bash\necho 'Generated from prompt: " + prompt + "'");
        resultData.put("language", "bash");
        resultData.put("generatedAt", java.time.Instant.now().toString());

        long executionTime = System.currentTimeMillis() - startTime;

        return SkillResult.builder()
                .skillName("generate_script")
                .success(true)
                .data(resultData)
                .executionTimeMs(executionTime)
                .build();
    }
}
