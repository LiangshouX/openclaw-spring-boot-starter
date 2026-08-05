package io.openclaw.runtime.autoconfigure;

import io.openclaw.runtime.api.OpenClawRuntime;
import io.openclaw.runtime.api.interceptor.LifecycleInterceptor;
import io.openclaw.runtime.client.OpenClawClient;
import io.openclaw.runtime.client.http.*;
import io.openclaw.runtime.client.interceptor.AuthInterceptor;
import io.openclaw.runtime.client.interceptor.LoggingInterceptor;
import io.openclaw.runtime.client.interceptor.RequestInterceptor;
import io.openclaw.runtime.client.websocket.OpenClawWebSocketClient;
import io.openclaw.runtime.converter.*;
import io.openclaw.runtime.event.EventPublisher;
import io.openclaw.runtime.session.HeartbeatManager;
import io.openclaw.runtime.session.SessionLifecycleManager;
import io.openclaw.runtime.session.SessionManager;
import io.openclaw.runtime.tool.dispatcher.ToolDispatcher;
import io.openclaw.runtime.tool.registry.ToolRegistry;
import io.openclaw.runtime.tool.scanner.ToolMetadataBuilder;
import io.openclaw.runtime.tool.scanner.ToolScanner;
import io.openclaw.runtime.autoconfigure.runtime.DefaultOpenClawRuntime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;

/**
 * OpenClaw Runtime SDK 核心自动配置类。
 * 当设置了 {@code openclaw.endpoint} 属性时激活。
 */
