package io.openclaw.runtime.autoconfigure;

import com.fasterxml.jackson.databind.JsonNode;
import io.openclaw.runtime.event.callback.CallbackDispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接收 OpenClaw 回调的 REST 控制器。
 * 端点路径可通过 {@code openclaw.callback.path} 属性配置。
 */
@RestController
public class OpenClawCallbackController {

    private static final Logger log = LoggerFactory.getLogger(OpenClawCallbackController.class);

    private final CallbackDispatcher callbackDispatcher;
    private final OpenClawProperties properties;

    public OpenClawCallbackController(CallbackDispatcher callbackDispatcher,
                                       OpenClawProperties properties) {
        this.callbackDispatcher = callbackDispatcher;
        this.properties = properties;
    }

    /**
     * 处理传入的 OpenClaw 回调请求。
     *
     * @param payload 回调请求的 JSON 请求体
     * @return 返回 HTTP 200 状态码的空 {@link ResponseEntity}
     */
    @PostMapping("${openclaw.callback.path:/openclaw/callback}")
    public ResponseEntity<Void> handleCallback(@RequestBody JsonNode payload) {
        log.debug("Received OpenClaw callback: {}", payload);
        callbackDispatcher.dispatch(payload);
        return ResponseEntity.ok().build();
    }
}
