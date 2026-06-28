package io.openclaw.runtime.event;

import io.openclaw.runtime.api.event.ArtifactCreatedEvent;
import io.openclaw.runtime.api.event.ErrorEvent;
import io.openclaw.runtime.api.event.ReasoningEvent;
import io.openclaw.runtime.api.event.RuntimeEvent;
import io.openclaw.runtime.api.event.RuntimeStartedEvent;
import io.openclaw.runtime.api.event.RuntimeStoppedEvent;
import io.openclaw.runtime.api.event.SessionClosedEvent;
import io.openclaw.runtime.api.event.SessionCreatedEvent;
import io.openclaw.runtime.api.event.StreamingEvent;
import io.openclaw.runtime.api.event.TaskFailedEvent;
import io.openclaw.runtime.api.event.TaskFinishedEvent;
import io.openclaw.runtime.api.event.TaskStartedEvent;
import io.openclaw.runtime.api.event.ToolCallingEvent;
import io.openclaw.runtime.api.event.ToolFinishedEvent;
import io.openclaw.runtime.api.listener.RuntimeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 核心事件发布器，负责将 {@link RuntimeEvent} 实例分发给所有已注册的 {@link RuntimeListener}。 */
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final CopyOnWriteArrayList<RuntimeListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 将 {@link RuntimeEvent} 发布给所有已注册的监听器。
     *
     * @param event 要分发的事件
     */
    public void publish(RuntimeEvent event) {
        log.debug("Publishing event: {}", event.getClass().getSimpleName());

        for (RuntimeListener listener : listeners) {
            if (event instanceof RuntimeStartedEvent e) {
                listener.onRuntimeStarted(e);
            } else if (event instanceof RuntimeStoppedEvent e) {
                listener.onRuntimeStopped(e);
            } else if (event instanceof SessionCreatedEvent e) {
                listener.onSessionCreated(e);
            } else if (event instanceof SessionClosedEvent e) {
                listener.onSessionClosed(e);
            } else if (event instanceof TaskStartedEvent e) {
                listener.onTaskStarted(e);
            } else if (event instanceof TaskFinishedEvent e) {
                listener.onTaskFinished(e);
            } else if (event instanceof TaskFailedEvent e) {
                listener.onTaskFailed(e);
            } else if (event instanceof ToolCallingEvent e) {
                listener.onToolCalling(e);
            } else if (event instanceof ToolFinishedEvent e) {
                listener.onToolFinished(e);
            } else if (event instanceof ArtifactCreatedEvent e) {
                listener.onArtifactCreated(e);
            } else if (event instanceof ReasoningEvent e) {
                listener.onReasoning(e);
            } else if (event instanceof StreamingEvent e) {
                listener.onStreaming(e);
            } else if (event instanceof ErrorEvent e) {
                listener.onError(e);
            } else {
                log.warn("Unknown event type: {}", event.getClass().getSimpleName());
            }
        }
    }

    /**
     * 注册新的监听器以接收运行时事件。
     *
     * @param listener 要添加的监听器
     */
    public void addListener(RuntimeListener listener) {
        listeners.add(listener);
    }

    /**
     * 移除已注册的监听器，使其不再接收事件。
     *
     * @param listener 要移除的监听器
     */
    public void removeListener(RuntimeListener listener) {
        listeners.remove(listener);
    }

    /**
     * 返回所有当前已注册监听器的不可修改视图。
     *
     * @return 已注册监听器的不可修改列表
     */
    public List<RuntimeListener> getListeners() {
        return Collections.unmodifiableList(listeners);
    }
}
