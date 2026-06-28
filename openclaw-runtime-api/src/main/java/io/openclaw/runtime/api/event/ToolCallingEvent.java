package io.openclaw.runtime.api.event;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** OpenClaw 调用已注册技能（工具调用）时发布的事件。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ToolCallingEvent extends RuntimeEvent {

    private String toolCallId;
    private String skillName;
    private JsonNode arguments;
}
