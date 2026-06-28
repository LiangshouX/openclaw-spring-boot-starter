package io.openclaw.runtime.autoconfigure;

import io.openclaw.runtime.api.event.RuntimeStartedEvent;
import io.openclaw.runtime.event.EventPublisher;
import io.openclaw.runtime.skill.model.SkillMetadata;
import io.openclaw.runtime.skill.registry.SkillRegistrar;
import io.openclaw.runtime.skill.registry.SkillRegistry;
import io.openclaw.runtime.skill.scanner.SkillScanner;
import io.openclaw.runtime.skill.schema.JsonSchemaGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;

import java.util.List;

/**
 * 应用启动时初始化 OpenClaw Runtime。
 * 扫描技能、生成 Schema、注册技能，并发布 RuntimeStartedEvent。
 */
public class OpenClawLifecycleInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OpenClawLifecycleInitializer.class);

    private final ApplicationContext applicationContext;
    private final SkillScanner skillScanner;
    private final SkillRegistry skillRegistry;
    private final JsonSchemaGenerator jsonSchemaGenerator;
    private final SkillRegistrar skillRegistrar;
    private final EventPublisher eventPublisher;
    private final OpenClawProperties properties;

    public OpenClawLifecycleInitializer(ApplicationContext applicationContext,
                                          SkillScanner skillScanner,
                                          SkillRegistry skillRegistry,
                                          JsonSchemaGenerator jsonSchemaGenerator,
                                          SkillRegistrar skillRegistrar,
                                          EventPublisher eventPublisher,
                                          OpenClawProperties properties) {
        this.applicationContext = applicationContext;
        this.skillScanner = skillScanner;
        this.skillRegistry = skillRegistry;
        this.jsonSchemaGenerator = jsonSchemaGenerator;
        this.skillRegistrar = skillRegistrar;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    /** {@inheritDoc} */
    @Override
    public void run(ApplicationArguments args) {
        log.info("Initializing OpenClaw Runtime...");

        // 扫描并注册技能
        if (properties.isAutoRegisterSkill()) {
            log.info("Scanning for OpenClaw skills...");
            List<SkillMetadata> skills = skillScanner.scan(applicationContext);
            skillRegistry.registerAll(skills);
            log.info("Found {} skills", skills.size());

            // 生成 Schema
            for (SkillMetadata metadata : skills) {
                var schema = jsonSchemaGenerator.generate(metadata.getTargetClass());
                metadata.getDefinition().setJsonSchema(schema);
            }

            // 注册到 OpenClaw
            var manifest = skillRegistry.buildManifest();
            skillRegistrar.registerToOpenClaw(manifest);
        }

        // 发布 RuntimeStartedEvent
        RuntimeStartedEvent event = new RuntimeStartedEvent();
        event.setRuntimeId("openclaw-runtime");
        event.setEndpoint(properties.getEndpoint());
        eventPublisher.publish(event);

        log.info("OpenClaw Runtime initialized successfully");
    }
}
