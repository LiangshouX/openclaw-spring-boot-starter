package io.openclaw.runtime.autoconfigure;

import io.openclaw.runtime.api.dto.RuntimeSession;
import io.openclaw.runtime.api.event.RuntimeStoppedEvent;
import io.openclaw.runtime.event.EventPublisher;
import io.openclaw.runtime.session.HeartbeatManager;
import io.openclaw.runtime.session.SessionManager;
import io.openclaw.runtime.skill.model.SkillMetadata;
import io.openclaw.runtime.skill.registry.SkillRegistrar;
import io.openclaw.runtime.skill.registry.SkillRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理 OpenClaw Runtime 的优雅关闭。
 * 关闭会话、注销技能，并发布 RuntimeStoppedEvent。
 */
public class OpenClawShutdownHandler implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(OpenClawShutdownHandler.class);

    private final SessionManager sessionManager;
    private final SkillRegistry skillRegistry;
    private final SkillRegistrar skillRegistrar;
    private final HeartbeatManager heartbeatManager;
    private final EventPublisher eventPublisher;

    public OpenClawShutdownHandler(SessionManager sessionManager,
                                    SkillRegistry skillRegistry,
                                    SkillRegistrar skillRegistrar,
                                    HeartbeatManager heartbeatManager,
                                    EventPublisher eventPublisher) {
        this.sessionManager = sessionManager;
        this.skillRegistry = skillRegistry;
        this.skillRegistrar = skillRegistrar;
        this.heartbeatManager = heartbeatManager;
        this.eventPublisher = eventPublisher;
    }

    /** {@inheritDoc} */
    @Override
    public void destroy() {
        log.info("Shutting down OpenClaw Runtime...");

        // 关闭活跃会话
        List<RuntimeSession> activeSessions = sessionManager.getActiveSessions();
        for (RuntimeSession session : activeSessions) {
            try {
                sessionManager.close(session.getSessionId());
                log.debug("Closed session: {}", session.getSessionId());
            } catch (Exception e) {
                log.warn("Failed to close session: {}", session.getSessionId(), e);
            }
        }

        // 注销技能
        List<String> skillNames = skillRegistry.getAll().stream()
                .map(SkillMetadata::getDefinition)
                .map(d -> d.getName())
                .collect(Collectors.toList());
        if (!skillNames.isEmpty()) {
            skillRegistrar.unregisterFromOpenClaw(skillNames);
        }

        // 关闭心跳
        heartbeatManager.shutdown();

        // 发布 RuntimeStoppedEvent
        RuntimeStoppedEvent event = new RuntimeStoppedEvent();
        event.setRuntimeId("openclaw-runtime");
        event.setReason("Application shutdown");
        eventPublisher.publish(event);

        log.info("OpenClaw Runtime shut down successfully");
    }
}
