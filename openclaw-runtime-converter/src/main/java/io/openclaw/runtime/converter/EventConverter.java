package io.openclaw.runtime.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openclaw.runtime.api.event.ErrorEvent;
import io.openclaw.runtime.api.event.RuntimeEvent;

/** 事件 DTO 转换器，负责 JSON 和运行时表示之间的转换。 */
public class EventConverter {

    private final ObjectMapper objectMapper;

    public EventConverter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 将 JSON 节点转换为运行时 {@link RuntimeEvent}。
     *
     * @param node 事件的 JSON 表示
     * @return 转换后的 {@link RuntimeEvent}；若转换尚未实现，则返回 {@link ErrorEvent}
     */
    public RuntimeEvent toRuntimeEvent(JsonNode node) {
        // 桩实现：暂时返回 ErrorEvent
        ErrorEvent errorEvent = new ErrorEvent();
        errorEvent.setErrorCode("CONVERSION_STUB");
        errorEvent.setErrorMessage("Event conversion not yet fully implemented");
        return errorEvent;
    }

    /**
     * 将运行时 {@link RuntimeEvent} 转换为原始 JSON 节点。
     *
     * @param event 要转换的运行时事件
     * @return 事件的 JSON 表示
     */
    public JsonNode toRawEvent(RuntimeEvent event) {
        return objectMapper.valueToTree(event);
    }
}
