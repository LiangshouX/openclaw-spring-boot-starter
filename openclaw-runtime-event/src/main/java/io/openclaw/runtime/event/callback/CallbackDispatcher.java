package io.openclaw.runtime.event.callback;

import com.fasterxml.jackson.databind.JsonNode;
import io.openclaw.runtime.api.event.RuntimeEvent;
import io.openclaw.runtime.event.EventPublisher;

/** 回调调度器，负责解析传入的回调载荷并将其转换为事件，通过 {@link EventPublisher} 发布。 */
public class CallbackDispatcher {

    private final EventPublisher eventPublisher;
    private final CallbackPayloadParser callbackPayloadParser;

    public CallbackDispatcher(EventPublisher eventPublisher, CallbackPayloadParser callbackPayloadParser) {
        this.eventPublisher = eventPublisher;
        this.callbackPayloadParser = callbackPayloadParser;
    }

    /**
     * 解析回调载荷为 {@link RuntimeEvent} 并发布。
     *
     * @param payload 从 OpenClaw Gateway 接收的原始 JSON 载荷
     */
    public void dispatch(JsonNode payload) {
        RuntimeEvent event = callbackPayloadParser.parse(payload);
        eventPublisher.publish(event);
    }
}
