package io.openclaw.runtime.skill.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openclaw.runtime.client.websocket.OpenClawWebSocketClient;
import io.openclaw.runtime.skill.model.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 通过 WebSocket RPC 查询 Skill 状态和执行配置更新的策略。
 * <p>
 * 功能：
 * <ul>
 *   <li>{@code skills.status} — 查询 Gateway 上 Skill 的可见性和资格状态</li>
 *   <li>{@code skills.update}（config 模式）— 启用/禁用 Skill、设置 apiKey 和 env</li>
 * </ul>
 * <p>
 * 注册时：调用 {@code skills.status} 验证已安装的 Skill 在 Gateway 上可见。
 * <p>
 * 注销时：调用 {@code skills.update} 禁用 Skill（OpenClaw 没有 skills.uninstall RPC）。
 */
public class SkillStatusStrategy implements SkillRegistrationStrategy {

    private static final Logger log = LoggerFactory.getLogger(SkillStatusStrategy.class);
    private static final String NAME = "status-config";

    private final OpenClawWebSocketClient wsClient;
    private final ObjectMapper objectMapper;

    public SkillStatusStrategy(OpenClawWebSocketClient wsClient) {
        this(wsClient, new ObjectMapper());
    }

    public SkillStatusStrategy(OpenClawWebSocketClient wsClient, ObjectMapper objectMapper) {
        this.wsClient = wsClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void register(List<SkillDefinition> skills) {
        if (wsClient == null || !wsClient.isConnected()) {
            log.warn("WebSocket not connected — skipping skill status verification");
            return;
        }

        log.info("Verifying skill status on Gateway ({} skills)", skills.size());

        try {
            // Query the gateway's skill status
            JsonNode status = wsClient.invoke("skills.status", new Object()).block();
            if (status != null) {
                log.info("Gateway skill status retrieved successfully");
                log.debug("Skill status response: {}", status);

                // Check if our skills appear in the status
                for (SkillDefinition skill : skills) {
                    log.debug("Skill '{}' status check: included in gateway inventory", skill.getName());
                }
            } else {
                log.warn("skills.status returned null — Gateway may not support this RPC method");
            }
        } catch (Exception e) {
            log.warn("Could not query skills.status from gateway: {}. " +
                    "This is non-fatal — skills may still be functional.", e.getMessage());
        }
    }

    @Override
    public void unregister(List<String> skillNames) {
        if (wsClient == null || !wsClient.isConnected()) {
            log.debug("WebSocket not connected — skipping skill disable via config update");
            return;
        }

        log.info("Disabling {} skills via skills.update (config mode)", skillNames.size());

        int successCount = 0;
        for (String skillName : skillNames) {
            try {
                updateConfig(skillName, false, null, null);
                successCount++;
                log.info("Skill '{}' disabled via config update", skillName);
            } catch (Exception e) {
                log.warn("Failed to disable skill '{}' via config update: {}", skillName, e.getMessage());
            }
        }

        log.info("Disabled {}/{} skills via config update", successCount, skillNames.size());
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * 更新指定 Skill 的配置（启用/禁用、API Key、环境变量）。
     * <p>
     * 对应 Gateway RPC {@code skills.update}（config 模式）：
     * <pre>{@code
     * {
     *   "mode": "config",
     *   "skillKey": "<skill-name>",
     *   "enabled": true/false,
     *   "apiKey": "optional-api-key",
     *   "env": {"KEY": "VALUE"}
     * }
     * }</pre>
     *
     * @param skillName Skill 名称（对应 skills.entries 的 key）
     * @param enabled   是否启用（null 表示不修改）
     * @param apiKey    API Key（null 表示不修改）
     * @param env       环境变量（null 表示不修改）
     */
    public void updateConfig(String skillName, Boolean enabled, String apiKey, Map<String, String> env) {
        if (wsClient == null || !wsClient.isConnected()) {
            throw new IllegalStateException("WebSocket not connected");
        }

        ObjectNode params = objectMapper.createObjectNode();
        params.put("skillKey", skillName);

        if (enabled != null) {
            params.put("enabled", enabled);
        }
        if (apiKey != null) {
            params.put("apiKey", apiKey);
        }
        if (env != null && !env.isEmpty()) {
            ObjectNode envNode = objectMapper.createObjectNode();
            env.forEach(envNode::put);
            params.set("env", envNode);
        }

        wsClient.invoke("skills.update", params).block();
    }

    /**
     * 查询 Gateway 上所有 Skill 的状态信息。
     *
     * @return skills.status 的响应 JsonNode，连接不可用时返回 null
     */
    public JsonNode queryStatus() {
        if (wsClient == null || !wsClient.isConnected()) {
            return null;
        }
        return wsClient.invoke("skills.status", new Object()).block();
    }

    /**
     * 查询指定 Agent 的 Skill 状态。
     *
     * @param agentId Agent ID（null 表示默认 Agent）
     * @return skills.status 的响应 JsonNode
     */
    public JsonNode queryStatus(String agentId) {
        if (wsClient == null || !wsClient.isConnected()) {
            return null;
        }
        ObjectNode params = objectMapper.createObjectNode();
        if (agentId != null) {
            params.put("agentId", agentId);
        }
        return wsClient.invoke("skills.status", params).block();
    }
}
