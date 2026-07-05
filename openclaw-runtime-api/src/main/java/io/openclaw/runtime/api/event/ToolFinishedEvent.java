package io.openclaw.runtime.api.event;

import io.openclaw.runtime.api.dto.ToolResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 工具调用完成并返回结果时发布的事件。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ToolFinishedEvent extends RuntimeEvent {

    private String toolCallId;
    private String toolName;
    private ToolResult result;
}
