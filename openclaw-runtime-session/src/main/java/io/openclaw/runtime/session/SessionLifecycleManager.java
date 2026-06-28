package io.openclaw.runtime.session;

import io.openclaw.runtime.api.exception.ErrorCode;
import io.openclaw.runtime.api.exception.SessionException;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** 会话生命周期管理器，根据定义的生命周期规则验证会话状态转换。 */
public class SessionLifecycleManager {

    private static final Map<SessionStatus, Set<SessionStatus>> VALID_TRANSITIONS;

    static {
        VALID_TRANSITIONS = Map.of(
                SessionStatus.CREATED, EnumSet.of(SessionStatus.ACTIVE, SessionStatus.CLOSED),
                SessionStatus.ACTIVE, EnumSet.of(SessionStatus.RUNNING, SessionStatus.SUSPENDED, SessionStatus.CLOSED),
                SessionStatus.RUNNING, EnumSet.of(SessionStatus.SUSPENDED, SessionStatus.COMPLETED, SessionStatus.CLOSED),
                SessionStatus.SUSPENDED, EnumSet.of(SessionStatus.RUNNING, SessionStatus.ACTIVE, SessionStatus.CLOSED),
                SessionStatus.COMPLETED, EnumSet.of(SessionStatus.CLOSED),
                SessionStatus.CLOSED, Collections.emptySet(),
                SessionStatus.EXPIRED, EnumSet.of(SessionStatus.CLOSED)
        );
    }

    /**
     * 验证会话状态转换是否被允许。
     *
     * @param from 当前会话状态
     * @param to 目标会话状态
     * @throws io.openclaw.runtime.api.exception.SessionException 如果转换不合法
     */
    public void validateTransition(SessionStatus from, SessionStatus to) {
        if (!isValidTransition(from, to)) {
            throw new SessionException(ErrorCode.SESSION_CLOSED,
                    "Invalid session state transition from " + from + " to " + to);
        }
    }

    /**
     * 检查会话状态转换是否被允许。
     *
     * @param from 当前会话状态
     * @param to 目标会话状态
     * @return 转换合法时返回 {@code true}，否则返回 {@code false}
     */
    public boolean isValidTransition(SessionStatus from, SessionStatus to) {
        Set<SessionStatus> validTargets = VALID_TRANSITIONS.get(from);
        return validTargets != null && validTargets.contains(to);
    }
}
