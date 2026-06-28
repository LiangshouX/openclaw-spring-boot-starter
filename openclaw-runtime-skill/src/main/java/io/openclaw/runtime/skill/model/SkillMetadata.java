package io.openclaw.runtime.skill.model;

import io.openclaw.runtime.api.dto.SkillDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Method;

/** 技能元数据 DTO，将已发现技能的定义关联到 Spring Bean 和调用方法。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillMetadata {

    private SkillDefinition definition;
    private Class<?> targetClass;
    private Object targetBean;
    private Method invokeMethod;
    private boolean registered;
}
