package io.openclaw.runtime.tool.registry;

import io.openclaw.runtime.api.exception.ErrorCode;
import io.openclaw.runtime.api.exception.RegisterException;
import io.openclaw.runtime.api.interceptor.LifecycleInterceptor;
import io.openclaw.runtime.tool.model.ToolMetadata;
import io.openclaw.runtime.tool.registration.ToolRegistrationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 工具注册器，处理与 OpenClaw Gateway 之间的工具注册和注销操作。
 * <p>
 * 使用可插拔的 {@link ToolRegistrationStrategy} 策略列表执行实际注册，
 * 并在注册前后调用 {@link LifecycleInterceptor} 生命周期拦截器。
 * <p>
 * 注册流程：
 * <ol>
 *   <li>对每个工具调用 {@code beforeRegisterTool} 拦截器</li>
 *   <li>依次执行所有策略的 {@code register} 方法</li>
 *   <li>更新 {@link ToolMetadata#isRegistered()} 标志</li>
 *   <li>对每个工具调用 {@code afterRegisterTool} 拦截器</li>
 * </ol>
 */
public class ToolRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistrar.class);

    private final List<ToolRegistrationStrategy> strategies;
    private final List<LifecycleInterceptor> interceptors;
    private final ToolRegistry toolRegistry;

    public ToolRegistrar(List<ToolRegistrationStrategy> strategies,
                         List<LifecycleInterceptor> interceptors,
                         ToolRegistry toolRegistry) {
        this.strategies = strategies;
        this.interceptors = interceptors;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 将给定清单中的所有工具注册到 OpenClaw Gateway。
     * <p>
     * 依次执行所有注册策略，任一策略失败不会阻止其他策略的执行。
     * 所有策略执行完毕后更新工具的注册状态并调用拦截器。
     *
     * @param manifest 包含待注册工具定义的工具清单
     * @throws RegisterException 当所有策略均执行失败时
     */
    public void registerToOpenClaw(ToolManifest manifest) {
        if (manifest == null || manifest.getTools() == null || manifest.getTools().isEmpty()) {
            log.info("No tools to register");
            return;
        }

        int toolCount = manifest.getTools().size();
        log.info("Registering {} tools to OpenClaw via {} strategies", toolCount, strategies.size());

        // Invoke beforeRegisterTool interceptors for each tool
        manifest.getTools().forEach(def -> {
            for (LifecycleInterceptor interceptor : interceptors) {
                try {
                    interceptor.beforeRegisterTool(def.getName());
                } catch (Exception e) {
                    log.warn("beforeRegisterTool interceptor failed for '{}': {}", def.getName(), e.getMessage());
                }
            }
        });

        // Execute each registration strategy
        int successCount = 0;
        for (ToolRegistrationStrategy strategy : strategies) {
            try {
                strategy.register(manifest);
                successCount++;
                log.info("Strategy '{}' registered {} tools successfully", strategy.getName(), toolCount);
            } catch (Exception e) {
                log.error("Strategy '{}' failed to register tools: {}", strategy.getName(), e.getMessage(), e);
            }
        }

        if (successCount == 0 && !strategies.isEmpty()) {
            throw new RegisterException(ErrorCode.REGISTER_FAILED,
                    "All " + strategies.size() + " registration strategies failed for " + toolCount + " tools");
        }

        // Update registered status in the registry
        manifest.getTools().forEach(def -> {
            ToolMetadata metadata = toolRegistry.get(def.getName());
            if (metadata != null) {
                metadata.setRegistered(true);
            }
        });

        // Invoke afterRegisterTool interceptors for each tool
        manifest.getTools().forEach(def -> {
            for (LifecycleInterceptor interceptor : interceptors) {
                try {
                    interceptor.afterRegisterTool(def.getName());
                } catch (Exception e) {
                    log.warn("afterRegisterTool interceptor failed for '{}': {}", def.getName(), e.getMessage());
                }
            }
        });

        log.info("Tool registration complete: {} tools registered via {}/{} strategies",
                toolCount, successCount, strategies.size());
    }

    /**
     * 从 OpenClaw Gateway 注销指定的工具。
     * <p>
     * 依次执行所有策略的注销方法，并更新工具的注册状态。
     *
     * @param toolNames 要注销的工具名称列表
     * @throws RegisterException 当所有策略均执行失败时
     */
    public void unregisterFromOpenClaw(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            log.info("No tools to unregister");
            return;
        }

        log.info("Unregistering {} tools from OpenClaw via {} strategies", toolNames.size(), strategies.size());

        // Execute each strategy's unregister
        int successCount = 0;
        for (ToolRegistrationStrategy strategy : strategies) {
            try {
                strategy.unregister(toolNames);
                successCount++;
                log.info("Strategy '{}' unregistered {} tools successfully", strategy.getName(), toolNames.size());
            } catch (Exception e) {
                log.error("Strategy '{}' failed to unregister tools: {}", strategy.getName(), e.getMessage(), e);
            }
        }

        if (successCount == 0 && !strategies.isEmpty()) {
            throw new RegisterException(ErrorCode.UNREGISTER_FAILED,
                    "All " + strategies.size() + " unregistration strategies failed for " + toolNames.size() + " tools");
        }

        // Update registered status in the registry
        for (String toolName : toolNames) {
            ToolMetadata metadata = toolRegistry.get(toolName);
            if (metadata != null) {
                metadata.setRegistered(false);
            }
        }

        log.info("Tool unregistration complete: {} tools unregistered via {}/{} strategies",
                toolNames.size(), successCount, strategies.size());
    }
}
