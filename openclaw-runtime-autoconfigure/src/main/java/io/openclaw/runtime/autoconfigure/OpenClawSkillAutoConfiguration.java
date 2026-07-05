package io.openclaw.runtime.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openclaw.runtime.api.interceptor.LifecycleInterceptor;
import io.openclaw.runtime.client.websocket.OpenClawWebSocketClient;
import io.openclaw.runtime.skill.archive.SkillArchiveBuilder;
import io.openclaw.runtime.skill.loader.DefaultSkillLoader;
import io.openclaw.runtime.skill.loader.DefaultSkillManager;
import io.openclaw.runtime.skill.loader.SkillLoader;
import io.openclaw.runtime.skill.registry.SkillRegistrationStrategy;
import io.openclaw.runtime.skill.registry.SkillRegistrar;
import io.openclaw.runtime.skill.registry.SkillStatusStrategy;
import io.openclaw.runtime.skill.registry.UploadSkillStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * OpenClaw Skill (SKILL.md) 注册自动配置类。
 * <p>
 * 当 {@code openclaw.auto-register-skill} 为 {@code true} 时激活。
 * <p>
 * 启用此功能需要 Gateway 配置 {@code skills.install.allowUploadedArchives: true}。
 * <p>
 * 创建以下 Bean：
 * <ul>
 *   <li>{@link SkillArchiveBuilder} — 将 SKILL.md 打包为 zip 归档</li>
 *   <li>{@link UploadSkillStrategy} — 通过 skills.upload + skills.install RPC 安装 Skill</li>
 *   <li>{@link SkillStatusStrategy} — 查询 skills.status 和 skills.update 配置</li>
 *   <li>{@link SkillRegistrar} — 组合策略执行注册和注销</li>
 *   <li>{@link SkillLoader} / {@link DefaultSkillManager} — Skill 加载和管理</li>
 * </ul>
 */
@AutoConfiguration(after = OpenClawToolAutoConfiguration.class)
@ConditionalOnProperty(prefix = "openclaw", name = "auto-register-skill", havingValue = "true", matchIfMissing = false)
public class OpenClawSkillAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenClawSkillAutoConfiguration.class);

    /** 创建 {@link SkillArchiveBuilder} Bean，用于将 SKILL.md 打包为 zip 归档。 */
    @Bean
    @ConditionalOnMissingBean
    public SkillArchiveBuilder skillArchiveBuilder() {
        return new SkillArchiveBuilder();
    }

    /** 创建 {@link UploadSkillStrategy} Bean，通过 WebSocket RPC 上传并安装 Skill。 */
    @Bean
    @ConditionalOnMissingBean
    public UploadSkillStrategy uploadSkillStrategy(OpenClawWebSocketClient webSocketClient,
                                                    SkillArchiveBuilder skillArchiveBuilder,
                                                    ObjectMapper objectMapper) {
        return new UploadSkillStrategy(webSocketClient, skillArchiveBuilder, objectMapper);
    }

    /** 创建 {@link SkillStatusStrategy} Bean，查询 Skill 状态和执行配置更新。 */
    @Bean
    @ConditionalOnMissingBean
    public SkillStatusStrategy skillStatusStrategy(OpenClawWebSocketClient webSocketClient,
                                                    ObjectMapper objectMapper) {
        return new SkillStatusStrategy(webSocketClient, objectMapper);
    }

    /** 创建 {@link SkillRegistrar} Bean，组合策略执行 Skill 注册和注销。 */
    @Bean
    @ConditionalOnMissingBean
    public SkillRegistrar skillRegistrar(List<SkillRegistrationStrategy> strategies,
                                          List<LifecycleInterceptor> interceptors) {
        return new SkillRegistrar(strategies, interceptors);
    }

    /** 创建 {@link SkillLoader} Bean，从文件系统扫描 SKILL.md 文件。 */
    @Bean
    @ConditionalOnMissingBean
    public SkillLoader skillLoader() {
        return new DefaultSkillLoader();
    }

    /** 创建 {@link DefaultSkillManager} Bean，管理 Skill 的内存注册和启用/禁用。 */
    @Bean
    @ConditionalOnMissingBean
    public DefaultSkillManager skillManager(SkillLoader skillLoader) {
        return new DefaultSkillManager(skillLoader);
    }

    /**
     * 创建 {@link OpenClawSkillLifecycleInitializer} Bean，
     * 在应用启动时自动加载 SKILL.md 并注册到 Gateway。
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenClawSkillLifecycleInitializer openClawSkillLifecycleInitializer(
            DefaultSkillManager skillManager,
            SkillRegistrar skillRegistrar,
            OpenClawProperties properties) {
        return new OpenClawSkillLifecycleInitializer(skillManager, skillRegistrar, properties);
    }

    /**
     * 创建 {@link OpenClawSkillShutdownHandler} Bean，
     * 在应用关闭时禁用已注册的 Skill。
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenClawSkillShutdownHandler openClawSkillShutdownHandler(
            DefaultSkillManager skillManager,
            SkillRegistrar skillRegistrar) {
        return new OpenClawSkillShutdownHandler(skillManager, skillRegistrar);
    }
}
