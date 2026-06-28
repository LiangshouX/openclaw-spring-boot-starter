package io.openclaw.runtime.api.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 任务开始执行时发布的事件。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TaskStartedEvent extends RuntimeEvent {

    private String taskId;
    private String sessionId;
    private String description;
}
