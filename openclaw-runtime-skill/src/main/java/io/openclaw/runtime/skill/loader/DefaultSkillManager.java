package io.openclaw.runtime.skill.loader;

import io.openclaw.runtime.skill.model.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 默认 Skill 管理器实现，提供 Skill 的内存注册、查询和启用/禁用管理。
 * <p>
 * 管理器维护两个集合：
 * <ul>
 *   <li><b>已加载 Skill</b>：从文件系统扫描到的所有 Skill 定义</li>
 *   <li><b>禁用列表</b>：被显式禁用的 Skill 名称集合</li>
 * </ul>
 * <p>
 * 有效的 Skill（eligible）= 已加载 且 未禁用。
 */
public class DefaultSkillManager implements SkillManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultSkillManager.class);

    private final ConcurrentHashMap<String, SkillDefinition> skills = new ConcurrentHashMap<>();
    private final Set<String> disabledSkills = ConcurrentHashMap.newKeySet();
    private final SkillLoader skillLoader;

    public DefaultSkillManager(SkillLoader skillLoader) {
        this.skillLoader = skillLoader;
    }

    /**
     * 从指定目录加载所有 Skill 并注册到管理器。
     * 可多次调用以从不同目录加载 Skill。
     *
     * @param directory 要扫描的目录路径
     * @return 本次加载的 Skill 数量
     */
    public int loadFromDirectory(Path directory) {
        List<SkillDefinition> loaded = skillLoader.loadFromDirectory(directory);
        for (SkillDefinition skill : loaded) {
            skills.put(skill.getName(), skill);
        }
        return loaded.size();
    }

    /**
     * 从多个目录批量加载 Skill。
     *
     * @param directories 目录路径列表
     * @return 总共加载的 Skill 数量
     */
    public int loadFromDirectories(List<Path> directories) {
        int total = 0;
        for (Path dir : directories) {
            total += loadFromDirectory(dir);
        }
        return total;
    }

    /** {@inheritDoc} */
    @Override
    public List<SkillDefinition> getAllSkills() {
        return new ArrayList<>(skills.values());
    }

    /**
     * 获取所有有效（eligible）的 Skill — 已加载且未被禁用。
     *
     * @return 有效 Skill 定义列表
     */
    public List<SkillDefinition> getEligibleSkills() {
        return skills.values().stream()
                .filter(s -> !disabledSkills.contains(s.getName()))
                .collect(Collectors.toList());
    }

    /** {@inheritDoc} */
    @Override
    public SkillDefinition getSkill(String name) {
        return skills.get(name);
    }

    /** {@inheritDoc} */
    @Override
    public void enableSkill(String name) {
        if (disabledSkills.remove(name)) {
            log.info("Skill '{}' enabled", name);
        } else {
            log.debug("Skill '{}' was already enabled", name);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void disableSkill(String name) {
        if (skills.containsKey(name)) {
            disabledSkills.add(name);
            log.info("Skill '{}' disabled", name);
        } else {
            log.warn("Cannot disable unknown skill: '{}'", name);
        }
    }

    /**
     * 检查指定 Skill 是否有效（已加载且未禁用）。
     *
     * @param name Skill 名称
     * @return 有效时返回 true
     */
    public boolean isEligible(String name) {
        return skills.containsKey(name) && !disabledSkills.contains(name);
    }

    /**
     * 获取所有已加载 Skill 的数量。
     */
    public int size() {
        return skills.size();
    }

    /**
     * 获取所有被禁用 Skill 的名称集合。
     */
    public Set<String> getDisabledSkillNames() {
        return Collections.unmodifiableSet(disabledSkills);
    }

    /**
     * 清空所有已加载的 Skill 和禁用列表。
     */
    public void clear() {
        skills.clear();
        disabledSkills.clear();
    }
}
