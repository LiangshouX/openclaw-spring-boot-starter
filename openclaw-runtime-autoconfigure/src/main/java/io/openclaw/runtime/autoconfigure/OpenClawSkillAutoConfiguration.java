package io.openclaw.runtime.autoconfigure;

import io.openclaw.runtime.client.http.SessionHttpClient;
import io.openclaw.runtime.event.EventPublisher;
import io.openclaw.runtime.skill.registry.SkillRegistrar;
import io.openclaw.runtime.skill.registry.SkillRegistry;
import io.openclaw.runtime.skill.scanner.SkillScanner;
import io.openclaw.runtime.skill.schema.JsonSchemaGenerator;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * OpenClaw 技能注册自动配置类。
 * 当 {@code openclaw.auto-register-skill} 为 true（默认值）时激活。
 */
@AutoConfiguration(after = OpenClawAutoConfiguration.class)
@ConditionalOnProperty(prefix = "openclaw", name = "auto-register-skill", havingValue = "true", matchIfMissing = true)
public class OpenClawSkillAutoConfiguration {

    /** 创建 {@link JsonSchemaGenerator} Bean，用于从技能类生成 JSON Schema。 */
    @Bean
    @ConditionalOnMissingBean
    public JsonSchemaGenerator jsonSchemaGenerator() {
        return new JsonSchemaGenerator();
    }

    /** 创建 {@link SkillRegistrar} Bean，用于向 OpenClaw Gateway 注册技能。 */
    @Bean
    @ConditionalOnMissingBean
    public SkillRegistrar skillRegistrar(SessionHttpClient sessionHttpClient) {
        return new SkillRegistrar(sessionHttpClient);
    }

    /** 创建 {@link OpenClawLifecycleInitializer} Bean，用于在应用启动时引导运行时。 */
    @Bean
    @ConditionalOnMissingBean
    public OpenClawLifecycleInitializer openClawLifecycleInitializer(
            ApplicationContext applicationContext,
            SkillScanner skillScanner,
            SkillRegistry skillRegistry,
            JsonSchemaGenerator jsonSchemaGenerator,
            SkillRegistrar skillRegistrar,
            EventPublisher eventPublisher,
            OpenClawProperties properties) {
        return new OpenClawLifecycleInitializer(applicationContext, skillScanner,
                skillRegistry, jsonSchemaGenerator, skillRegistrar, eventPublisher, properties);
    }
}
