package io.openclaw.runtime.autoconfigure.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenClaw Runtime 操作链路追踪器。
 * 为 Runtime → Session → Task → Tool → HTTP 调用链创建 Span。
 * <p>
 * 此为桩实现。正式实现将使用 Micrometer Tracing / OTEL。
 */
public class OpenClawTracer {

    private static final Logger log = LoggerFactory.getLogger(OpenClawTracer.class);

    /**
     * 为运行时操作开始一个新的追踪 Span。
     *
     * @param operationName 操作名称
     * @param sessionId     会话 ID
     */
    public void startSpan(String operationName, String sessionId) {
        log.debug("Trace: start span '{}' for session '{}'", operationName, sessionId);
    }

    /**
     * 结束当前追踪 Span。
     *
     * @param operationName 操作名称
     */
    public void endSpan(String operationName) {
        log.debug("Trace: end span '{}'", operationName);
    }

    /**
     * 在当前 Span 中记录错误。
     *
     * @param operationName 操作名称
     * @param error         发生的错误
     */
    public void recordError(String operationName, Throwable error) {
        log.debug("Trace: error in span '{}': {}", operationName, error.getMessage());
    }

    /**
     * 为当前 Span 添加属性。
     *
     * @param key   属性键
     * @param value 属性值
     */
    public void addAttribute(String key, String value) {
        log.trace("Trace: attribute {}={}", key, value);
    }
}
