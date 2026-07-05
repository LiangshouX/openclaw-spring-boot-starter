package io.openclaw.runtime.tool.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 工具注解，标记 Spring 组件为 OpenClaw 工具，将被自动发现、生成 Schema 并注册。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface OpenClawTool {
    String name();
    String description() default "";
    String version() default "1.0";
}
