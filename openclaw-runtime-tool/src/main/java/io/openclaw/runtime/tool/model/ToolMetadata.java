package io.openclaw.runtime.tool.model;

import io.openclaw.runtime.api.dto.ToolDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Method;

/** 工具元数据 DTO，将已发现工具的定义关联到 Spring Bean 和调用方法。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolMetadata {

    /** 工具定义（名称、描述、JSON Schema 等）。 */
    private ToolDefinition definition;
    /** 工具实现类的 Class 对象。 */
    private Class<?> targetClass;
    /** 工具实现类的 Spring Bean 实例。 */
    private Object targetBean;
    /** 工具的 invoke 方法引用。 */
    private Method invokeMethod;
    /** 工具是否已成功注册到 OpenClaw Gateway。 */
    private boolean registered;
}
