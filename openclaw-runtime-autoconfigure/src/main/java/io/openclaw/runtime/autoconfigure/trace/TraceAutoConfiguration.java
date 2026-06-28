package io.openclaw.runtime.autoconfigure.trace;

import io.openclaw.runtime.autoconfigure.OpenClawAutoConfiguration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * OpenClaw 链路追踪支持的自动配置类。
 * 当 {@code openclaw.trace.enabled} 为 true 且 Micrometer Tracing 在类路径上时激活。
 */
@AutoConfiguration(after = OpenClawAutoConfiguration.class)
@ConditionalOnProperty(prefix = "openclaw.trace", name = "enabled", havingValue = "true")
@ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
public class TraceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenClawTracer openClawTracer() {
        return new OpenClawTracer();
    }
}
