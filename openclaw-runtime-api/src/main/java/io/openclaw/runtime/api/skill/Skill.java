package io.openclaw.runtime.api.skill;

import com.fasterxml.jackson.databind.JsonNode;
import io.openclaw.runtime.api.dto.SkillResult;

/**
 * 所有 OpenClaw 技能必须实现的接口。
 * 通过 {@code @OpenClawSkill} 注解自动发现技能。
 */
public interface Skill {

    /**
     * 使用给定参数调用技能。
     *
     * @param arguments 传递给技能的 JSON 参数
     * @return 技能调用的结果
     */
    SkillResult invoke(JsonNode arguments);
}
