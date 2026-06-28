package io.openclaw.runtime.skill.registry;

import io.openclaw.runtime.api.dto.SkillDefinition;
import io.openclaw.runtime.skill.model.SkillMetadata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** 技能注册表，内存中管理所有已发现和已注册的 OpenClaw 技能。 */
public class SkillRegistry {

    private final ConcurrentHashMap<String, SkillMetadata> skills = new ConcurrentHashMap<>();

    /**
     * 在注册表中注册单个技能。
     *
     * @param metadata 要注册的技能元数据
     */
    public void register(SkillMetadata metadata) {
        String skillName = metadata.getDefinition().getName();
        skills.put(skillName, metadata);
        metadata.setRegistered(true);
    }

    /**
     * 批量注册所有提供的技能元数据。
     *
     * @param metadataList 要注册的技能元数据列表
     */
    public void registerAll(List<SkillMetadata> metadataList) {
        for (SkillMetadata metadata : metadataList) {
            register(metadata);
        }
    }

    /**
     * 根据名称从注册表中移除技能。
     *
     * @param skillName 要注销的技能名称
     */
    public void unregister(String skillName) {
        SkillMetadata removed = skills.remove(skillName);
        if (removed != null) {
            removed.setRegistered(false);
        }
    }

    /**
     * 根据名称获取已注册技能的元数据。
     *
     * @param skillName 技能名称
     * @return 技能元数据，如果未注册则返回 {@code null}
     */
    public SkillMetadata get(String skillName) {
        return skills.get(skillName);
    }

    /**
     * 返回所有已注册的技能元数据。
     *
     * @return 所有已注册技能元数据的列表
     */
    public List<SkillMetadata> getAll() {
        return new ArrayList<>(skills.values());
    }

    /**
     * 检查指定名称的技能是否已注册。
     *
     * @param skillName 技能名称
     * @return 技能已注册时返回 {@code true}，否则返回 {@code false}
     */
    public boolean isRegistered(String skillName) {
        return skills.containsKey(skillName);
    }

    /**
     * 构建包含所有已注册技能定义的清单。
     *
     * @return 包含所有已注册技能定义的 {@link SkillManifest}
     */
    public SkillManifest buildManifest() {
        List<SkillDefinition> definitions = skills.values().stream()
                .map(SkillMetadata::getDefinition)
                .collect(Collectors.toList());

        return SkillManifest.builder()
                .version("1.0")
                .generatedAt(Instant.now())
                .skills(definitions)
                .build();
    }
}
