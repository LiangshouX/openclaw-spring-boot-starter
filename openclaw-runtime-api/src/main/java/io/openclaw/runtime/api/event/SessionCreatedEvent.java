package io.openclaw.runtime.api.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 新会话创建时发布的事件。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SessionCreatedEvent extends RuntimeEvent {

    private String sessionId;
    private String workspaceId;
}
