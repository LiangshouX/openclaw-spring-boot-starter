package io.openclaw.runtime.skill.loader;

import io.openclaw.runtime.skill.model.SkillDefinition;

import java.util.List;

/**
 * Skill 管理器接口，负责 Skill 的生命周期管理。
 * <p>
 * 管理 Skill 的加载、启用/禁用、以及通过 Gateway WebSocket RPC
 * （如 {@code skills.install}、{@code skills.update}）进行的远程操作。
 * <p>
 * 本接口为未来扩展预留，当前版本不包含完整实现。
 */
public interface SkillManager {

    /**
     * 获取所有已加载的 Skill 定义。
     *
     * @return Skill 定义列表
     */
    List<SkillDefinition> getAllSkills();

    /**
     * 根据名称获取 Skill 定义。
     *
     * @param name Skill 名称
     * @return Skill 定义，未找到时返回 null
     */
    SkillDefinition getSkill(String name);

    /**
     * 启用指定的 Skill。
     *
     * @param name Skill 名称
     */
    void enableSkill(String name);

    /**
     * 禁用指定的 Skill。
     *
     * @param name Skill 名称
     */
    void disableSkill(String name);
}
