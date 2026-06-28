package io.openclaw.runtime.api.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** OpenClaw 在推理过程中产生推理内容时发布的事件。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ReasoningEvent extends RuntimeEvent {

    private String sessionId;
    private String reasoningContent;
}
