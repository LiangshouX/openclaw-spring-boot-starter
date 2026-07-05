package io.openclaw.runtime.tool.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import io.openclaw.runtime.api.dto.ToolResult;
import io.openclaw.runtime.api.exception.ErrorCode;
import io.openclaw.runtime.api.exception.ToolException;
import io.openclaw.runtime.api.interceptor.LifecycleInterceptor;
import io.openclaw.runtime.tool.model.ToolMetadata;
import io.openclaw.runtime.tool.registry.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** 工具调度器，将传入的工具调用分派到对应的工具 Bean，并应用生命周期拦截器。 */
public class ToolDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ToolDispatcher.class);

    private final ToolRegistry toolRegistry;
    private final List<LifecycleInterceptor> interceptors;

    public ToolDispatcher(ToolRegistry toolRegistry, List<LifecycleInterceptor> interceptors) {
        this.toolRegistry = toolRegistry;
        this.interceptors = interceptors;
    }

    /**
     * 将工具调用分派到指定名称的工具，在调用前后应用生命周期拦截器。
     *
     * @param toolName  要调用的工具名称
     * @param arguments 传递给工具的 JSON 参数
     * @return 工具调用的结果
     * @throws ToolException 如果工具未找到或调用失败
     */
    public ToolResult dispatch(String toolName, JsonNode arguments) {
        ToolMetadata metadata = toolRegistry.get(toolName);
        if (metadata == null) {
            throw new ToolException(ErrorCode.TOOL_NOT_FOUND,
                    "Tool not found: " + toolName);
        }

        long startTime = System.currentTimeMillis();

        try {
            // Execute beforeToolCall interceptors
            for (LifecycleInterceptor interceptor : interceptors) {
                interceptor.beforeToolCall(toolName, arguments);
            }

            // Invoke the tool via reflection
            Object result = metadata.getInvokeMethod().invoke(metadata.getTargetBean(), arguments);
            ToolResult toolResult = (ToolResult) result;

            // Execute afterToolCall interceptors
            for (LifecycleInterceptor interceptor : interceptors) {
                interceptor.afterToolCall(toolName, toolResult);
            }

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Tool '{}' dispatched successfully in {}ms", toolName, executionTime);

            return toolResult;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Tool '{}' invocation failed after {}ms", toolName, executionTime, e);
            throw new ToolException(ErrorCode.TOOL_INVOCATION_FAILED,
                    "Failed to invoke tool: " + toolName, e);
        }
    }

    /**
     * 检查指定名称的工具是否已注册且可被分派。
     *
     * @param toolName 工具名称
     * @return 工具已注册时返回 {@code true}，否则返回 {@code false}
     */
    public boolean canDispatch(String toolName) {
        return toolRegistry.isRegistered(toolName);
    }
}
