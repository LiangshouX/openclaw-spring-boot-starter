package io.openclaw.runtime.autoconfigure;

import io.openclaw.runtime.api.dto.RuntimeSession;
import io.openclaw.runtime.api.event.RuntimeStoppedEvent;
import io.openclaw.runtime.event.EventPublisher;
import io.openclaw.runtime.session.HeartbeatManager;
import io.openclaw.runtime.session.SessionManager;
import io.openclaw.runtime.tool.model.ToolMetadata;
import io.openclaw.runtime.tool.registry.ToolRegistrar;
import io.openclaw.runtime.tool.registry.ToolRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理 OpenClaw Runtime 的优雅关闭。
 * 关闭会话、注销工具，并发布 RuntimeStoppedEvent。
 */
public class OpenClawShutdownHandler implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(OpenClawShutdownHandler.class);

    private final SessionManager sessionManager;
    private final ToolRegistry toolRegistry;
    private final ToolRegistrar toolRegistrar;
    private final HeartbeatManager heartbeatManager;
    private final EventPublisher eventPublisher;

    public OpenClawShutdownHandler(SessionManager sessionManager,
                                    ToolRegistry toolRegistry,
                                    ToolRegistrar toolRegistrar,
                                    HeartbeatManager heartbeatManager,
                                    EventPublisher eventPublisher) {
        this.sessionManager = sessionManager;
        this.toolRegistry = toolRegistry;
        this.toolRegistrar = toolRegistrar;
        this.heartbeatManager = heartbeatManager;
        this.eventPublisher = eventPublisher;
    }

    /** {@inheritDoc} */
    @Override
    public void destroy() {
        log.info("Shutting down OpenClaw Runtime...");

        // 关闭活跃会话
        try {
            List<RuntimeSession> activeSessions = sessionManager.getActiveSessions();
            for (RuntimeSession session : activeSessions) {
                try {
                    sessionManager.close(session.getSessionId());
                    log.debug("Closed session: {}", session.getSessionId());
                } catch (Exception e) {
                    log.warn("Failed to close session: {}", session.getSessionId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to enumerate active sessions during shutdown", e);
        }

        // 注销工具（仅当 ToolRegistrar 可用时）
        if (toolRegistrar != null) {
            try {
                List<String> toolNames = toolRegistry.getAll().stream()
                        .map(ToolMetadata::getDefinition)
                        .map(d -> d.getName())
                        .collect(Collectors.toList());
                if (!toolNames.isEmpty()) {
                    toolRegistrar.unregisterFromOpenClaw(toolNames);
                }
            } catch (Exception e) {
                log.error("Failed to unregister tools during shutdown — continuing cleanup", e);
            }
        }

        // 关闭心跳
        try {
            heartbeatManager.shutdown();
        } catch (Exception e) {
            log.error("Failed to shutdown heartbeat manager", e);
        }

        // 发布 RuntimeStoppedEvent
        try {
            RuntimeStoppedEvent event = new RuntimeStoppedEvent();
            event.setRuntimeId("openclaw-runtime");
            event.setReason("Application shutdown");
            eventPublisher.publish(event);
        } catch (Exception e) {
            log.error("Failed to publish RuntimeStoppedEvent", e);
        }

        log.info("OpenClaw Runtime shut down successfully");
    }
}
