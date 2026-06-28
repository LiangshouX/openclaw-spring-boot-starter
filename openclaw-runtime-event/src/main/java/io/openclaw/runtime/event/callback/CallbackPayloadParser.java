package io.openclaw.runtime.event.callback;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 将原始 JSON 回调载荷解析为对应的 {@link RuntimeEvent} 子类型。 */
public class CallbackPayloadParser {

    private static final Logger log = LoggerFactory.getLogger(CallbackPayloadParser.class);

    /**
     * 将原始 JSON 回调载荷解析为 {@link RuntimeEvent}。
     *
     * @param payload 从 OpenClaw Gateway 接收的 JSON 载荷
     * @return 解析后的 {@link RuntimeEvent} 子类型；若无法识别载荷，则返回 {@link io.openclaw.runtime.api.event.ErrorEvent}
     */
    public RuntimeEvent parse(JsonNode payload) {
        String eventType = payload.has("eventType") ? payload.get("eventType").asText() : null;

        if (eventType == null) {
            log.warn("No eventType found in payload, returning ErrorEvent");
            ErrorEvent errorEvent = new ErrorEvent();
            errorEvent.setErrorCode("MISSING_EVENT_TYPE");
            errorEvent.setErrorMessage("No eventType field found in callback payload");
            return errorEvent;
        }

        switch (eventType) {
            case "RUNTIME_STARTED":
                return payloadToObject(payload, new RuntimeStartedEvent());
            case "RUNTIME_STOPPED":
                return payloadToObject(payload, new RuntimeStoppedEvent());
            case "SESSION_CREATED":
                return payloadToObject(payload, new SessionCreatedEvent());
            case "SESSION_CLOSED":
                return payloadToObject(payload, new SessionClosedEvent());
            case "TASK_STARTED":
                return payloadToObject(payload, new TaskStartedEvent());
            case "TASK_FINISHED":
                return payloadToObject(payload, new TaskFinishedEvent());
            case "TASK_FAILED":
                return payloadToObject(payload, new TaskFailedEvent());
            case "TOOL_CALLING":
                return payloadToObject(payload, new ToolCallingEvent());
            case "TOOL_FINISHED":
                return payloadToObject(payload, new ToolFinishedEvent());
            case "ARTIFACT_CREATED":
                return payloadToObject(payload, new ArtifactCreatedEvent());
            case "REASONING":
                return payloadToObject(payload, new ReasoningEvent());
            case "STREAMING":
                return payloadToObject(payload, new StreamingEvent());
            case "ERROR":
                return payloadToObject(payload, new ErrorEvent());
            default:
                log.warn("Unknown eventType: {}, returning ErrorEvent", eventType);
                ErrorEvent errorEvent = new ErrorEvent();
                errorEvent.setErrorCode("UNKNOWN_EVENT_TYPE");
                errorEvent.setErrorMessage("Unknown event type: " + eventType);
                return errorEvent;
        }
    }

    private <T extends RuntimeEvent> T payloadToObject(JsonNode payload, T event) {
        if (payload.has("source")) {
            event.setSource(payload.get("source").asText(null));
        }
        return event;
    }
}
