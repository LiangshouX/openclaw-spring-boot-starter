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
import io.openclaw.runtime.skill.dispatcher.SkillDispatcher;
import io.openclaw.runtime.skill.registry.SkillRegistry;
import io.openclaw.runtime.skill.scanner.SkillMetadataBuilder;
import io.openclaw.runtime.skill.scanner.SkillScanner;
import io.openclaw.runtime.autoconfigure.runtime.DefaultOpenClawRuntime;

import org.springframework.beans.factory.ObjectProvider;
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

    /** 创建支持 Java Time 模块的 Jackson {@link ObjectMapper} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /** 创建配置了 OpenClaw 端点和认证令牌的 {@link WebClient} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public WebClient openClawWebClient(OpenClawProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getEndpoint())
                .defaultHeader("Authorization", "Bearer " + properties.getToken())
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

    /** 创建用于向 OpenClaw 发送聊天消息的 {@link ChatClient} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public ChatClient chatClient(WebClient openClawWebClient) {
        return new ChatClient(openClawWebClient);
    }

    /** 创建用于管理 OpenClaw 任务的 {@link TaskClient} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public TaskClient taskClient(WebClient openClawWebClient) {
        return new TaskClient(openClawWebClient);
    }

    /** 创建用于 HTTP 会话操作的 {@link SessionHttpClient} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public SessionHttpClient sessionHttpClient(WebClient openClawWebClient) {
        return new SessionHttpClient(openClawWebClient);
    }

    /** 创建用于向 OpenClaw 提交事件的 {@link EventClient} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public EventClient eventClient(WebClient openClawWebClient) {
        return new EventClient(openClawWebClient);
    }

    /** 创建用于向 OpenClaw 上传文件的 {@link UploadClient} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public UploadClient uploadClient(WebClient openClawWebClient) {
        return new UploadClient(openClawWebClient);
    }

    /** 创建用于管理 OpenClaw 制品的 {@link ArtifactClient} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public ArtifactClient artifactClient(WebClient openClawWebClient) {
        return new ArtifactClient(openClawWebClient);
    }

    /** 创建用于 WebSocket 连接 OpenClaw 的 {@link OpenClawWebSocketClient} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public OpenClawWebSocketClient openClawWebSocketClient() {
        return new OpenClawWebSocketClient();
    }

    /** 创建 {@link OpenClawClient} 门面 Bean，聚合所有领域专用的 HTTP 客户端。 */
    @Bean
    @ConditionalOnMissingBean
    public OpenClawClient openClawClient(ChatClient chatClient,
                                         TaskClient taskClient,
                                         SessionHttpClient sessionHttpClient,
                                         EventClient eventClient,
                                         UploadClient uploadClient,
                                         ArtifactClient artifactClient) {
        return OpenClawClient.builder()
                .chatClient(chatClient)
                .taskClient(taskClient)
                .sessionClient(sessionHttpClient)
                .eventClient(eventClient)
                .uploadClient(uploadClient)
                .artifactClient(artifactClient)
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

    /** 创建用于从注解构建技能元数据的 {@link SkillMetadataBuilder} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public SkillMetadataBuilder skillMetadataBuilder() {
        return new SkillMetadataBuilder();
    }

    /** 创建用于在应用上下文中发现注解技能的 {@link SkillScanner} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public SkillScanner skillScanner(SkillMetadataBuilder skillMetadataBuilder) {
        return new SkillScanner(skillMetadataBuilder);
    }

    /** 创建用于持有已发现技能定义的 {@link SkillRegistry} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public SkillRegistry skillRegistry() {
        return new SkillRegistry();
    }

    /** 创建用于调用技能并执行生命周期拦截器的 {@link SkillDispatcher} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public SkillDispatcher skillDispatcher(SkillRegistry skillRegistry,
                                           List<LifecycleInterceptor> interceptors) {
        return new SkillDispatcher(skillRegistry, interceptors);
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

    /** 创建用于技能 DTO 转换的 {@link SkillConverter} Bean。 */
    @Bean
    @ConditionalOnMissingBean
    public SkillConverter skillConverter() {
        return new SkillConverter();
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
                                             SkillConverter skillConverter,
                                             EventConverter eventConverter) {
        return new RuntimeConverter(chatConverter, sessionConverter, skillConverter, eventConverter);
    }

    /** 创建 {@link OpenClawRuntime} Bean，作为运行时操作的主入口。 */
    @Bean
    @ConditionalOnMissingBean
    public OpenClawRuntime openClawRuntime(SessionManager sessionManager,
                                           OpenClawClient openClawClient,
                                           SkillRegistry skillRegistry,
                                           EventPublisher eventPublisher) {
        return new DefaultOpenClawRuntime(sessionManager, openClawClient, skillRegistry, eventPublisher);
    }

    /**
     * 创建 {@link OpenClawShutdownHandler} Bean，用于在应用关闭时优雅地释放运行时资源。
     * {@code SkillRegistrar} 为可选依赖——当 {@code openclaw.auto-register-skill=false} 时该 Bean 不存在，
     * 关闭处理器会跳过技能注销步骤。
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenClawShutdownHandler openClawShutdownHandler(
            SessionManager sessionManager,
            SkillRegistry skillRegistry,
            ObjectProvider<io.openclaw.runtime.skill.registry.SkillRegistrar> skillRegistrarProvider,
            HeartbeatManager heartbeatManager,
            EventPublisher eventPublisher) {
        return new OpenClawShutdownHandler(sessionManager, skillRegistry,
                skillRegistrarProvider.getIfAvailable(), heartbeatManager, eventPublisher);
    }
}
