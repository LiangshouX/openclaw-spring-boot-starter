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

        long startTime = System.nanoTime();

        // Execute beforeToolCall interceptors with isolation
        for (LifecycleInterceptor interceptor : interceptors) {
            try {
                interceptor.beforeToolCall(toolName, arguments);
            } catch (Exception e) {
                throw new ToolException(ErrorCode.TOOL_INVOCATION_FAILED,
                        "Interceptor beforeToolCall failed for tool: " + toolName, e);
            }
        }

        ToolResult toolResult;
        try {
            // Invoke the tool via reflection
            Object result = metadata.getInvokeMethod().invoke(metadata.getTargetBean(), arguments);
            if (result instanceof ToolResult tr) {
                toolResult = tr;
            } else if (result == null) {
                toolResult = ToolResult.failure(toolName, "Tool returned null");
            } else {
                toolResult = ToolResult.failure(toolName,
                        "Tool returned unexpected type: " + result.getClass().getName());
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
            long executionMs = (System.nanoTime() - startTime) / 1_000_000;
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Tool '{}' invocation failed after {}ms", toolName, executionMs, cause);
            throw new ToolException(ErrorCode.TOOL_INVOCATION_FAILED,
                    "Failed to invoke tool: " + toolName, cause);
        } catch (Exception e) {
            long executionMs = (System.nanoTime() - startTime) / 1_000_000;
            log.error("Tool '{}' invocation failed after {}ms", toolName, executionMs, e);
            throw new ToolException(ErrorCode.TOOL_INVOCATION_FAILED,
                    "Failed to invoke tool: " + toolName, e);
        }

        // Execute afterToolCall interceptors with isolation
        for (LifecycleInterceptor interceptor : interceptors) {
            try {
                interceptor.afterToolCall(toolName, toolResult);
            } catch (Exception e) {
                log.error("Interceptor afterToolCall failed for tool '{}': {}",
                        toolName, e.getMessage(), e);
            }
        }

        long executionMs = (System.nanoTime() - startTime) / 1_000_000;
        log.info("Tool '{}' dispatched successfully in {}ms", toolName, executionMs);

        return toolResult;
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
