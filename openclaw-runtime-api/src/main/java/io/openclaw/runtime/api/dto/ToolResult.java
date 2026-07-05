package io.openclaw.runtime.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 表示工具调用的结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /** 产生此结果的工具名称。 */
    private String toolName;
    /** 工具调用是否成功。 */
    private boolean success;
    /** 工具返回的输出数据。 */
    private JsonNode data;
    /** 工具调用失败时的错误信息。 */
    private String errorMessage;
    /** 工具的执行时间（毫秒）。 */
    private long executionTimeMs;

    /**
     * 创建成功的工具执行结果。
     *
     * @param toolName 工具名称
     * @param data     工具产生的输出数据
     * @return 新的成功 {@code ToolResult}
     */
    public static ToolResult success(String toolName, JsonNode data) {
        return ToolResult.builder()
                .toolName(toolName)
                .success(true)
                .data(data)
                .build();
    }

    /**
     * 创建失败的工具执行结果。
     *
     * @param toolName     工具名称
     * @param errorMessage 描述失败的错误信息
     * @return 新的失败 {@code ToolResult}
     */
    public static ToolResult failure(String toolName, String errorMessage) {
        return ToolResult.builder()
                .toolName(toolName)
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
