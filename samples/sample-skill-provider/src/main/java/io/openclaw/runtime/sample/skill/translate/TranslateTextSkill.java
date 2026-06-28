package io.openclaw.runtime.sample.skill.translate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openclaw.runtime.api.dto.SkillResult;
import io.openclaw.runtime.api.skill.Skill;
import io.openclaw.runtime.skill.annotation.OpenClawSkill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 示例技能，实现文本的语言间翻译。
 * 演示如何使用 @OpenClawSkill 注解并实现 Skill 接口。
 */
@OpenClawSkill(
        name = "translate_text",
        description = "Translate text between languages"
)
public class TranslateTextSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(TranslateTextSkill.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public SkillResult invoke(JsonNode arguments) {
        log.info("Translating text with arguments: {}", arguments);

        long startTime = System.currentTimeMillis();

        String text = arguments.has("text") ? arguments.get("text").asText() : "";
        String sourceLang = arguments.has("sourceLanguage") ? arguments.get("sourceLanguage").asText() : "en";
        String targetLang = arguments.has("targetLanguage") ? arguments.get("targetLanguage").asText() : "zh";

        // 模拟翻译
        ObjectNode resultData = objectMapper.createObjectNode();
        resultData.put("originalText", text);
        resultData.put("translatedText", "[Translated] " + text);
        resultData.put("sourceLanguage", sourceLang);
        resultData.put("targetLanguage", targetLang);

        long executionTime = System.currentTimeMillis() - startTime;

        return SkillResult.builder()
                .skillName("translate_text")
                .success(true)
                .data(resultData)
                .executionTimeMs(executionTime)
                .build();
    }
}
