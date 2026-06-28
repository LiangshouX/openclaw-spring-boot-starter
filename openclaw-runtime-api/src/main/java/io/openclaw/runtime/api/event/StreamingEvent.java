package io.openclaw.runtime.api.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 流式响应中每个数据块对应的事件。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class StreamingEvent extends RuntimeEvent {

    private String sessionId;
    private String delta;
    private boolean done;
}
