package io.openclaw.runtime.tool.scanner;

import io.openclaw.runtime.tool.annotation.OpenClawTool;
import io.openclaw.runtime.tool.model.ToolMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 工具扫描器，扫描 Spring ApplicationContext 中标注了 {@code @OpenClawTool} 的 Bean 并构建元数据。 */
public class ToolScanner {

    private static final Logger log = LoggerFactory.getLogger(ToolScanner.class);

    private final ToolMetadataBuilder metadataBuilder;

    public ToolScanner(ToolMetadataBuilder metadataBuilder) {
        this.metadataBuilder = metadataBuilder;
    }

    /**
     * 扫描给定的应用上下文，查找标注了工具注解的 Bean 并构建其元数据。
     *
     * @param context 要扫描的 Spring 应用上下文
     * @return 已发现的工具元数据列表
     */
    public List<ToolMetadata> scan(ApplicationContext context) {
        List<ToolMetadata> metadataList = new ArrayList<>();
        Map<String, Object> beans = context.getBeansWithAnnotation(OpenClawTool.class);

        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            // Search the bean class and its superclasses for the annotation
            OpenClawTool annotation = findAnnotation(bean.getClass());
            if (annotation == null) {
                log.debug("Bean '{}' has no @OpenClawTool annotation on class or superclasses, skipping",
                        entry.getKey());
                continue;
            }

            ToolMetadata metadata = metadataBuilder.build(bean, annotation);
            metadataList.add(metadata);
            log.info("Discovered tool: name={}, class={}", annotation.name(), bean.getClass().getName());
        }

        return metadataList;
    }

    private OpenClawTool findAnnotation(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            OpenClawTool annotation = current.getAnnotation(OpenClawTool.class);
            if (annotation != null) {
                return annotation;
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
