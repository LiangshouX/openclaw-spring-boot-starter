package io.openclaw.runtime.sample.listener;

import io.openclaw.runtime.api.event.*;
import io.openclaw.runtime.api.listener.RuntimeListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 示例运行时监听器，记录关键生命周期事件。
 */
@Component
public class SampleRuntimeListener implements RuntimeListener {

    private static final Logger log = LoggerFactory.getLogger(SampleRuntimeListener.class);

    @Override
    public void onRuntimeStarted(RuntimeStartedEvent event) {
        log.info("Runtime started: id={}, endpoint={}", event.getRuntimeId(), event.getEndpoint());
    }

    @Override
    public void onRuntimeStopped(RuntimeStoppedEvent event) {
        log.info("Runtime stopped: id={}, reason={}", event.getRuntimeId(), event.getReason());
    }

    @Override
    public void onSessionCreated(SessionCreatedEvent event) {
        log.info("Session created: id={}, workspace={}", event.getSessionId(), event.getWorkspaceId());
    }

    @Override
    public void onSessionClosed(SessionClosedEvent event) {
        log.info("Session closed: id={}, reason={}", event.getSessionId(), event.getReason());
    }

    @Override
    public void onTaskStarted(TaskStartedEvent event) {
        log.info("Task started: id={}, session={}", event.getTaskId(), event.getSessionId());
    }

    @Override
    public void onTaskFinished(TaskFinishedEvent event) {
        log.info("Task finished: id={}, session={}", event.getTaskId(), event.getSessionId());
    }

    @Override
    public void onTaskFailed(TaskFailedEvent event) {
        log.error("Task failed: id={}, session={}, error={}:{}",
                event.getTaskId(), event.getSessionId(),
                event.getErrorCode(), event.getErrorMessage());
    }

    @Override
    public void onError(ErrorEvent event) {
        log.error("Runtime error: code={}, message={}", event.getErrorCode(), event.getErrorMessage());
    }
}
