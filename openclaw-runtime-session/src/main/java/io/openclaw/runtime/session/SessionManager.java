package io.openclaw.runtime.session;

import io.openclaw.runtime.api.dto.RuntimeSession;
import io.openclaw.runtime.api.exception.ErrorCode;
import io.openclaw.runtime.api.exception.SessionException;
import io.openclaw.runtime.client.http.SessionHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** 会话管理器，管理运行时会话的生命周期，包括创建、获取和关闭。 */
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final ConcurrentHashMap<String, SessionContext> sessions = new ConcurrentHashMap<>();
    private final SessionHttpClient sessionHttpClient;

    public SessionManager(SessionHttpClient sessionHttpClient) {
        this.sessionHttpClient = sessionHttpClient;
    }

    /**
     * 为指定工作空间创建新会话。
     *
     * @param workspaceId 工作空间标识符
     * @return 新创建的运行时会话
     */
    public RuntimeSession create(String workspaceId) {
        String sessionId = UUID.randomUUID().toString();
        RuntimeSession session = RuntimeSession.builder()
                .sessionId(sessionId)
                .workspaceId(workspaceId)
                .createTime(Instant.now())
                .status("CREATED")
                .build();

        SessionContext context = SessionContext.builder()
                .session(session)
                .status(SessionStatus.CREATED)
                .lastHeartbeat(Instant.now())
                .heartbeatFailures(0)
                .build();

        sessions.put(sessionId, context);
        log.info("Session created: sessionId={}, workspaceId={}", sessionId, workspaceId);
        return session;
    }

    /**
     * 根据标识符恢复已有会话。
     *
     * @param sessionId 要恢复的会话的唯一标识符
     * @return 恢复后的运行时会话
     * @throws io.openclaw.runtime.api.exception.SessionException 如果会话不存在
     */
    public RuntimeSession resume(String sessionId) {
        SessionContext context = sessions.get(sessionId);
        if (context == null) {
            throw new SessionException(ErrorCode.SESSION_NOT_FOUND,
                    "Session not found: " + sessionId);
        }
        log.info("Session resumed: sessionId={}", sessionId);
        return context.getSession();
    }

    /**
     * 关闭活跃会话。
     *
     * @param sessionId 要关闭的会话的唯一标识符
     * @throws io.openclaw.runtime.api.exception.SessionException 如果会话不存在
     */
    public void close(String sessionId) {
        SessionContext context = sessions.get(sessionId);
        if (context == null) {
            throw new SessionException(ErrorCode.SESSION_NOT_FOUND,
                    "Session not found: " + sessionId);
        }
        context.setStatus(SessionStatus.CLOSED);
        context.getSession().setStatus("CLOSED");
        log.info("Session closed: sessionId={}", sessionId);
    }

    /**
     * 根据标识符获取会话。
     *
     * @param sessionId 会话的唯一标识符
     * @return 运行时会话，如果未找到则返回 {@code null}
     */
    public RuntimeSession get(String sessionId) {
        SessionContext context = sessions.get(sessionId);
        if (context == null) {
            return null;
        }
        return context.getSession();
    }

    /**
     * 返回所有未关闭且未过期的会话。
     *
     * @return 活跃运行时会话的列表
     */
    public List<RuntimeSession> getActiveSessions() {
        return sessions.values().stream()
                .filter(context -> context.getStatus() != SessionStatus.CLOSED
                        && context.getStatus() != SessionStatus.EXPIRED)
                .map(SessionContext::getSession)
                .collect(Collectors.toList());
    }

    /**
     * 根据过期时间检查会话是否已过期。
     *
     * @param sessionId 会话的唯一标识符
     * @return 会话已过期时返回 {@code true}，否则返回 {@code false}
     */
    public boolean isExpired(String sessionId) {
        SessionContext context = sessions.get(sessionId);
        if (context == null) {
            return false;
        }
        RuntimeSession session = context.getSession();
        return session.getExpireTime() != null
                && session.getExpireTime().isBefore(Instant.now());
    }
}
