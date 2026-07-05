package io.openclaw.runtime.api.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.openclaw.runtime.api.dto.ToolResult;

/**
 * 所有 OpenClaw 工具必须实现的接口。
 * 通过 {@code @OpenClawTool} 注解自动发现工具。
 */
public interface Tool {

    /**
     * 使用给定参数调用工具。
     *
     * @param arguments 传递给工具的 JSON 参数
     * @return 工具调用的结果
     */
    ToolResult invoke(JsonNode arguments);
}
