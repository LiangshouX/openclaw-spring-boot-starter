package io.openclaw.runtime.skill.registry;

import io.openclaw.runtime.skill.model.SkillDefinition;

import java.util.List;

/**
 * Skill 注册策略接口。
 * <p>
 * 定义 Skill 注册到 OpenClaw Gateway 的可插拔策略。
 * 不同策略可通过不同通道（如 WebSocket RPC upload、状态查询、配置更新）
 * 完成 Skill 的注册和管理。
 * <p>
 * 实现类可被组合使用，由 {@link SkillRegistrar} 统一调度。
 *
 * @see UploadSkillStrategy
 * @see SkillStatusStrategy
 */
public interface SkillRegistrationStrategy {

    /**
     * 将指定的 Skill 列表注册到 OpenClaw Gateway。
     *
     * @param skills 要注册的 Skill 定义列表
     */
    void register(List<SkillDefinition> skills);

    /**
     * 从 OpenClaw Gateway 注销指定的 Skill。
     *
     * @param skillNames 要注销的 Skill 名称列表
     */
    void unregister(List<String> skillNames);

    /**
     * 获取策略名称，用于日志和调试。
     *
     * @return 策略名称
     */
    String getName();
}
