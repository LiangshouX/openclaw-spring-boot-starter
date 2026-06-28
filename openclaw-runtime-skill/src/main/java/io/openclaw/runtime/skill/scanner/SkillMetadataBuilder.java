package io.openclaw.runtime.skill.scanner;

import com.fasterxml.jackson.databind.JsonNode;
import io.openclaw.runtime.api.dto.SkillDefinition;
import io.openclaw.runtime.api.skill.Skill;
import io.openclaw.runtime.skill.annotation.OpenClawSkill;
import io.openclaw.runtime.skill.model.SkillMetadata;

import java.lang.reflect.Method;

/** 技能元数据构建器，根据 Spring Bean 及其 {@code @OpenClawSkill} 注解构建 {@link SkillMetadata}。 */
public class SkillMetadataBuilder {

    /**
     * 根据给定的 Bean 及其注解构建技能元数据。
     *
     * @param bean 标注为技能的 Spring Bean 实例
     * @param annotation Bean 上的 {@code @OpenClawSkill} 注解
     * @return 构建完成的技能元数据
     */
    public SkillMetadata build(Object bean, OpenClawSkill annotation) {
        SkillDefinition definition = SkillDefinition.builder()
                .name(annotation.name())
                .description(annotation.description())
                .version(annotation.version())
                .className(bean.getClass().getName())
                .build();

        Method invokeMethod = findInvokeMethod(bean.getClass());

        return SkillMetadata.builder()
                .definition(definition)
                .targetClass(bean.getClass())
                .targetBean(bean)
                .invokeMethod(invokeMethod)
                .registered(false)
                .build();
    }

    private Method findInvokeMethod(Class<?> clazz) {
        // Look for the invoke(JsonNode) method from the Skill interface
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals("invoke")
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == JsonNode.class) {
                return method;
            }
        }

        // Fallback: if the class implements Skill interface, find it through the interface
        if (Skill.class.isAssignableFrom(clazz)) {
            try {
                return clazz.getMethod("invoke", JsonNode.class);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                        "Cannot find invoke(JsonNode) method on class: " + clazz.getName(), e);
            }
        }

        throw new IllegalArgumentException(
                "Cannot find invoke(JsonNode) method on class: " + clazz.getName());
    }
}
