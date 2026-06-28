package io.openclaw.runtime.api.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** OpenClaw Runtime 成功启动时发布的事件。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class RuntimeStartedEvent extends RuntimeEvent {

    private String runtimeId;
    private String endpoint;
}
