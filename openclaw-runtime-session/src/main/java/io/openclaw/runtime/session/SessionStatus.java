package io.openclaw.runtime.session;

/** 会话生命周期状态枚举。 */
public enum SessionStatus {
    CREATED,
    ACTIVE,
    RUNNING,
    SUSPENDED,
    COMPLETED,
    CLOSED,
    EXPIRED
}
