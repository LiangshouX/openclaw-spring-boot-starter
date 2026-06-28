package io.openclaw.runtime.api.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** OpenClaw Runtime 停止时发布的事件。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class RuntimeStoppedEvent extends RuntimeEvent {

    private String runtimeId;
    private String reason;
}
