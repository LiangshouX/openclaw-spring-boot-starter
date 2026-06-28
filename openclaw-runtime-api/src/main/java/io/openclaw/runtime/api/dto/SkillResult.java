package io.openclaw.runtime.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 表示技能调用的结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillResult {

    /** 产生此结果的技能名称。 */
    private String skillName;
    /** 技能调用是否成功。 */
    private boolean success;
    /** 技能返回的输出数据。 */
    private JsonNode data;
    /** 技能调用失败时的错误信息。 */
    private String errorMessage;
    /** 技能的执行时间（毫秒）。 */
    private long executionTimeMs;

    /**
     * 创建成功的技能执行结果。
     *
     * @param skillName 技能名称
     * @param data      技能产生的输出数据
     * @return 新的成功 {@code SkillResult}
     */
    public static SkillResult success(String skillName, JsonNode data) {
        return SkillResult.builder()
                .skillName(skillName)
                .success(true)
                .data(data)
                .build();
    }

    /**
     * 创建失败的技能执行结果。
     *
     * @param skillName    技能名称
     * @param errorMessage 描述失败的错误信息
     * @return 新的失败 {@code SkillResult}
     */
    public static SkillResult failure(String skillName, String errorMessage) {
        return SkillResult.builder()
                .skillName(skillName)
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
