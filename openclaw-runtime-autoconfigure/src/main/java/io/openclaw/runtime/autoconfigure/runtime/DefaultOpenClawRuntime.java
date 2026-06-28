package io.openclaw.runtime.autoconfigure.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.openclaw.runtime.api.OpenClawRuntime;
import io.openclaw.runtime.api.dto.ChatRequest;
import io.openclaw.runtime.api.dto.ChatResponse;
import io.openclaw.runtime.api.dto.RuntimeSession;
import io.openclaw.runtime.api.event.RuntimeEvent;
import io.openclaw.runtime.api.listener.RuntimeListener;
import io.openclaw.runtime.client.OpenClawClient;
import io.openclaw.runtime.event.EventPublisher;
import io.openclaw.runtime.session.SessionManager;
import io.openclaw.runtime.skill.registry.SkillRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * {@link OpenClawRuntime} 的默认实现。
 * 委托给内部管理器和客户端进行操作。
 */
public class DefaultOpenClawRuntime implements OpenClawRuntime {

    private static final Logger log = LoggerFactory.getLogger(DefaultOpenClawRuntime.class);

    private final SessionManager sessionManager;
    private final OpenClawClient openClawClient;
    private final SkillRegistry skillRegistry;
    private final EventPublisher eventPublisher;

    public DefaultOpenClawRuntime(SessionManager sessionManager,
                                   OpenClawClient openClawClient,
                                   SkillRegistry skillRegistry,
                                   EventPublisher eventPublisher) {
        this.sessionManager = sessionManager;
        this.openClawClient = openClawClient;
        this.skillRegistry = skillRegistry;
        this.eventPublisher = eventPublisher;
    }

    /** {@inheritDoc} */
    @Override
    public ChatResponse chat(ChatRequest request) {
        log.debug("Sending chat request: {}", request.getSessionId());
        return openClawClient.getChatClient().sendMessage(request);
    }

    /** {@inheritDoc} */
    @Override
    public Flux<RuntimeEvent> stream(ChatRequest request) {
        log.debug("Opening stream for request: {}", request.getSessionId());
        return openClawClient.getChatClient()
                .streamMessage(request)
                .map(delta -> {
                    io.openclaw.runtime.api.event.StreamingEvent event =
                            new io.openclaw.runtime.api.event.StreamingEvent();
                    event.setSessionId(request.getSessionId());
                    event.setDelta(delta);
                    event.setDone(false);
                    return (RuntimeEvent) event;
                });
    }

    /** {@inheritDoc} */
    @Override
    public RuntimeSession createSession() {
        log.info("Creating new session");
        return sessionManager.create(null);
    }

    /** {@inheritDoc} */
    @Override
    public RuntimeSession resumeSession(String sessionId) {
        log.info("Resuming session: {}", sessionId);
        return sessionManager.resume(sessionId);
    }

    /** {@inheritDoc} */
    @Override
    public void closeSession(String sessionId) {
        log.info("Closing session: {}", sessionId);
        sessionManager.close(sessionId);
    }

    /** {@inheritDoc} */
    @Override
    public void registerSkill() {
        log.info("Manual skill registration triggered");
        var manifest = skillRegistry.buildManifest();
        log.info("Built skill manifest with {} skills", manifest.getSkills().size());
    }

    /** {@inheritDoc} */
    @Override
    public void addListener(RuntimeListener listener) {
        log.debug("Adding runtime listener: {}", listener.getClass().getName());
        eventPublisher.addListener(listener);
    }
}
