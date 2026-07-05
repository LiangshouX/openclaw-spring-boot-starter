package io.openclaw.runtime.tool.scanner;

import com.fasterxml.jackson.databind.JsonNode;
import io.openclaw.runtime.api.dto.ToolDefinition;
import io.openclaw.runtime.api.tool.Tool;
import io.openclaw.runtime.tool.annotation.OpenClawTool;
import io.openclaw.runtime.tool.model.ToolMetadata;

import java.lang.reflect.Method;

/** 工具元数据构建器，根据 Spring Bean 及其 {@code @OpenClawTool} 注解构建 {@link ToolMetadata}。 */
public class ToolMetadataBuilder {

    /**
     * 根据给定的 Bean 及其注解构建工具元数据。
     *
     * @param bean       标注为工具的 Spring Bean 实例
     * @param annotation Bean 上的 {@code @OpenClawTool} 注解
     * @return 构建完成的工具元数据
     */
    public ToolMetadata build(Object bean, OpenClawTool annotation) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(annotation.name())
                .description(annotation.description())
                .version(annotation.version())
                .className(bean.getClass().getName())
                .build();

        Method invokeMethod = findInvokeMethod(bean.getClass());

        return ToolMetadata.builder()
                .definition(definition)
                .targetClass(bean.getClass())
                .targetBean(bean)
                .invokeMethod(invokeMethod)
                .registered(false)
                .build();
    }

    private Method findInvokeMethod(Class<?> clazz) {
        // Look for the invoke(JsonNode) method from the Tool interface
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals("invoke")
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == JsonNode.class) {
                return method;
            }
        }

        // Fallback: if the class implements Tool interface, find it through the interface
        if (Tool.class.isAssignableFrom(clazz)) {
            try {
                return clazz.getMethod("invoke", JsonNode.class);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                        "Cannot find invoke(JsonNode) method on class: " + clazz.getName(), e);
            }
        }

        throw new IllegalArgumentException(
                "Cannot find invoke(JsonNode) method on class: " + clazz.getName());
    }
}
