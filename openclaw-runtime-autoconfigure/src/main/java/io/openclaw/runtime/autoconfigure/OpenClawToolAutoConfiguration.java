package io.openclaw.runtime.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openclaw.runtime.api.interceptor.LifecycleInterceptor;
import io.openclaw.runtime.client.http.ChatClient;
import io.openclaw.runtime.client.websocket.OpenClawWebSocketClient;
import io.openclaw.runtime.event.EventPublisher;
import io.openclaw.runtime.tool.registry.ToolRegistrar;
import io.openclaw.runtime.tool.registry.ToolRegistry;
import io.openclaw.runtime.tool.registration.ChatRequestToolStrategy;
import io.openclaw.runtime.tool.registration.ToolRegistrationStrategy;
import io.openclaw.runtime.tool.registration.WebSocketToolStrategy;
import io.openclaw.runtime.tool.scanner.ToolScanner;
import io.openclaw.runtime.tool.schema.JsonSchemaGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * OpenClaw 工具注册自动配置类。
 * 当 {@code openclaw.auto-register-tool} 为 true（默认值）时激活。
 * <p>
 * 创建工具注册策略、注册器和生命周期初始化器：
 * <ul>
 *   <li>{@link ChatRequestToolStrategy} — 将工具定义注入到聊天请求的 {@code tools} 字段</li>
 *   <li>{@link WebSocketToolStrategy} — 通过 WebSocket RPC 验证工具目录</li>
 *   <li>{@link ToolRegistrar} — 组合策略执行注册和注销</li>
 * </ul>
 */
@AutoConfiguration(after = OpenClawAutoConfiguration.class)
@ConditionalOnProperty(prefix = "openclaw", name = "auto-register-tool", havingValue = "true", matchIfMissing = true)
public class OpenClawToolAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenClawToolAutoConfiguration.class);

    /** 创建 {@link JsonSchemaGenerator} Bean，用于从工具类生成 JSON Schema。 */
    @Bean
    @ConditionalOnMissingBean
    public JsonSchemaGenerator jsonSchemaGenerator() {
        return new JsonSchemaGenerator();
    }

    /** 创建 {@link ChatRequestToolStrategy} Bean，缓存工具定义供聊天请求注入。 */
    @Bean
    @ConditionalOnMissingBean
    public ChatRequestToolStrategy chatRequestToolStrategy(ObjectMapper objectMapper) {
        return new ChatRequestToolStrategy(objectMapper);
    }

    /** 创建 {@link WebSocketToolStrategy} Bean，通过 WebSocket RPC 与 Gateway 通信。 */
    @Bean
    @ConditionalOnMissingBean
    public WebSocketToolStrategy webSocketToolStrategy(OpenClawWebSocketClient webSocketClient) {
        return new WebSocketToolStrategy(webSocketClient);
    }

    /** 创建 {@link ToolRegistrar} Bean，组合策略执行工具注册和注销。 */
    @Bean
    @ConditionalOnMissingBean
    public ToolRegistrar toolRegistrar(List<ToolRegistrationStrategy> strategies,
                                       List<LifecycleInterceptor> interceptors,
                                       ToolRegistry toolRegistry) {
        return new ToolRegistrar(strategies, interceptors, toolRegistry);
    }

    /** 创建 {@link OpenClawLifecycleInitializer} Bean，用于在应用启动时引导运行时。 */
    @Bean
    @ConditionalOnMissingBean
    public OpenClawLifecycleInitializer openClawLifecycleInitializer(
            ApplicationContext applicationContext,
            ToolScanner toolScanner,
            ToolRegistry toolRegistry,
            JsonSchemaGenerator jsonSchemaGenerator,
            ToolRegistrar toolRegistrar,
            EventPublisher eventPublisher,
            OpenClawProperties properties) {
        return new OpenClawLifecycleInitializer(applicationContext, toolScanner,
                toolRegistry, jsonSchemaGenerator, toolRegistrar, eventPublisher, properties);
    }

    /**
     * 在所有 Bean 初始化完成后，将 {@link ChatRequestToolStrategy} 注入到 {@link ChatClient}，
     * 使每次聊天请求自动携带已注册的工具定义。
     */
    @Bean
    public SmartInitializingSingleton chatClientToolProviderInitializer(
            ChatClient chatClient,
            ChatRequestToolStrategy chatRequestToolStrategy) {
        return () -> {
            chatClient.setToolProvider(chatRequestToolStrategy::getOpenAIToolsArray);
            log.info("Wired ChatRequestToolStrategy into ChatClient for automatic tool injection");
        };
    }
}
