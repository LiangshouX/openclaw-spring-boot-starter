package io.openclaw.runtime.tool.registration;

import com.fasterxml.jackson.databind.JsonNode;
import io.openclaw.runtime.api.exception.ClientException;
import io.openclaw.runtime.api.exception.ErrorCode;
import io.openclaw.runtime.api.exception.RegisterException;
import io.openclaw.runtime.client.websocket.OpenClawWebSocketClient;
import io.openclaw.runtime.tool.registry.ToolManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * WebSocket RPC 工具注册策略。
 * <p>
 * 通过 WebSocket RPC 与 OpenClaw Gateway 通信，
 * 使用 {@code tools.catalog} 验证工具可见性。
 * <p>
 * <b>注意</b>：当前 OpenClaw Gateway 协议未提供明确的 {@code tools.register} RPC 方法。
 * 本策略在注册时通过 {@code tools.catalog} 查询网关已有工具目录，
 * 并将 SDK 工具定义缓存在本地供后续请求使用。
 * 当 Gateway 未来版本增加工具注册 RPC 时，可在此策略中扩展支持。
 */
public class WebSocketToolStrategy implements ToolRegistrationStrategy {

    private static final Logger log = LoggerFactory.getLogger(WebSocketToolStrategy.class);
    private static final String NAME = "websocket-rpc";

    private final OpenClawWebSocketClient wsClient;

    public WebSocketToolStrategy(OpenClawWebSocketClient wsClient) {
        this.wsClient = wsClient;
    }

    @Override
    public void register(ToolManifest manifest) {
        if (wsClient == null || !wsClient.isConnected()) {
            log.warn("WebSocket not connected — skipping Gateway-side tool registration. " +
                    "Tools will be available via chat request injection only.");
            return;
        }

        log.info("Verifying tool catalog via WebSocket RPC ({} tools in manifest)", manifest.getTools().size());

        try {
            // Query the gateway's tool catalog to verify connectivity and discover existing tools
            JsonNode catalog = wsClient.invoke("tools.catalog", new Object())
                    .onErrorMap(e -> {
                        if (e instanceof ClientException) return e;
                        return new ClientException(ErrorCode.WEBSOCKET_ERROR,
                                "Failed to query tools.catalog", e);
                    })
                    .block();

            if (catalog != null) {
                log.info("Gateway tool catalog retrieved successfully");
                log.debug("Tool catalog response: {}", catalog);
            }
        } catch (Exception e) {
            // Non-fatal: tools.catalog may not be available or may require different params
            log.warn("Could not query tools.catalog from gateway: {}. " +
                    "This is expected if the gateway doesn't support this RPC method. " +
                    "Tools will still be injected into chat requests.", e.getMessage());
        }
    }

    @Override
    public void unregister(List<String> toolNames) {
        if (wsClient == null || !wsClient.isConnected()) {
            log.debug("WebSocket not connected — skipping Gateway-side tool unregistration");
            return;
        }

        log.info("Unregistering {} tools from gateway (WS-RPC)", toolNames.size());
        // Currently a no-op on the gateway side since there's no tools.unregister RPC.
        // The tools will be removed from the chat request injection cache by the ToolRegistrar.
    }

    @Override
    public String getName() {
        return NAME;
    }
}
