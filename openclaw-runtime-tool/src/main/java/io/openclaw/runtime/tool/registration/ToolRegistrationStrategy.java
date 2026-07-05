package io.openclaw.runtime.tool.registration;

import io.openclaw.runtime.tool.registry.ToolManifest;

import java.util.List;

/**
 * 工具注册策略接口。
 * <p>
 * 定义将工具注册到 OpenClaw Gateway 以及从 Gateway 注销工具的统一契约。
 * 不同的实现可以通过不同机制（如 HTTP 请求体、WebSocket RPC、MCP 桥接等）
 * 完成工具注册。
 */
public interface ToolRegistrationStrategy {

    /**
     * 将清单中的所有工具注册到 OpenClaw Gateway。
     *
     * @param manifest 包含待注册工具定义的工具清单
     */
    void register(ToolManifest manifest);

    /**
     * 从 OpenClaw Gateway 注销指定的工具。
     *
     * @param toolNames 要注销的工具名称列表
     */
    void unregister(List<String> toolNames);

    /**
     * 返回策略的名称标识。
     *
     * @return 策略名称
     */
    String getName();
}
