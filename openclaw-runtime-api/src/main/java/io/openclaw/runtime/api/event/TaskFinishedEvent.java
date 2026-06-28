package io.openclaw.runtime.api.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 任务成功完成时发布的事件。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TaskFinishedEvent extends RuntimeEvent {

    private String taskId;
    private String sessionId;
    private String result;
}