@AutoConfiguration
@EnableConfigurationProperties(OpenClawProperties.class)
@ConditionalOnProperty(prefix = "openclaw", name = "endpoint")
public class OpenClawAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenClawAutoConfiguration.class);

    /** 创建支持 Java Time 模块的 Jackson {@link ObjectMapper} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /** 创建配置了 OpenClaw 端点、认证令牌和超时时间的 {@link WebClient} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public WebClient openClawWebClient(OpenClawProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getEndpoint())
                .defaultHeader("Authorization", "Bearer " + properties.getToken())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    /** 创建用于注入授权头的 {@link AuthInterceptor} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public AuthInterceptor authInterceptor(OpenClawProperties properties) {
        return new AuthInterceptor(properties.getToken());
    }

    /** 创建用于 HTTP 请求/响应日志记录的 {@link LoggingInterceptor} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public LoggingInterceptor loggingInterceptor() {
        return new LoggingInterceptor();
    }

    /**
     * 创建用于向 OpenClaw 发送聊天消息的 {@link ChatClient} Bean。
     * 通过 HTTP {@code /v1/chat/completions} 端点发送请求（shared-secret auth 自动获得完整 operator 权限）。
     * <p>
     * 如果配置了 {@code openclaw.agent-id}，则设置为默认 Agent ID 用于路由请求。
     */
    @Bean
    @ConditionalOnMissingBean
    public ChatClient chatClient(WebClient openClawWebClient, ObjectMapper objectMapper,
                                 OpenClawProperties properties) {
        ChatClient client = new ChatClient(openClawWebClient, objectMapper);
        client.setDefaultAgentId(properties.getAgentId());
        return client;
    }

    /** 创建用于管理 OpenClaw 任务的 {@link TaskClient} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public TaskClient taskClient(WebClient openClawWebClient,
                                 OpenClawWebSocketClient openClawWebSocketClient,
                                 ObjectMapper objectMapper) {
        return new TaskClient(openClawWebClient, openClawWebSocketClient, objectMapper);
    }

    /** 创建用于会话操作的 {@link SessionHttpClient} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public SessionHttpClient sessionHttpClient(WebClient openClawWebClient,
                                               OpenClawWebSocketClient openClawWebSocketClient,
                                               ObjectMapper objectMapper) {
        return new SessionHttpClient(openClawWebClient, openClawWebSocketClient, objectMapper);
    }

    /** 创建用于向 OpenClaw 提交事件的 {@link EventClient} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public EventClient eventClient(WebClient openClawWebClient,
                                   OpenClawWebSocketClient openClawWebSocketClient,
                                   ObjectMapper objectMapper) {
        return new EventClient(openClawWebClient, openClawWebSocketClient, objectMapper);
    }

    /** 创建用于向 OpenClaw 上传文件的 {@link UploadClient} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public UploadClient uploadClient(WebClient openClawWebClient, OpenClawProperties properties) {
        return new UploadClient(openClawWebClient, properties.getUploadMaxSizeBytes());
    }

    /** 创建用于管理 OpenClaw 制品的 {@link ArtifactClient} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public ArtifactClient artifactClient(WebClient openClawWebClient,
                                         OpenClawWebSocketClient openClawWebSocketClient,
                                         ObjectMapper objectMapper) {
        return new ArtifactClient(openClawWebClient, openClawWebSocketClient, objectMapper);
    }

    /** 创建用于 WebSocket 连接 OpenClaw 的 {@link OpenClawWebSocketClient} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public OpenClawWebSocketClient openClawWebSocketClient(OpenClawProperties properties,
                                                           ObjectMapper objectMapper) {
        String wsEndpoint = properties.getWebsocket().getEndpoint();
        if (wsEndpoint == null || wsEndpoint.isBlank()) {
            // Derive WS endpoint from HTTP endpoint: http→ws, https→wss
            String httpEndpoint = properties.getEndpoint();
            if (httpEndpoint.startsWith("http://")) {
                throw new IllegalStateException(
                        "OpenClaw endpoint must use HTTPS for secure token transmission. " +
                        "Current endpoint: " + httpEndpoint +
                        ". Set openclaw.endpoint to an https:// URL or explicitly configure " +
                        "openclaw.websocket.endpoint to a wss:// URL.");
            }
            wsEndpoint = httpEndpoint
                    .replaceFirst("^https://", "wss://");
        } else if (wsEndpoint.startsWith("ws://") && properties.getToken() != null
                && !properties.getToken().isBlank()) {
            log.warn("WebSocket endpoint uses unencrypted ws:// with authentication token. " +
                    "Token transmission is insecure. Consider using wss:// endpoint.");
        }
        return new OpenClawWebSocketClient(
                wsEndpoint,
                properties.getToken(),
                objectMapper,
                properties.getWebsocket().getConnectTimeout(),
                properties.getWebsocket().getRequestTimeout(),
                properties.getWebsocket().getProtocolVersion(),
                properties.getWebsocket().getMaxReconnectAttempts());
    }

    /** 创建 {@link OpenClawClient} 门面 Bean，聚合所有领域专用的 HTTP/WebSocket 客户端。 */
    @Bean
    @ConditionalOnMissingBean
    public OpenClawClient openClawClient(ChatClient chatClient,
                                         TaskClient taskClient,
                                         SessionHttpClient sessionHttpClient,
                                         EventClient eventClient,
                                         UploadClient uploadClient,
                                         ArtifactClient artifactClient,
                                         OpenClawWebSocketClient openClawWebSocketClient) {
        return OpenClawClient.builder()
                .chatClient(chatClient)
                .taskClient(taskClient)
                .sessionClient(sessionHttpClient)
                .eventClient(eventClient)
                .uploadClient(uploadClient)
                .artifactClient(artifactClient)
                .webSocketClient(openClawWebSocketClient)
                .build();
    }

    /** 创建用于管理运行时会话生命周期的 {@link SessionManager} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public SessionManager sessionManager(SessionHttpClient sessionHttpClient) {
        return new SessionManager(sessionHttpClient);
    }

    /** 创建用于协调会话状态转换的 {@link SessionLifecycleManager} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public SessionLifecycleManager sessionLifecycleManager() {
        return new SessionLifecycleManager();
    }

    /** 创建用于维护连接活跃状态的 {@link HeartbeatManager} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public HeartbeatManager heartbeatManager() {
        return new HeartbeatManager();
    }

    /** 创建用于向监听器分发运行时事件的 {@link EventPublisher} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public EventPublisher eventPublisher() {
        return new EventPublisher();
    }

    /** 创建用于从注解构建工具元数据的 {@link ToolMetadataBuilder} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public ToolMetadataBuilder toolMetadataBuilder() {
        return new ToolMetadataBuilder();
    }

    /** 创建用于在应用上下文中发现注解工具的 {@link ToolScanner} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public ToolScanner toolScanner(ToolMetadataBuilder toolMetadataBuilder) {
        return new ToolScanner(toolMetadataBuilder);
    }

    /** 创建用于持有已发现工具定义的 {@link ToolRegistry} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry() {
        return new ToolRegistry();
    }

    /** 创建用于调用工具并执行生命周期拦截器的 {@link ToolDispatcher} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public ToolDispatcher toolDispatcher(ToolRegistry toolRegistry,
                                         List<LifecycleInterceptor> interceptors) {
        return new ToolDispatcher(toolRegistry, interceptors);
    }

    /** 创建用于聊天 DTO 转换的 {@link ChatConverter} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public ChatConverter chatConverter() {
        return new ChatConverter();
    }

    /** 创建用于会话 DTO 转换的 {@link SessionConverter} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public SessionConverter sessionConverter() {
        return new SessionConverter();
    }

    /** 创建用于工具 DTO 转换的 {@link ToolConverter} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public ToolConverter toolConverter() {
        return new ToolConverter();
    }

    /** 创建用于事件 DTO 转换的 {@link EventConverter} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public EventConverter eventConverter() {
        return new EventConverter();
    }

    /** 创建 {@link RuntimeConverter} 门面 Bean，委托各领域专用转换器进行转换。 */
    @Bean
    @ConditionalOnMissingBean
    public RuntimeConverter runtimeConverter(ChatConverter chatConverter,
                                             SessionConverter sessionConverter,
                                             ToolConverter toolConverter,
                                             EventConverter eventConverter) {
        return new RuntimeConverter(chatConverter, sessionConverter, toolConverter, eventConverter);
    }

    /** 创建 {@link OpenClawRuntime} Bean，作为运行时操作的主入口。 */
    @Bean
    @ConditionalOnMissingBean
    public OpenClawRuntime openClawRuntime(SessionManager sessionManager,
                                           OpenClawClient openClawClient,
                                           ToolRegistry toolRegistry,
                                           EventPublisher eventPublisher) {
        return new DefaultOpenClawRuntime(sessionManager, openClawClient, toolRegistry, eventPublisher);
    }

    /**
     * 创建 {@link OpenClawShutdownHandler} Bean，用于在应用关闭时优雅地释放运行时资源。
     * {@code ToolRegistrar} 为可选依赖——当 {@code openclaw.auto-register-tool=false} 时该 Bean 不存在，
     * 关闭处理器会跳过工具注销步骤。
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenClawShutdownHandler openClawShutdownHandler(
            SessionManager sessionManager,
            ToolRegistry toolRegistry,
            ObjectProvider<io.openclaw.runtime.tool.registry.ToolRegistrar> toolRegistrarProvider,
            HeartbeatManager heartbeatManager,
            EventPublisher eventPublisher) {
        return new OpenClawShutdownHandler(sessionManager, toolRegistry,
                toolRegistrarProvider.getIfAvailable(), heartbeatManager, eventPublisher);
    }

    /**
     * 在所有单例 Bean 初始化完成后自动建立 WebSocket 连接。
     * 当 {@code openclaw.websocket.auto-connect=true}（默认值）时激活。
     */
    @Bean
    @ConditionalOnMissingBean(name = "openClawWebSocketInitializer")
    public SmartInitializingSingleton openClawWebSocketInitializer(
            OpenClawWebSocketClient webSocketClient,
            OpenClawProperties properties) {
        return () -> {
            if (properties.getWebsocket().isAutoConnect()) {
                webSocketClient.connect()
                        .doOnSuccess(v -> log.info("WebSocket connected to gateway"))
                        .doOnError(e -> log.warn("WebSocket auto-connect failed: {}", e.getMessage()))
                        .subscribe();
            }
        };
    }
}
