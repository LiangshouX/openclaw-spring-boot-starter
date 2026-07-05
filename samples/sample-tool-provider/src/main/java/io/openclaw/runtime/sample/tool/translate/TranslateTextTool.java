package io.openclaw.runtime.sample.tool.translate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openclaw.runtime.api.dto.ToolResult;
import io.openclaw.runtime.api.tool.Tool;
import io.openclaw.runtime.tool.annotation.OpenClawTool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 示例工具，实现文本的语言间翻译。
 * 演示如何使用 @OpenClawTool 注解并实现 Tool 接口。
 */
@OpenClawTool(
        name = "translate_text",
        description = "Translate text between languages"
)
public class TranslateTextTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(TranslateTextTool.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ToolResult invoke(JsonNode arguments) {
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

        return ToolResult.builder()
                .toolName("translate_text")
                .success(true)
                .data(resultData)
                .executionTimeMs(executionTime)
                .build();
    }
}
