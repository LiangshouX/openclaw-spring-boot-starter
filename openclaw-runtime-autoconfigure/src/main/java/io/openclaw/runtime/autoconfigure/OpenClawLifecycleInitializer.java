package io.openclaw.runtime.autoconfigure;

import io.openclaw.runtime.api.event.RuntimeStartedEvent;
import io.openclaw.runtime.event.EventPublisher;
import io.openclaw.runtime.tool.model.ToolMetadata;
import io.openclaw.runtime.tool.registry.ToolRegistrar;
import io.openclaw.runtime.tool.registry.ToolRegistry;
import io.openclaw.runtime.tool.scanner.ToolScanner;
import io.openclaw.runtime.tool.schema.JsonSchemaGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;

import java.util.List;

/**
 * 应用启动时初始化 OpenClaw Runtime。
 * 扫描工具、生成 Schema、注册工具，并发布 RuntimeStartedEvent。
 */
public class OpenClawLifecycleInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OpenClawLifecycleInitializer.class);

    private final ApplicationContext applicationContext;
    private final ToolScanner toolScanner;
    private final ToolRegistry toolRegistry;
    private final JsonSchemaGenerator jsonSchemaGenerator;
    private final ToolRegistrar toolRegistrar;
    private final EventPublisher eventPublisher;
    private final OpenClawProperties properties;

    public OpenClawLifecycleInitializer(ApplicationContext applicationContext,
                                          ToolScanner toolScanner,
                                          ToolRegistry toolRegistry,
                                          JsonSchemaGenerator jsonSchemaGenerator,
                                          ToolRegistrar toolRegistrar,
                                          EventPublisher eventPublisher,
                                          OpenClawProperties properties) {
        this.applicationContext = applicationContext;
        this.toolScanner = toolScanner;
        this.toolRegistry = toolRegistry;
        this.jsonSchemaGenerator = jsonSchemaGenerator;
        this.toolRegistrar = toolRegistrar;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    /** {@inheritDoc} */
    @Override
    public void run(ApplicationArguments args) {
        log.info("Initializing OpenClaw Runtime...");

        // 扫描并注册工具
        if (properties.isAutoRegisterTool()) {
            log.info("Scanning for OpenClaw tools...");
            List<ToolMetadata> tools = toolScanner.scan(applicationContext);
            toolRegistry.registerAll(tools);
            log.info("Found {} tools", tools.size());

            // 生成 Schema（单个工具失败不影响其他工具）
            for (ToolMetadata metadata : tools) {
                try {
                    var schema = jsonSchemaGenerator.generate(metadata.getTargetClass());
                    metadata.getDefinition().setJsonSchema(schema);
                } catch (Exception e) {
                    log.error("Failed to generate schema for tool '{}', skipping",
                            metadata.getDefinition().getName(), e);
                }
            }

            // 注册到 OpenClaw
            try {
                var manifest = toolRegistry.buildManifest();
                toolRegistrar.registerToOpenClaw(manifest);
            } catch (Exception e) {
                log.error("Tool registration to OpenClaw failed — tools available locally only", e);
            }
        }

        // 发布 RuntimeStartedEvent（始终发布，即使工具注册失败）
        try {
            RuntimeStartedEvent event = new RuntimeStartedEvent();
            event.setRuntimeId("openclaw-runtime");
            event.setEndpoint(properties.getEndpoint());
            eventPublisher.publish(event);
        } catch (Exception e) {
            log.error("Failed to publish RuntimeStartedEvent", e);
        }

        log.info("OpenClaw Runtime initialized successfully");
    }
}
