package io.openclaw.runtime.api.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 任务执行失败时发布的事件。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TaskFailedEvent extends RuntimeEvent {

    private String taskId;
    private String sessionId;
    private String errorCode;
    private String errorMessage;
}
