package io.openclaw.runtime.api.event;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 所有 OpenClaw Runtime 事件的基类。 */
@Data
@NoArgsConstructor
public abstract class RuntimeEvent {

    private String eventId = UUID.randomUUID().toString();
    private Instant timestamp = Instant.now();
    private String source;
    private Map<String, Object> metadata;
}
