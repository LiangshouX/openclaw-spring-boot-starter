package io.openclaw.runtime.autoconfigure;

import io.openclaw.runtime.skill.loader.DefaultSkillManager;
import io.openclaw.runtime.skill.model.SkillDefinition;
import io.openclaw.runtime.skill.registry.SkillRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理 Skill 的优雅关闭。
 * <p>
 * 在应用关闭时，通过 {@link SkillRegistrar} 禁用已注册的 Skill
 * （调用 {@code skills.update} config 模式设置 {@code enabled=false}）。
 * <p>
 * 注意：这不会从 Gateway 文件系统删除 SKILL.md 文件，
 * 仅修改 {@code skills.entries} 配置中的启用状态。
 */
public class OpenClawSkillShutdownHandler implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(OpenClawSkillShutdownHandler.class);

    private final DefaultSkillManager skillManager;
    private final SkillRegistrar skillRegistrar;

    public OpenClawSkillShutdownHandler(DefaultSkillManager skillManager,
                                         SkillRegistrar skillRegistrar) {
        this.skillManager = skillManager;
        this.skillRegistrar = skillRegistrar;
    }

    @Override
    public void destroy() {
        log.info("Unregistering OpenClaw skills...");

        List<String> skillNames = skillManager.getEligibleSkills().stream()
                .map(SkillDefinition::getName)
                .collect(Collectors.toList());

        if (!skillNames.isEmpty()) {
            try {
                skillRegistrar.unregisterFromOpenClaw(skillNames);
                log.info("Unregistered {} skills", skillNames.size());
            } catch (Exception e) {
                log.warn("Failed to unregister skills during shutdown: {}", e.getMessage());
            }
        } else {
            log.debug("No eligible skills to unregister");
        }
    }
}
