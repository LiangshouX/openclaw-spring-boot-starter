package io.openclaw.runtime.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 表示与 OpenClaw 的活跃运行时会话。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeSession {

    private String sessionId;
    private String conversationId;
    private String taskId;
    private String workspaceId;
    private String runtimeId;
    private Instant createTime;
    private Instant expireTime;
    private String status;
}
