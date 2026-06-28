package io.openclaw.runtime.session;

import io.openclaw.runtime.api.dto.RuntimeSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** 会话上下文 DTO，包含受管会话的完整上下文信息，包括当前状态和心跳状态。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionContext {

    private RuntimeSession session;
    private SessionStatus status;
    private Instant lastHeartbeat;
    private int heartbeatFailures;

    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();
}
