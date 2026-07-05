package io.openclaw.runtime.skill.registry;

import io.openclaw.runtime.api.exception.ErrorCode;
import io.openclaw.runtime.api.exception.RegisterException;
import io.openclaw.runtime.api.interceptor.LifecycleInterceptor;
import io.openclaw.runtime.skill.model.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Skill 注册器，处理与 OpenClaw Gateway 之间的 Skill 注册和注销操作。
 * <p>
 * 使用可插拔的 {@link SkillRegistrationStrategy} 策略列表执行实际注册，
 * 并在注册前后调用 {@link LifecycleInterceptor} 生命周期拦截器。
 * <p>
 * 注册流程：
 * <ol>
 *   <li>对每个 Skill 调用 {@code beforeRegisterSkill} 拦截器</li>
 *   <li>依次执行所有策略的 {@code register} 方法</li>
 *   <li>对每个 Skill 调用 {@code afterRegisterSkill} 拦截器</li>
 * </ol>
 * <p>
 * 任一策略失败不会阻止其他策略的执行。所有策略均失败时抛出 {@link RegisterException}。
 *
 * @see SkillRegistrationStrategy
 * @see UploadSkillStrategy
 * @see SkillStatusStrategy
 */
public class SkillRegistrar {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistrar.class);

    private final List<SkillRegistrationStrategy> strategies;
    private final List<LifecycleInterceptor> interceptors;

    public SkillRegistrar(List<SkillRegistrationStrategy> strategies,
                          List<LifecycleInterceptor> interceptors) {
        this.strategies = strategies;
        this.interceptors = interceptors;
    }

    /**
     * 将指定的 Skill 列表注册到 OpenClaw Gateway。
     * <p>
     * 依次执行所有注册策略，任一策略失败不会阻止其他策略的执行。
     *
     * @param skills 要注册的 Skill 定义列表
     * @throws RegisterException 当所有策略均执行失败时
     */
    public void registerToOpenClaw(List<SkillDefinition> skills) {
        if (skills == null || skills.isEmpty()) {
            log.info("No skills to register");
            return;
        }

        int skillCount = skills.size();
        log.info("Registering {} skills to OpenClaw via {} strategies", skillCount, strategies.size());

        // Invoke beforeRegisterSkill interceptors
        for (SkillDefinition skill : skills) {
            for (LifecycleInterceptor interceptor : interceptors) {
                try {
                    interceptor.beforeRegisterSkill(skill.getName());
                } catch (Exception e) {
                    log.warn("beforeRegisterSkill interceptor failed for '{}': {}",
                            skill.getName(), e.getMessage());
                }
            }
        }

        // Execute each registration strategy
        int successCount = 0;
        for (SkillRegistrationStrategy strategy : strategies) {
            try {
                strategy.register(skills);
                successCount++;
                log.info("Strategy '{}' registered {} skills successfully",
                        strategy.getName(), skillCount);
            } catch (Exception e) {
                log.error("Strategy '{}' failed to register skills: {}",
                        strategy.getName(), e.getMessage(), e);
            }
        }

        if (successCount == 0 && !strategies.isEmpty()) {
            throw new RegisterException(ErrorCode.REGISTER_FAILED,
                    "All " + strategies.size() + " registration strategies failed for " +
                            skillCount + " skills");
        }

        // Invoke afterRegisterSkill interceptors
        for (SkillDefinition skill : skills) {
            for (LifecycleInterceptor interceptor : interceptors) {
                try {
                    interceptor.afterRegisterSkill(skill.getName());
                } catch (Exception e) {
                    log.warn("afterRegisterSkill interceptor failed for '{}': {}",
                            skill.getName(), e.getMessage());
                }
            }
        }

        log.info("Skill registration complete: {} skills registered via {}/{} strategies",
                skillCount, successCount, strategies.size());
    }

    /**
     * 从 OpenClaw Gateway 注销指定的 Skill。
     * <p>
     * 由于 OpenClaw Gateway 没有 {@code skills.uninstall} RPC，
     * 注销操作通过 {@link SkillStatusStrategy} 的 config 模式禁用 Skill。
     *
     * @param skillNames 要注销的 Skill 名称列表
     * @throws RegisterException 当所有策略均执行失败时
     */
    public void unregisterFromOpenClaw(List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            log.info("No skills to unregister");
            return;
        }

        log.info("Unregistering {} skills from OpenClaw via {} strategies",
                skillNames.size(), strategies.size());

        int successCount = 0;
        for (SkillRegistrationStrategy strategy : strategies) {
            try {
                strategy.unregister(skillNames);
                successCount++;
                log.info("Strategy '{}' unregistered skills successfully", strategy.getName());
            } catch (Exception e) {
                log.error("Strategy '{}' failed to unregister skills: {}",
                        strategy.getName(), e.getMessage(), e);
            }
        }

        if (successCount == 0 && !strategies.isEmpty()) {
            throw new RegisterException(ErrorCode.UNREGISTER_FAILED,
                    "All " + strategies.size() + " unregistration strategies failed for " +
                            skillNames.size() + " skills");
        }

        log.info("Skill unregistration complete: via {}/{} strategies",
                successCount, strategies.size());
    }
}
