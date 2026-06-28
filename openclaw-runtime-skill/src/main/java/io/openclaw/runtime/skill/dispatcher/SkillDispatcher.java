package io.openclaw.runtime.skill.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import io.openclaw.runtime.api.dto.SkillResult;
import io.openclaw.runtime.api.exception.ErrorCode;
import io.openclaw.runtime.api.exception.SkillException;
import io.openclaw.runtime.api.interceptor.LifecycleInterceptor;
import io.openclaw.runtime.skill.model.SkillMetadata;
import io.openclaw.runtime.skill.registry.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** 技能调度器，将传入的工具调用分派到对应的技能 Bean，并应用生命周期拦截器。 */
public class SkillDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SkillDispatcher.class);

    private final SkillRegistry skillRegistry;
    private final List<LifecycleInterceptor> interceptors;

    public SkillDispatcher(SkillRegistry skillRegistry, List<LifecycleInterceptor> interceptors) {
        this.skillRegistry = skillRegistry;
        this.interceptors = interceptors;
    }

    /**
     * 将工具调用分派到指定名称的技能，在调用前后应用生命周期拦截器。
     *
     * @param skillName 要调用的技能名称
     * @param arguments 传递给技能的 JSON 参数
     * @return 技能调用的结果
     * @throws io.openclaw.runtime.api.exception.SkillException 如果技能未找到或调用失败
     */
    public SkillResult dispatch(String skillName, JsonNode arguments) {
        SkillMetadata metadata = skillRegistry.get(skillName);
        if (metadata == null) {
            throw new SkillException(ErrorCode.SKILL_NOT_FOUND,
                    "Skill not found: " + skillName);
        }

        long startTime = System.currentTimeMillis();

        try {
            // Execute beforeToolCall interceptors
            for (LifecycleInterceptor interceptor : interceptors) {
                interceptor.beforeToolCall(skillName, arguments);
            }

            // Invoke the skill via reflection
            Object result = metadata.getInvokeMethod().invoke(metadata.getTargetBean(), arguments);
            SkillResult skillResult = (SkillResult) result;

            // Execute afterToolCall interceptors
            for (LifecycleInterceptor interceptor : interceptors) {
                interceptor.afterToolCall(skillName, skillResult);
            }

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Skill '{}' dispatched successfully in {}ms", skillName, executionTime);

            return skillResult;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Skill '{}' invocation failed after {}ms", skillName, executionTime, e);
            throw new SkillException(ErrorCode.SKILL_INVOCATION_FAILED,
                    "Failed to invoke skill: " + skillName, e);
        }
    }

    /**
     * 检查指定名称的技能是否已注册且可被分派。
     *
     * @param skillName 技能名称
     * @return 技能已注册时返回 {@code true}，否则返回 {@code false}
     */
    public boolean canDispatch(String skillName) {
        return skillRegistry.isRegistered(skillName);
    }
}
